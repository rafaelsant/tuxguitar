package app.tuxguitar.android.view.dialog.pickstroke

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGCheckRow
import app.tuxguitar.android.view.compose.TGDropdownField
import app.tuxguitar.android.view.compose.TGForm
import app.tuxguitar.android.view.compose.TGFormSection
import app.tuxguitar.android.view.compose.TGOption
import app.tuxguitar.android.view.compose.TGRadioList
import app.tuxguitar.android.view.compose.TGTextField
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.note.TGChangePickStrokeDownAction
import app.tuxguitar.editor.action.note.TGChangePickStrokeUpAction
import app.tuxguitar.song.models.TGBeat
import app.tuxguitar.song.models.TGMeasure
import app.tuxguitar.song.models.TGPickStroke

class TGPickStrokeDialog : TGComposeModalFragment(R.string.pickstroke_dlg_title) {

	private var direction by mutableStateOf(TGPickStroke.PICK_STROKE_NONE)

	override fun initState() {
		direction = getAttribute<TGBeat>(TGDocumentContextAttributes.ATTRIBUTE_BEAT)?.pickStroke?.direction ?: TGPickStroke.PICK_STROKE_NONE
	}

	@Composable
	override fun Content() {
		TGForm {
			TGFormSection(stringResource(R.string.pickstroke_dlg_direction_label))
			TGRadioList(
				listOf(
					TGOption(TGPickStroke.PICK_STROKE_NONE, stringResource(R.string.pickstroke_dlg_direction_none)),
					TGOption(TGPickStroke.PICK_STROKE_UP, stringResource(R.string.pickstroke_dlg_direction_up)),
					TGOption(TGPickStroke.PICK_STROKE_DOWN, stringResource(R.string.pickstroke_dlg_direction_down)),
				),
				direction, { direction = it },
			)
		}
	}

	override fun onOk() {
		if (direction == TGPickStroke.PICK_STROKE_NONE) return
		val action = if (direction == TGPickStroke.PICK_STROKE_UP) TGChangePickStrokeUpAction.NAME else TGChangePickStrokeDownAction.NAME
		val processor = TGActionProcessor(findContext(), action)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, getAttribute<TGBeat>(TGDocumentContextAttributes.ATTRIBUTE_BEAT))
		processor.process()
	}
}
