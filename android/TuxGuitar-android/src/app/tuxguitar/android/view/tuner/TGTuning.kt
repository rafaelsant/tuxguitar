package app.tuxguitar.android.view.tuner

import app.tuxguitar.song.models.TGTrack
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

/** A string to tune: its MIDI note (as it sounds, with the track offset) and its label, e.g. "E2". */
data class TGTunerString(val midi: Int, val label: String)

object TGTuning {

	private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

	/** E2 A2 D3 G3 B3 E4, for percussion tracks or when there is no song. */
	val STANDARD = listOf(40, 45, 50, 55, 59, 64).map { TGTunerString(it, noteLabel(it)) }

	fun noteLabel(midi: Int): String = NOTE_NAMES[((midi % 12) + 12) % 12] + (midi / 12 - 1)

	fun noteName(midi: Int): String = NOTE_NAMES[((midi % 12) + 12) % 12]

	fun frequency(midi: Double): Double = 440.0 * 2.0.pow((midi - 69) / 12)

	/** MIDI note as a fractional number: 69.0 is A4 = 440 Hz. */
	fun midi(frequency: Float): Double = 69 + 12 * log2(frequency / 440.0)

	/** Strings of the track from the lowest to the highest, or null for percussion. */
	fun stringsOf(track: TGTrack?): List<TGTunerString>? {
		if (track == null || track.isPercussion || track.strings.isEmpty()) {
			return null
		}
		return track.strings
			.map { it.value + track.offset }
			.sorted()
			.map { TGTunerString(it, noteLabel(it)) }
	}

	/** The string whose note is closest to the played one. */
	fun nearest(strings: List<TGTunerString>, midi: Double): TGTunerString? = strings.minByOrNull { abs(midi - it.midi) }

	fun cents(midi: Double, target: Int): Int = ((midi - target) * 100).roundToInt()
}
