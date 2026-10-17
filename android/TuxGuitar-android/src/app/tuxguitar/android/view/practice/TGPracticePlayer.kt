package app.tuxguitar.android.view.practice

import android.content.Context
import android.os.Handler
import android.os.Looper
import app.tuxguitar.android.action.impl.caret.TGMoveToAction
import app.tuxguitar.android.action.impl.transport.TGTransportPlayAction
import app.tuxguitar.android.action.impl.transport.TGTransportStopAction
import app.tuxguitar.android.activity.TGActivityController
import app.tuxguitar.android.transport.TGTransport
import app.tuxguitar.android.util.MidiTickUtil
import app.tuxguitar.android.view.tablature.TGSongViewController
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.transport.TGTransportMetronomeAction
import app.tuxguitar.event.TGEvent
import app.tuxguitar.event.TGEventListener
import app.tuxguitar.player.base.MidiPlayer
import app.tuxguitar.player.base.MidiPlayerEvent
import app.tuxguitar.player.base.MidiPlayerMode
import app.tuxguitar.util.TGContext
import app.tuxguitar.util.error.TGErrorManager
import kotlin.math.roundToInt

/** Player state read by the bars; refreshed by polling while they are shown. */
data class TGPlayerSnapshot(
	val running: Boolean,
	val currentPercent: Int,
	val metronome: Boolean,
	val measureCount: Int,
	val caretMeasure: Int,
	/** Quarter note tempo where playback starts: loop start if looping, else the caret measure. */
	val baseTempo: Int,
)

/**
 * Practice settings, kept as tempo percentages like [MidiPlayerMode] and saved between sessions.
 * Loop measures <= 0 mean "from the beginning" / "to the end".
 */
data class TGPracticeSettings(
	val training: Boolean = false,
	val loop: Boolean = true,
	val fixedPercent: Int = 100,
	val fromPercent: Int = 80,
	val toPercent: Int = 100,
	val incrementPercent: Int = 5,
	val loopStart: Int = -1,
	val loopEnd: Int = -1,
	/** Bars of clicks before playback starts (0: none). */
	val countInBars: Int = 0,
) {
	val isLooping: Boolean
		get() = training || loop
}

/** Bridge between the Compose bars and the TuxGuitar player/actions. */
class TGPracticePlayer(private val context: TGContext) {

	companion object {
		const val MIN_PERCENT = 1
		const val MAX_PERCENT = 500
		const val MAX_COUNT_IN_BARS = 2
		private const val PREFS = "practice"
		// lets the play loop of the stopped playback end before starting again (it checks every 10 ms)
		private const val RESTART_DELAY_MILLIS = 100L
	}

	private val player: MidiPlayer
		get() = MidiPlayer.getInstance(context)

	private val handler = Handler(Looper.getMainLooper())
	private var pendingRestart: Runnable? = null
	/** Count-in of the current mode: only in practice mode. */
	private var countInBars = 0
	/** Measure where playback last started (not resumed), the start to go back to outside a loop. */
	private var startMeasure = 1
	private var paused = false

	init {
		player.addListener(TGEventListener { event -> onPlayerEvent(event) })
	}

	/**
	 * The count-in plays when playback starts, not again on every loop pass: the player restarts itself at the end
	 * of the loop, with the count-in when it is on. Called on the player thread, before it acts.
	 */
	private fun onPlayerEvent(event: TGEvent) {
		if (MidiPlayerEvent.EVENT_TYPE != event.eventType) {
			return
		}
		val countDown = player.countDown
		val type: Int? = event.getAttribute(MidiPlayerEvent.PROPERTY_NOTIFICATION_TYPE)
		when (type) {
			MidiPlayerEvent.NOTIFY_LOOP -> countDown.isEnabled = false
			MidiPlayerEvent.NOTIFY_STOPPED -> countDown.isEnabled = countInBars > 0
			// the clicks of one bar follow its time signature, so count them for the bar where playback starts
			MidiPlayerEvent.NOTIFY_COUNT_DOWN_STARTED -> countDown.tickCount =
				if (countInBars <= 1) 0 else (countDown.findMeasureHeader()?.timeSignature?.numerator ?: 4) * countInBars
		}
	}

