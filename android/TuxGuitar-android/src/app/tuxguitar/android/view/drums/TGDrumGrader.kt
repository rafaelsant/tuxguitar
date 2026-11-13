package app.tuxguitar.android.view.drums

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tuxguitar.android.activity.TGActivityController
import app.tuxguitar.android.util.MidiTickUtil
import app.tuxguitar.android.view.tablature.TGSongViewController
import app.tuxguitar.document.TGDocumentManager
import app.tuxguitar.graphics.control.TGNoteHighlighter
import app.tuxguitar.player.base.MidiPlayer
import app.tuxguitar.song.models.TGDuration
import app.tuxguitar.song.models.TGNote
import app.tuxguitar.song.models.TGTrack
import app.tuxguitar.ui.resource.UIColorModel
import app.tuxguitar.util.TGContext
import app.tuxguitar.util.singleton.TGSingletonFactory
import app.tuxguitar.util.singleton.TGSingletonUtil
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** How a note of the score was played. */
enum class TGDrumGrade { ON_TIME, EARLY, LATE, MISSED }

/**
 * Score of a pass over the practiced section. [extra] are hits that match no note of the score (a wrong piece, or no
 * note at that time).
 */
data class TGDrumPass(
	val onTime: Int = 0,
	val early: Int = 0,
	val late: Int = 0,
	val missed: Int = 0,
	val extra: Int = 0,
) {
	val judged: Int
		get() = onTime + early + late + missed

	/** On time counts as one, early or late as half; extra hits count against. Null until something was played. */
	val percent: Int?
		get() {
			val total = judged + extra
			return if (total == 0) null else ((onTime + (early + late) / 2f) * 100f / total).roundToInt()
		}
}

/**
 * Grades playing on a MIDI drum kit against the drum track while the song plays (practice mode): each hit is matched
 * with the nearest note of the same piece that is not played yet, notes left behind are missed. The notes are colored
 * in the score (green on time, yellow early or late, red missed) and every pass over the section gets a score.
 *
 * Times: a hit carries the time it reached the phone; the player position at that moment is the sequencer position
 * now, moved back by the time since then. The song is heard [latencyMillis] after the sequencer plays it (audio
 * output), so the hit is compared with the note the player was on [latencyMillis] before. That latency is measured by
 * [calibrate]: the typical distance of the hits to their notes over the last pass.
 */
class TGDrumGrader(private val context: TGContext) : TGNoteHighlighter {

