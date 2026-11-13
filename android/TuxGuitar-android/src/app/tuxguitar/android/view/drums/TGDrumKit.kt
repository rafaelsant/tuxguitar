package app.tuxguitar.android.view.drums

import app.tuxguitar.android.R
import app.tuxguitar.util.TGMessagesManager

/**
 * Pieces of a drum kit and the General MIDI notes each one sends. The modules send a different note for each zone or
 * articulation (rimshot, open / closed / foot hi-hat, ride bell), so the notes are grouped by piece: that is what is
 * compared with the score. Notes of the Yamaha DTX402 kits follow General MIDI.
 */
enum class TGDrumPiece(val label: Int, val notes: Set<Int>) {
	HI_HAT(R.string.drums_hihat, setOf(42, 44, 46)),
	CRASH(R.string.drums_crash, setOf(49, 52, 55, 57)),
	RIDE(R.string.drums_ride, setOf(51, 53, 59)),
	TOM_1(R.string.drums_tom1, setOf(48, 50)),
	TOM_2(R.string.drums_tom2, setOf(45, 47)),
	FLOOR_TOM(R.string.drums_floor_tom, setOf(41, 43)),
	SNARE(R.string.drums_snare, setOf(37, 38, 40)),
	KICK(R.string.drums_kick, setOf(35, 36));

	companion object {
		fun of(note: Int): TGDrumPiece? = entries.firstOrNull { note in it.notes }
	}
}

object TGDrumKit {
	/** General MIDI name of a percussion note (e.g. "Acoustic Snare"), as TuxGuitar shows it. */
	fun noteName(note: Int): String? =
		runCatching { TGMessagesManager.getMidiPercussionKeys().firstOrNull { it.value == note }?.name }.getOrNull()
}