	fun snapshot(settings: TGPracticeSettings?): TGPlayerSnapshot {
		val song = player.song
		val measureCount = song.countMeasureHeaders()
		val caretMeasure = caretMeasure()
		val startMeasure = if (settings != null && settings.isLooping && settings.loopStart > 0) settings.loopStart else caretMeasure
		return TGPlayerSnapshot(
			running = player.isRunning,
			currentPercent = player.mode.currentPercent,
			metronome = player.isMetronomeEnabled,
			measureCount = measureCount,
			caretMeasure = caretMeasure,
			baseTempo = song.getMeasureHeader(startMeasure.coerceIn(1, measureCount) - 1).tempo.quarterValue,
		)
	}

	// ---- playback ----

	fun playPause(settings: TGPracticeSettings?) {
		cancelRestart()
		if (player.isRunning) {
			paused = true
		} else {
			if (!paused) {
				startMeasure = if (settings != null && settings.isLooping) loopStart(settings) else caretMeasure()
				if (settings != null && settings.isLooping && settings.loopStart > 0) {
					moveCaretToMeasure(settings.loopStart)
				}
			}
			paused = false
			player.countDown.isEnabled = countInBars > 0
		}
		TGActionProcessor(context, TGTransportPlayAction.NAME).process()
	}

	fun stop() {
		cancelRestart()
		paused = false
		TGActionProcessor(context, TGTransportStopAction.NAME).process()
	}

	/**
	 * Back to the start: of the loop when looping, else where playback started. While playing, playback goes on from
	 * there at the same speed (a training pass is not lost), after the count-in when it is on; stopped, only the
	 * cursor moves.
	 */
	fun restart(settings: TGPracticeSettings) {
		// a second tap while restarting with the count-in restarts again rather than only moving the cursor
		val restarting = pendingRestart != null
		cancelRestart()
		val measure = if (settings.isLooping) loopStart(settings) else startMeasure
		if (!player.isRunning && !restarting) {
			paused = false
			player.reset()
			moveCaretToMeasure(measure)
			return
		}
		val song = player.song
		val header = song.getMeasureHeader((measure - 1).coerceIn(0, song.countMeasureHeaders() - 1))
		val tick = MidiTickUtil.getTick(context, header.start)
		if (countInBars <= 0 && player.isRunning) {
			// the play loop jumps there without stopping
			player.setTickPosition(tick)
			return
		}
		// stop and start again, for the count-in; the speed is kept (playing from the play button resets it)
		val percent = player.mode.currentPercent
		if (player.isRunning) {
			player.reset()
		}
		val restart = Runnable {
			pendingRestart = null
			if (player.isRunning) {
				return@Runnable
			}
			player.setTickPosition(tick)
			TGTransport.getInstance(context).goToTickPosition()
			player.mode.currentPercent = percent
			player.countDown.isEnabled = countInBars > 0
			try {
				player.play()
			} catch (e: Throwable) {
				TGErrorManager.getInstance(context).handleError(e)
			}
		}
		pendingRestart = restart
		handler.postDelayed(restart, RESTART_DELAY_MILLIS)
	}

	private fun cancelRestart() {
		pendingRestart?.let { handler.removeCallbacks(it) }
		pendingRestart = null
	}

	private fun loopStart(settings: TGPracticeSettings): Int = if (settings.loopStart > 0) settings.loopStart else 1

	private fun caretMeasure(): Int = TGSongViewController.getInstance(context).caret.measure.number

	fun toggleMetronome() {
		TGActionProcessor(context, TGTransportMetronomeAction.NAME).process()
	}


	private fun moveCaretToMeasure(measure: Int) {
		val track = player.song.getTrack(0)
		val beat = track.getMeasure((measure - 1).coerceIn(0, track.countMeasures() - 1)).getBeat(0)
		val processor = TGActionProcessor(context, TGMoveToAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, beat)
		processor.process()
	}

	// ---- player mode ----

