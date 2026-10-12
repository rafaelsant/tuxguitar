package app.tuxguitar.android.view.dialog.track

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGForm
import app.tuxguitar.android.view.compose.TGNumberField
import app.tuxguitar.android.view.compose.TGTextField
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.song.models.TGSong
import app.tuxguitar.song.models.TGTrack
import app.tuxguitar.editor.action.track.TGSetTrackStringCountAction

class TGTrackStringCountDialog : TGComposeModalFragment(R.string.track_string_count_dlg_title) {

	private var count by mutableStateOf(6)

	private val track: TGTrack
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK)

	override fun initState() {
		count = track.stringCount()
	}

	@Composable
	override fun Content() {
		TGForm {
			TGNumberField(
				label = stringResource(R.string.track_string_count_dlg_count_label),
				value = count,
				range = TGTrack.MIN_STRINGS..TGTrack.MAX_STRINGS,
				onValueChange = { count = it },
				onValidityChange = {},
				hint = null,
			)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGSetTrackStringCountAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, getAttribute<TGSong>(TGDocumentContextAttributes.ATTRIBUTE_SONG))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, track)
		processor.setAttribute(TGSetTrackStringCountAction.ATTRIBUTE_STRING_COUNT, count)
		processor.process()
	}
}