	companion object {
		/** Up to this far from the note, a hit is on time. */
		const val ON_TIME_MILLIS = 45
		/** Up to this far, early or late; further, the hit is for no note. */
		const val WINDOW_MILLIS = 130
		/** Hits this far from their note are still used to measure the latency. */
		private const val CALIBRATION_WINDOW_MILLIS = 350
		const val MIN_CALIBRATION_HITS = 8
		const val MAX_LATENCY_MILLIS = 500
		private const val PREFS = "drums"

		/** Colors of on time, early or late, and missed, readable on the score background of each theme. */
		@JvmStatic
		fun gradeColors(dark: Boolean): Triple<Int, Int, Int> =
			if (dark) Triple(0x3FB950, 0xFFB300, 0xEF5350) else Triple(0x1A7F37, 0xB86E00, 0xC62828)

		private fun model(rgb: Int) = UIColorModel((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)

		@JvmStatic
		fun getInstance(context: TGContext): TGDrumGrader =
			TGSingletonUtil.getInstance(context, TGDrumGrader::class.java.name, object : TGSingletonFactory<TGDrumGrader> {
				override fun createInstance(context: TGContext): TGDrumGrader = TGDrumGrader(context)
			})
	}

	/** A note of the drum track to be played: [group] is the piece (or the note itself for other percussion). */
	private class Expected(val tick: Long, val group: Int, val note: TGNote)

	private val player: MidiPlayer
		get() = MidiPlayer.getInstance(context)

	// ---- state shown by the practice bar

	var enabled by mutableStateOf(false)
		private set
	var status by mutableStateOf<TGMidiStatus>(TGMidiStatus.NoDevice)
		private set
	/** Name of the graded track, null when the song has no drum track. */
	var trackName by mutableStateOf<String?>(null)
		private set
	var latencyMillis by mutableStateOf(0)
		private set
	var current by mutableStateOf(TGDrumPass())
		private set
	var last by mutableStateOf<TGDrumPass?>(null)
		private set
	/** Number of hits measured for the latency over the last pass (enough from [MIN_CALIBRATION_HITS]). */
	var calibrationHits by mutableStateOf(0)
		private set

	private var input: TGMidiInput? = null
	private var track: TGTrack? = null
	private var expected: List<Expected> = emptyList()
	private val grades = IdentityHashMap<TGNote, TGDrumGrade>()
	private var errors = mutableListOf<Int>()
	private var lastErrors = listOf<Int>()
	private var running = false
	private var passStartTick = -1L
	private var lastTick = -1L
	private var loaded = false

	private fun load() {
		if (loaded) {
			return
		}
		loaded = true
		val prefs = prefs() ?: return
		enabled = prefs.getBoolean("enabled", false)
		latencyMillis = prefs.getInt("latencyMillis", 0)
	}

	private fun prefs() = TGActivityController.getInstance(context).activity?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private fun save() {
		prefs()?.edit()?.putBoolean("enabled", enabled)?.putInt("latencyMillis", latencyMillis)?.apply()
	}

	/** Restores the saved settings; call before reading [enabled]. */
	fun restore() = load()

	fun setEnabled(androidContext: Context, value: Boolean) {
		load()
		enabled = value
		save()
		if (value) start(androidContext) else stop()
	}

	fun setLatency(millis: Int) {
		latencyMillis = millis.coerceIn(0, MAX_LATENCY_MILLIS)
		save()
	}

	/** Moves the latency by the typical distance of the hits of the last pass to their notes. */
	fun calibrate(): Boolean {
		val samples = (if (lastErrors.size >= MIN_CALIBRATION_HITS) lastErrors else errors).sorted()
		if (samples.size < MIN_CALIBRATION_HITS) {
			return false
		}
		setLatency(latencyMillis + samples[samples.size / 2])
		lastErrors = emptyList()
		errors.clear()
		calibrationHits = 0
		return true
	}

	// ---- connection: while practicing with grading on and the app shown

	fun start(androidContext: Context) {
		load()
		if (!enabled || input != null) {
			return
		}
		val input = TGMidiInput(androidContext.applicationContext, { status = it }, ::onHit)
		this.input = input
		input.start()
		// shows which track is graded before playing
		loadExpected()
		TGSongViewController.getInstance(context).layout.noteHighlighter = this
	}

	fun stop() {
		input?.stop()
		input = null
		status = TGMidiStatus.NoDevice
		val controller = TGSongViewController.getInstance(context)
		if (controller.layout.noteHighlighter === this) {
			controller.layout.noteHighlighter = null
		}
		clearMarks()
		running = false
		controller.redraw()
	}

	// ---- the notes to play

	/** The drum track: the selected one if it is a drum track, else the first drum track of the song. */
	private fun findTrack(): TGTrack? {
		val documentManager = TGDocumentManager.getInstance(context)
		val song = documentManager.song ?: return null
		val manager = documentManager.songManager
		val selected = runCatching { TGSongViewController.getInstance(context).caret.track }.getOrNull()
		if (selected != null && manager.isPercussionChannel(song, selected.channelId)) {
			return selected
		}
		return (0 until song.countTracks()).map { song.getTrack(it) }.firstOrNull { manager.isPercussionChannel(song, it.channelId) }
	}

	private fun loadExpected() {
		val track = findTrack()
		this.track = track
		trackName = track?.name
		if (track == null) {
			expected = emptyList()
			return
		}
		val list = ArrayList<Expected>()
		for (m in 0 until track.countMeasures()) {
			for (beat in track.getMeasure(m).beats) {
				for (v in 0 until beat.countVoices()) {
					val voice = beat.getVoice(v)
					if (voice.isEmpty || voice.isRestVoice) {
						continue
					}
					for (note in voice.notes) {
						if (note.isTiedNote) {
							continue
						}
						val value = (track.getString(note.string)?.value ?: 0) + note.value
						val group = TGDrumPiece.of(value)?.ordinal ?: (1000 + value)
						list.add(Expected(beat.start, group, note))
					}
				}
			}
		}
		list.sortBy { it.tick }
		expected = list
	}

	// ---- grading

	private fun clearMarks() {
		grades.clear()
		current = TGDrumPass()
	}

	/** Song position (in song ticks) and the ticks per millisecond there, or null while not playing. */
	private fun position(): Pair<Long, Double>? {
		val player = player
		if (!player.isRunning) {
			return null
		}
		val tick = MidiTickUtil.getStart(context, player.tickPosition)
		val song = TGDocumentManager.getInstance(context).song ?: return null
		val header = TGDocumentManager.getInstance(context).songManager.getMeasureHeaderAt(song, tick) ?: return null
		val bpm = header.tempo.quarterValue * player.mode.currentPercent / 100.0
		return tick to (TGDuration.QUARTER_TIME * bpm / 60000.0)
	}

	/**
	 * Follows the playback (called often while practicing): a new pass starts when the position jumps back (next loop
	 * pass, restart) or playback starts; notes left behind are missed.
	 */
	fun tick() {
		if (input == null) {
			return
		}
		val position = position()
		if (position == null) {
			if (running) {
				running = false
				finishPass()
			}
			return
		}
		val (tick, ticksPerMs) = position
		if (!running || tick < lastTick - TGDuration.QUARTER_TIME / 2) {
			if (running) finishPass() else loadExpected()
			running = true
			newPass(tick)
		}
		lastTick = tick
		// heard now: what the player was on latencyMillis ago
		val heard = tick - (latencyMillis * ticksPerMs).toLong()
		val windowTicks = (WINDOW_MILLIS * ticksPerMs).toLong()
		var changed = false
		var missed = 0
		for (e in expected) {
			if (e.tick >= heard - windowTicks) {
				break
			}
			if (e.tick >= passStartTick && !grades.containsKey(e.note)) {
				grades[e.note] = TGDrumGrade.MISSED
				missed++
				changed = true
			}
		}
		if (changed) {
			current = current.copy(missed = current.missed + missed)
		}
	}

	private fun newPass(tick: Long) {
		clearMarks()
		passStartTick = tick
		lastTick = tick
		errors = mutableListOf()
	}

	private fun finishPass() {
		if (current.judged + current.extra > 0) {
			last = current
		}
		lastErrors = errors.toList()
		calibrationHits = lastErrors.size
		TGSongViewController.getInstance(context).redraw()
	}

	private fun onHit(hit: TGDrumHit) {
		val player = player
		// the count-in clicks are not part of the song
		if (!running || player.countDown.isRunning) {
			return
		}
		val (tick, ticksPerMs) = position() ?: return
		val ageMillis = (System.nanoTime() - hit.timeNanos) / 1_000_000.0
		val hitTick = tick - ((ageMillis + latencyMillis) * ticksPerMs).toLong()
		val group = TGDrumPiece.of(hit.note)?.ordinal ?: (1000 + hit.note)

		// the nearest note of that piece, among those not played yet
		val calibrationTicks = (CALIBRATION_WINDOW_MILLIS * ticksPerMs).toLong()
		var best: Expected? = null
		var bestDistance = Long.MAX_VALUE
		for (e in candidates(hitTick - calibrationTicks, hitTick + calibrationTicks)) {
			if (e.group == group && e.tick >= passStartTick && !grades.containsKey(e.note)) {
				val distance = abs(e.tick - hitTick)
				if (distance < bestDistance) {
					best = e
					bestDistance = distance
				}
			}
		}
		val errorMillis = best?.let { ((hitTick - it.tick) / ticksPerMs).roundToInt() }
		if (errorMillis != null) {
			errors.add(errorMillis)
			calibrationHits = maxOf(calibrationHits, errors.size)
		}
		if (best == null || errorMillis == null || abs(errorMillis) > WINDOW_MILLIS) {
			current = current.copy(extra = current.extra + 1)
			return
		}
		val grade = when {
			abs(errorMillis) <= ON_TIME_MILLIS -> TGDrumGrade.ON_TIME
			errorMillis < 0 -> TGDrumGrade.EARLY
			else -> TGDrumGrade.LATE
		}
		grades[best.note] = grade
		current = when (grade) {
			TGDrumGrade.ON_TIME -> current.copy(onTime = current.onTime + 1)
			TGDrumGrade.EARLY -> current.copy(early = current.early + 1)
			else -> current.copy(late = current.late + 1)
		}
	}

	/** Expected notes between two ticks (the list is sorted). */
	private fun candidates(from: Long, to: Long): List<Expected> {
		var low = 0
		var high = expected.size
		while (low < high) {
			val mid = (low + high) / 2
			if (expected[mid].tick < from) low = mid + 1 else high = mid
		}
		val result = ArrayList<Expected>()
		var i = low
		while (i < expected.size && expected[i].tick <= to) {
			result.add(expected[i])
			i++
		}
		return result
	}

	// the score follows the system theme when the app starts
	private val colors = gradeColors(TGSongViewController.isDarkMode()).let { (green, amber, red) ->
		Triple(model(green), model(amber), model(red))
	}

	override fun getHighlightColor(note: TGNote): UIColorModel? = when (grades[note]) {
		TGDrumGrade.ON_TIME -> colors.first
		TGDrumGrade.EARLY, TGDrumGrade.LATE -> colors.second
		TGDrumGrade.MISSED -> colors.third
		null -> null
	}
}
