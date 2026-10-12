package app.tuxguitar.android.view.dialog.repeat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGCheckRow
import app.tuxguitar.android.view.compose.TGForm
import app.tuxguitar.android.view.compose.TGNumberField
import app.tuxguitar.android.view.compose.TGOption
import app.tuxguitar.android.view.compose.TGRadioList
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.song.models.TGMeasure
import app.tuxguitar.song.models.TGMeasureHeader
import app.tuxguitar.song.models.TGSong
import app.tuxguitar.song.models.TGTrack
import app.tuxguitar.editor.action.composition.TGRepeatCloseAction

class TGRepeatCloseDialog : TGComposeModalFragment(R.string.repeat_close_dlg_title) {

	private var count by mutableStateOf(1)

	private val header: TGMeasureHeader
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER)

	override fun initState() {
		count = header.repeatClose.coerceAtLeast(1)
	}

	@Composable
	override fun Content() {
		TGForm {
			TGNumberField(
				label = stringResource(R.string.repeat_close_dlg_count_label),
				value = count,
				range = 0..100,
				onValueChange = { count = it },
				onValidityChange = {},
				hint = null,
			)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGRepeatCloseAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, getAttribute<TGSong>(TGDocumentContextAttributes.ATTRIBUTE_SONG))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER, header)
		processor.setAttribute(TGRepeatCloseAction.ATTRIBUTE_REPEAT_COUNT, count)
		processor.processOnNewThread()
	}
}
