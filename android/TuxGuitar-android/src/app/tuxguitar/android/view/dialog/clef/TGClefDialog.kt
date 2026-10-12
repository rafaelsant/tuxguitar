package app.tuxguitar.android.view.dialog.clef

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
import app.tuxguitar.editor.action.composition.TGChangeClefAction
import app.tuxguitar.song.models.TGMeasure
import app.tuxguitar.song.models.TGSong
import app.tuxguitar.song.models.TGTrack

class TGClefDialog : TGComposeModalFragment(R.string.clef_dlg_title) {

	private var clef by mutableStateOf(TGMeasure.CLEF_TREBLE)
	private var applyToEnd by mutableStateOf(true)

	override fun initState() {
		clef = getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE).clef
	}

	@Composable
	override fun Content() {
		TGForm {
			TGFormSection(stringResource(R.string.clef_dlg_clef_label))
			TGRadioList(
				listOf(
					TGOption(TGMeasure.CLEF_TREBLE, stringResource(R.string.clef_dlg_clef_value_treble)),
					TGOption(TGMeasure.CLEF_BASS, stringResource(R.string.clef_dlg_clef_value_bass)),
					TGOption(TGMeasure.CLEF_TENOR, stringResource(R.string.clef_dlg_clef_value_tenor)),
					TGOption(TGMeasure.CLEF_ALTO, stringResource(R.string.clef_dlg_clef_value_alto)),
				),
				clef, { clef = it },
			)
			TGCheckRow(stringResource(R.string.clef_dlg_options_apply_to_end), applyToEnd, { applyToEnd = it })
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGChangeClefAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, getAttribute<TGSong>(TGDocumentContextAttributes.ATTRIBUTE_SONG))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, getAttribute<TGTrack>(TGDocumentContextAttributes.ATTRIBUTE_TRACK))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.setAttribute(TGChangeClefAction.ATTRIBUTE_CLEF, clef)
		processor.setAttribute(TGChangeClefAction.ATTRIBUTE_APPLY_TO_END, applyToEnd)
		processor.process()
	}
}
