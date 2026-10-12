package app.tuxguitar.android.view.dialog.keySignature

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
import app.tuxguitar.editor.action.composition.TGChangeKeySignatureAction
import app.tuxguitar.song.models.TGMeasure
import app.tuxguitar.song.models.TGTrack

class TGKeySignatureDialog : TGComposeModalFragment(R.string.key_signature_dlg_title) {

	private var keySignature by mutableStateOf(0)
	private var applyToEnd by mutableStateOf(true)

	override fun initState() {
		keySignature = getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE).keySignature
	}

	@Composable
	override fun Content() {
		TGForm {
			TGDropdownField(
				stringResource(R.string.key_signature_dlg_ks_label),
				listOf(
					TGOption(0, stringResource(R.string.key_signature_dlg_ks_value_natural)),
					TGOption(1, stringResource(R.string.key_signature_dlg_ks_value_sharp_1)),
					TGOption(2, stringResource(R.string.key_signature_dlg_ks_value_sharp_2)),
					TGOption(3, stringResource(R.string.key_signature_dlg_ks_value_sharp_3)),
					TGOption(4, stringResource(R.string.key_signature_dlg_ks_value_sharp_4)),
					TGOption(5, stringResource(R.string.key_signature_dlg_ks_value_sharp_5)),
					TGOption(6, stringResource(R.string.key_signature_dlg_ks_value_sharp_6)),
					TGOption(7, stringResource(R.string.key_signature_dlg_ks_value_sharp_7)),
					TGOption(8, stringResource(R.string.key_signature_dlg_ks_value_flat_1)),
					TGOption(9, stringResource(R.string.key_signature_dlg_ks_value_flat_2)),
					TGOption(10, stringResource(R.string.key_signature_dlg_ks_value_flat_3)),
					TGOption(11, stringResource(R.string.key_signature_dlg_ks_value_flat_4)),
					TGOption(12, stringResource(R.string.key_signature_dlg_ks_value_flat_5)),
					TGOption(13, stringResource(R.string.key_signature_dlg_ks_value_flat_6)),
					TGOption(14, stringResource(R.string.key_signature_dlg_ks_value_flat_7)),
				),
				keySignature, { keySignature = it },
			)
			TGCheckRow(stringResource(R.string.key_signature_dlg_options_apply_to_end), applyToEnd, { applyToEnd = it })
		}
	}

	override fun onOk() {
		val processor = TGActionProcessor(findContext(), TGChangeKeySignatureAction.NAME)
		processor.setAttribute(TGChangeKeySignatureAction.ATTRIBUTE_KEY_SIGNATURE, keySignature)
		processor.setAttribute(TGChangeKeySignatureAction.ATTRIBUTE_APPLY_TO_END, applyToEnd)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, getAttribute<TGTrack>(TGDocumentContextAttributes.ATTRIBUTE_TRACK))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.processOnNewThread()
	}
}
