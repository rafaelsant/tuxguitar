package app.tuxguitar.android.view.dialog.stroke

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
import app.tuxguitar.android.view.compose.TGNumberField
import app.tuxguitar.android.view.compose.TGOption
import app.tuxguitar.android.view.compose.TGRadioList
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.song.managers.TGSongManager
import app.tuxguitar.song.models.TGBeat
import app.tuxguitar.song.models.TGDuration
import app.tuxguitar.song.models.TGMeasure
import app.tuxguitar.song.models.TGNote
import app.tuxguitar.song.models.TGString
import app.tuxguitar.editor.action.note.TGChangeStrokeAction
import app.tuxguitar.song.models.TGStroke

class TGStrokeDialog : TGComposeModalFragment(R.string.stroke_dlg_title) {

	private var direction by mutableStateOf(TGStroke.STROKE_NONE)
	private var duration by mutableStateOf(TGDuration.SIXTEENTH)

	private val beat: TGBeat?
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT)

	override fun initState() {
		val stroke = beat?.stroke
		direction = stroke?.direction ?: TGStroke.STROKE_NONE
		if (stroke != null && stroke.direction != TGStroke.STROKE_NONE) {
			duration = stroke.value
		}
	}

	@Composable
	override fun Content() {
		TGForm {
			TGFormSection(stringResource(R.string.stroke_dlg_direction_label))
			TGRadioList(
				listOf(
					TGOption(TGStroke.STROKE_NONE, stringResource(R.string.stroke_dlg_direction_none)),
					TGOption(TGStroke.STROKE_UP, stringResource(R.string.stroke_dlg_direction_up)),
					TGOption(TGStroke.STROKE_DOWN, stringResource(R.string.stroke_dlg_direction_down)),
				),
				direction, { direction = it },
			)
			TGRadioList(
				listOf(
					TGOption(TGDuration.QUARTER, stringResource(R.string.stroke_dlg_duration_4)),
					TGOption(TGDuration.EIGHTH, stringResource(R.string.stroke_dlg_duration_8)),
					TGOption(TGDuration.SIXTEENTH, stringResource(R.string.stroke_dlg_duration_16)),
					TGOption(TGDuration.THIRTY_SECOND, stringResource(R.string.stroke_dlg_duration_32)),
					TGOption(TGDuration.SIXTY_FOURTH, stringResource(R.string.stroke_dlg_duration_64)),
				),
				duration, { duration = it },
				enabled = direction != TGStroke.STROKE_NONE,
			)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGChangeStrokeAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, beat)
		processor.setAttribute(TGChangeStrokeAction.ATTRIBUTE_STROKE_DIRECTION, direction)
		processor.setAttribute(TGChangeStrokeAction.ATTRIBUTE_STROKE_VALUE, duration)
		processor.process()
	}
}
