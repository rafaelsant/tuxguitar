package app.tuxguitar.android.view.practice

import android.content.Context
import app.tuxguitar.android.action.impl.caret.TGMoveToAction
import app.tuxguitar.android.action.impl.transport.TGTransportPlayAction
import app.tuxguitar.android.action.impl.transport.TGTransportStopAction
import app.tuxguitar.android.activity.TGActivityController
import app.tuxguitar.android.view.tablature.TGSongViewController
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.transport.TGTransportCountDownAction
import app.tuxguitar.editor.action.transport.TGTransportMetronomeAction
import app.tuxguitar.player.base.MidiPlayer
import app.tuxguitar.player.base.MidiPlayerMode
import app.tuxguitar.util.TGContext
import kotlin.math.roundToInt

/** Player state read by the bars; refreshed by polling while they are shown. */
data class TGPlayerSnapshot(
	val running: Boolean,
	val currentPercent: Int,
	val metronome: Boolean,
	val countIn: Boolean,
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
) {
	val isLooping: Boolean
		get() = training || loop
}

/** Bridge between the Compose bars and the TuxGuitar player/actions. */
class TGPracticePlayer(private val context: TGContext) {

	companion object {
		const val MIN_PERCENT = 1
		const val MAX_PERCENT = 500
		private const val PREFS = "practice"
	}

	private val player: MidiPlayer
		get() = MidiPlayer.getInstance(context)

	fun snapshot(settings: TGPracticeSettings?): TGPlayerSnapshot {
		val song = player.song
		val measureCount = song.countMeasureHeaders()
		val caretMeasure = TGSongViewController.getInstance(context).caret.measure.number
		val startMeasure = if (settings != null && settings.isLooping && settings.loopStart > 0) settings.loopStart else caretMeasure
		return TGPlayerSnapshot(
			running = player.isRunning,
			currentPercent = player.mode.currentPercent,
			metronome = player.isMetronomeEnabled,
			countIn = player.countDown.isEnabled,
			measureCount = measureCount,
			caretMeasure = caretMeasure,
			baseTempo = song.getMeasureHeader(startMeasure.coerceIn(1, measureCount) - 1).tempo.quarterValue,
		)
	}

	// ---- playback ----

	fun playPause(settings: TGPracticeSettings?) {
		if (!player.isRunning && settings != null && settings.isLooping && settings.loopStart > 0) {
			moveCaretToMeasure(settings.loopStart)
		}
		TGActionProcessor(context, TGTransportPlayAction.NAME).process()
	}

	fun stop() {
		TGActionProcessor(context, TGTransportStopAction.NAME).process()
	}

	fun toggleMetronome() {
		TGActionProcessor(context, TGTransportMetronomeAction.NAME).process()
	}

	fun toggleCountIn() {
		TGActionProcessor(context, TGTransportCountDownAction.NAME).process()
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
		resetIfStopped()
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
