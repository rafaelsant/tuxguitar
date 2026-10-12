package app.tuxguitar.android.view.dialog.measure

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
import app.tuxguitar.editor.action.measure.TGPasteMeasureAction

class TGMeasurePasteDialog : TGComposeModalFragment(R.string.measure_paste_dlg_title) {

	companion object {
		const val ATTRIBUTE_PASTE_MODE = "pasteMode"
		const val ATTRIBUTE_PASTE_COUNT = "pasteCount"
		@JvmField val TRANSFER_TYPE_REPLACE: Int = 1
		@JvmField val TRANSFER_TYPE_INSERT: Int = 2
	}

	private var count by mutableStateOf(1)
	private var mode by mutableStateOf(TRANSFER_TYPE_REPLACE)

	@Composable
	override fun Content() {
		TGForm {
			TGNumberField(
				label = stringResource(R.string.measure_paste_dlg_count_label),
				value = count,
				range = 1..100,
				onValueChange = { count = it },
				onValidityChange = {},
				hint = null,
			)
			TGRadioList(
				listOf(
					TGOption(TRANSFER_TYPE_REPLACE, stringResource(R.string.measure_paste_dlg_options_mode_replace)),
					TGOption(TRANSFER_TYPE_INSERT, stringResource(R.string.measure_paste_dlg_options_mode_insert)),
				),
				mode, { mode = it },
			)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGPasteMeasureAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, getAttribute<TGSong>(TGDocumentContextAttributes.ATTRIBUTE_SONG))
		processor.setAttribute(ATTRIBUTE_PASTE_COUNT, count)
		processor.setAttribute(ATTRIBUTE_PASTE_MODE, mode)
		processor.process()
	}
}
