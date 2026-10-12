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
import app.tuxguitar.editor.action.measure.TGAddMeasureListAction

class TGMeasureAddDialog : TGComposeModalFragment(R.string.measure_add_dlg_title) {

	private var count by mutableStateOf(1)
	private var position by mutableStateOf(1)

	private val song: TGSong
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG)

	private val header: TGMeasureHeader
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER)

	override fun initState() {
		position = song.countMeasureHeaders() + 1
	}

	@Composable
	override fun Content() {
		TGForm {
			TGNumberField(
				label = stringResource(R.string.measure_add_dlg_count_label),
				value = count,
				range = 1..100,
				onValueChange = { count = it },
				onValidityChange = {},
				hint = null,
			)
			TGRadioList(
				listOf(
					TGOption(header.number, stringResource(R.string.measure_add_dlg_options_before_position)),
					TGOption(header.number + 1, stringResource(R.string.measure_add_dlg_options_after_position)),
					TGOption(song.countMeasureHeaders() + 1, stringResource(R.string.measure_add_dlg_options_at_end)),
				),
				position, { position = it },
			)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGAddMeasureListAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, song)
		processor.setAttribute(TGAddMeasureListAction.ATTRIBUTE_MEASURE_COUNT, count)
		processor.setAttribute(TGAddMeasureListAction.ATTRIBUTE_MEASURE_NUMBER, position)
		processor.process()
	}
}
