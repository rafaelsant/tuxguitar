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
import app.tuxguitar.editor.action.track.TGSetTrackNameAction

class TGTrackNameDialog : TGComposeModalFragment(R.string.track_name_dlg_title) {

	private var name by mutableStateOf("")

	private val track: TGTrack
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK)

	override fun initState() {
		name = track.name ?: ""
	}

	@Composable
	override fun Content() {
		TGForm {
			TGTextField(stringResource(R.string.track_name_dlg_name_label), name, { name = it })
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGSetTrackNameAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, track)
		processor.setAttribute(TGSetTrackNameAction.ATTRIBUTE_TRACK_NAME, name)
		processor.processOnNewThread()
	}
}
