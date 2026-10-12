package app.tuxguitar.android.view.dialog.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGDropdownField
import app.tuxguitar.android.view.compose.TGForm
import app.tuxguitar.android.view.compose.TGFormSection
import app.tuxguitar.android.view.compose.TGOption
import app.tuxguitar.android.view.compose.TGRepeatingButton
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.track.TGChangeTrackTuningAction
import app.tuxguitar.song.managers.TGSongManager
import app.tuxguitar.song.models.TGSong
import app.tuxguitar.song.models.TGTrack

/** Track tuning: one row per string (highest first) changed by semitones, presets and the offset. */
class TGTrackTuningDialog : TGComposeModalFragment(R.string.track_tuning_dlg_title) {

	private class Preset(val name: String, val values: List<Int>)

	private val tuning = mutableStateListOf<Int>()
	private var offset by mutableStateOf(0)
	private var percussion = false
	private var presets = listOf<Preset>()

	private val songManager: TGSongManager
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)

	private val track: TGTrack
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK)

	override fun initState() {
		val song = getAttribute<TGSong>(TGDocumentContextAttributes.ATTRIBUTE_SONG)
		percussion = songManager.isPercussionChannel(song, track.channelId)
		presets = findActivity().tuningManager.tgTunings.map { Preset(it.name, it.values.toList()) }
		tuning.clear()
		tuning.addAll((1..track.stringCount()).map { track.getString(it).value })
		offset = track.offset
	}

	private fun validCount(): Boolean = tuning.size in TGTrack.MIN_STRINGS..TGTrack.MAX_STRINGS

	override fun isOkEnabled(): Boolean = validCount()

	@Composable
	override fun Content() {
		TGForm {
			val current = presets.firstOrNull { it.values == tuning.toList() }
			TGDropdownField(
				stringResource(R.string.track_tuning_dlg_preset_label),
				listOf(TGOption<Preset?>(null, stringResource(R.string.track_tuning_dlg_preset_select_value))) + presets.map { TGOption<Preset?>(it, it.name) },
				current,
				{ preset -> if (preset != null) { tuning.clear(); tuning.addAll(preset.values) } },
			)

			TGFormSection(stringResource(R.string.track_tuning_dlg_strings_label))
			tuning.forEachIndexed { index, value ->
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
					Text((index + 1).toString(), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(20.dp))
					Text(noteLabel(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.width(56.dp))
					TGRepeatingButton("−", stringResource(R.string.track_tuning_dlg_semitone_down), value > 0) {
						if (tuning[index] > 0) { tuning[index] = tuning[index] - 1; true } else false
					}
					TGRepeatingButton("+", stringResource(R.string.track_tuning_dlg_semitone_up), value < 127) {
						if (tuning[index] < 127) { tuning[index] = tuning[index] + 1; true } else false
					}
					IconButton(onClick = { tuning.removeAt(index) }) {
						Icon(painterResource(R.drawable.ic_bar_delete_24dp), stringResource(R.string.track_tuning_dlg_remove_string, index + 1))
					}
				}
			}
			// a new string goes below the lowest one, a fourth lower, like most instruments
			OutlinedButton(onClick = { tuning.add(((tuning.lastOrNull() ?: 52) - 5).coerceAtLeast(0)) }) {
				Icon(painterResource(R.drawable.ic_bar_add_24dp), null)
				Text(stringResource(R.string.track_tuning_dlg_add_string), modifier = Modifier.padding(start = 8.dp))
			}
			if (!validCount()) {
				Text(
					stringResource(R.string.track_tuning_dlg_range_error, TGTrack.MIN_STRINGS, TGTrack.MAX_STRINGS),
					color = MaterialTheme.colorScheme.error,
				)
			}

			TGDropdownField(
				stringResource(R.string.track_tuning_dlg_offset_label),
				(TGTrack.MIN_OFFSET..TGTrack.MAX_OFFSET).map { TGOption(it, stringResource(R.string.track_tuning_dlg_offset_select_value, it)) },
				offset, { offset = it },
				enabled = !percussion,
			)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGChangeTrackTuningAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, track)
		// only what changed is passed, as the action rewrites the notes when the strings change
		val oldValues = (1..track.stringCount()).map { track.getString(it).value }
		if (oldValues != tuning.toList()) {
			val strings = tuning.mapIndexed { i, value -> TGSongManager.newString(songManager.factory, i + 1, value) }
			processor.setAttribute(TGChangeTrackTuningAction.ATTRIBUTE_STRINGS, strings)
		}
		if (offset != track.offset) {
			processor.setAttribute(TGChangeTrackTuningAction.ATTRIBUTE_OFFSET, offset)
		}
		processor.process()
	}

	companion object {
		private val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

		/** MIDI note as name and octave, e.g. 64 -> "E5" (same numbering as the previous tuning dialog). */
		fun noteLabel(value: Int): String = NOTE_NAMES[value % 12] + (value / 12)
	}
}