	/** Practice mode: training or fixed speed, with the practice loop range. */
	fun applyPractice(settings: TGPracticeSettings) {
		val mode = player.mode
		mode.type = if (settings.training) MidiPlayerMode.TYPE_CUSTOM else MidiPlayerMode.TYPE_SIMPLE
		mode.isLoop = settings.isLooping
		mode.simplePercent = settings.fixedPercent
		mode.customPercentFrom = settings.fromPercent
		mode.customPercentTo = settings.toPercent
		mode.customPercentIncrement = settings.incrementPercent
		mode.loopSHeader = settings.loopStart
		mode.loopEHeader = settings.loopEnd
		countInBars = settings.countInBars
		updateCountIn()
		// a new range or speed while paused: play starts over from the loop start
		if (!player.isRunning) {
			paused = false
		}
		resetIfStopped()
	}

	/** Play mode: the song as written, optionally looping the whole song. */
	fun applyPlay(loop: Boolean) {
		val mode = player.mode
		mode.type = MidiPlayerMode.TYPE_SIMPLE
		mode.isLoop = loop
		mode.simplePercent = 100
		mode.loopSHeader = -1
		mode.loopEHeader = -1
		countInBars = 0
		updateCountIn()
		resetIfStopped()
		// remove the practice loop markers
		TGSongViewController.getInstance(context).redraw()
	}

	/** Off right away; on, from the next start (not during a loop pass, see [onPlayerEvent]). */
	private fun updateCountIn() {
		if (countInBars <= 0 || !player.isRunning) {
			player.countDown.isEnabled = countInBars > 0
		}
	}

	/** While playing, changes apply on the next loop pass (the player fixes the speed when it starts). */
	private fun resetIfStopped() {
		if (!player.isRunning) {
			player.mode.reset()
		}
	}

	// ---- settings storage ----

	private fun prefs() = TGActivityController.getInstance(context).activity
		?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	fun loadSettings(): TGPracticeSettings {
		val p = prefs() ?: return TGPracticeSettings()
		val d = TGPracticeSettings()
		return TGPracticeSettings(
			training = p.getBoolean("training", d.training),
			loop = p.getBoolean("loop", d.loop),
			fixedPercent = p.getInt("fixedPercent", d.fixedPercent),
			fromPercent = p.getInt("fromPercent", d.fromPercent),
			toPercent = p.getInt("toPercent", d.toPercent),
			incrementPercent = p.getInt("incrementPercent", d.incrementPercent),
			countInBars = p.getInt("countInBars", d.countInBars).coerceIn(0, MAX_COUNT_IN_BARS),
			// the loop range belongs to a song, so it is not restored
		)
	}

	fun saveSettings(settings: TGPracticeSettings) {
		prefs()?.edit()
			?.putBoolean("training", settings.training)
			?.putBoolean("loop", settings.loop)
			?.putInt("fixedPercent", settings.fixedPercent)
			?.putInt("fromPercent", settings.fromPercent)
			?.putInt("toPercent", settings.toPercent)
			?.putInt("incrementPercent", settings.incrementPercent)
			?.putInt("countInBars", settings.countInBars)
			?.apply()
	}
}

/** Conversions between tempo percentages and BPM for a given base tempo. */
object TGTempoMath {
	fun bpm(baseTempo: Int, percent: Int): Int = (baseTempo * percent / 100f).roundToInt()

	fun percent(baseTempo: Int, bpm: Int): Int =
		(bpm * 100f / baseTempo).roundToInt().coerceIn(TGPracticePlayer.MIN_PERCENT, TGPracticePlayer.MAX_PERCENT)

	/**
	 * Percent for a BPM picked with -/+ from [oldPercent]: with tempos above 100 BPM one percent is more
	 * than one BPM, so make sure the value always moves in the direction of the step.
	 */
	fun stepPercent(baseTempo: Int, oldPercent: Int, newBpm: Int): Int {
		val oldBpm = bpm(baseTempo, oldPercent)
		var p = percent(baseTempo, newBpm)
		if (p == oldPercent && newBpm != oldBpm) {
			p = (p + if (newBpm > oldBpm) 1 else -1).coerceIn(TGPracticePlayer.MIN_PERCENT, TGPracticePlayer.MAX_PERCENT)
		}
		return p
	}
}
