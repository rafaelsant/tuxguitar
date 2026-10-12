package app.tuxguitar.android.view.dialog.tripletFeel

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
import app.tuxguitar.editor.action.composition.TGChangeTripletFeelAction
import app.tuxguitar.song.models.TGMeasureHeader
import app.tuxguitar.song.models.TGSong

class TGTripletFeelDialog : TGComposeModalFragment(R.string.triplet_feel_dlg_title) {

	private var tripletFeel by mutableStateOf(TGMeasureHeader.TRIPLET_FEEL_NONE)
	private var applyToEnd by mutableStateOf(true)

	private val header: TGMeasureHeader
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER)

	override fun initState() {
		tripletFeel = header.tripletFeel
	}

	@Composable
	override fun Content() {
		TGForm {
			TGRadioList(
				listOf(
					TGOption(TGMeasureHeader.TRIPLET_FEEL_NONE, stringResource(R.string.triplet_feel_dlg_none)),
					TGOption(TGMeasureHeader.TRIPLET_FEEL_EIGHTH, stringResource(R.string.triplet_feel_dlg_eighth)),
					TGOption(TGMeasureHeader.TRIPLET_FEEL_SIXTEENTH, stringResource(R.string.triplet_feel_dlg_sixteenth)),
				),
				tripletFeel, { tripletFeel = it },
			)
			TGCheckRow(stringResource(R.string.triplet_feel_dlg_options_apply_to_end), applyToEnd, { applyToEnd = it })
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGChangeTripletFeelAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, getAttribute<TGSong>(TGDocumentContextAttributes.ATTRIBUTE_SONG))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER, header)
		processor.setAttribute(TGChangeTripletFeelAction.ATTRIBUTE_TRIPLET_FEEL, tripletFeel)
		processor.setAttribute(TGChangeTripletFeelAction.ATTRIBUTE_APPLY_TO_END, applyToEnd)
		processor.processOnNewThread()
	}
}
