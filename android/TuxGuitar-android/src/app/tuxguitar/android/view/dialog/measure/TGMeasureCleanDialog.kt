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
import app.tuxguitar.editor.action.measure.TGCleanMeasureListAction

class TGMeasureCleanDialog : TGComposeModalFragment(R.string.measure_clean_dlg_title) {

	private var from by mutableStateOf(1)
	private var to by mutableStateOf(1)

	private val maximum: Int
		get() = getAttribute<TGTrack>(TGDocumentContextAttributes.ATTRIBUTE_TRACK).countMeasures()

	override fun initState() {
		from = getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE).number
		to = from
	}

	@Composable
	override fun Content() {
		TGForm {
			TGNumberField(
				label = stringResource(R.string.measure_clean_dlg_from_label),
				value = from,
				range = 1..maximum,
				onValueChange = { from = it; if (to < it) to = it },
				onValidityChange = {},
				hint = null,
			)
			TGNumberField(
				label = stringResource(R.string.measure_clean_dlg_to_label),
				value = to,
				range = 1..maximum,
				onValueChange = { to = it; if (from > it) from = it },
				onValidityChange = {},
				hint = null,
			)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGCleanMeasureListAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, getAttribute<TGTrack>(TGDocumentContextAttributes.ATTRIBUTE_TRACK))
		processor.setAttribute(TGCleanMeasureListAction.ATTRIBUTE_MEASURE_NUMBER_1, from)
		processor.setAttribute(TGCleanMeasureListAction.ATTRIBUTE_MEASURE_NUMBER_2, to)
		processor.process()
	}
}
