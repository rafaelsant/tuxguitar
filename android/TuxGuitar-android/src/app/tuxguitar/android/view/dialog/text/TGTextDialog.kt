package app.tuxguitar.android.view.dialog.text

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
import app.tuxguitar.editor.action.note.TGInsertTextAction
import app.tuxguitar.editor.action.note.TGRemoveTextAction
import app.tuxguitar.song.models.TGBeat

class TGTextDialog : TGComposeModalFragment(R.string.text_dlg_title) {

	private var text by mutableStateOf("")

	private val beat: TGBeat
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT)

	override fun initState() {
		text = beat.text?.value ?: ""
	}

	@Composable
	override fun Content() {
		TGForm {
			TGTextField(stringResource(R.string.text_dlg_title), text, { text = it }, singleLine = false)
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGInsertTextAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, beat)
		processor.setAttribute(TGInsertTextAction.ATTRIBUTE_TEXT_VALUE, text)
		processor.process()
	}

	override fun secondaryActionLabel(): Int = R.string.global_button_clean

	override fun onSecondaryAction() {
		val processor = TGActionProcessor(findContext(), TGRemoveTextAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, beat)
		processor.process()
	}
}
