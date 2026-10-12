package app.tuxguitar.android.view.dialog.timeSignature

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
import app.tuxguitar.editor.action.composition.TGChangeTimeSignatureAction
import app.tuxguitar.song.managers.TGSongManager
import app.tuxguitar.song.models.TGMeasureHeader
import app.tuxguitar.song.models.TGSong

class TGTimeSignatureDialog : TGComposeModalFragment(R.string.time_signature_dlg_title) {

	private var numerator by mutableStateOf(4)
	private var denominator by mutableStateOf(4)
	private var applyToEnd by mutableStateOf(true)

	private val header: TGMeasureHeader
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER)

	override fun initState() {
		numerator = header.timeSignature.numerator
		denominator = header.timeSignature.denominator.value
	}

	@Composable
	override fun Content() {
		TGForm {
			TGDropdownField(
				stringResource(R.string.time_signature_dlg_ts_numerator_label),
				(1..32).map { TGOption(it, it.toString()) },
				numerator, { numerator = it },
			)
			TGDropdownField(
				stringResource(R.string.time_signature_dlg_ts_denominator_label),
				listOf(1, 2, 4, 8, 16, 32).map { TGOption(it, it.toString()) },
				denominator, { denominator = it },
			)
			TGCheckRow(stringResource(R.string.time_signature_dlg_options_apply_to_end), applyToEnd, { applyToEnd = it })
		}
	}

	override fun onOk() {
		val songManager = getAttribute<TGSongManager>(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)
		val timeSignature = songManager.factory.newTimeSignature()
		timeSignature.numerator = numerator
		timeSignature.denominator.value = denominator

		val processor = TGActionProcessor(findContext(), TGChangeTimeSignatureAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, getAttribute<TGSong>(TGDocumentContextAttributes.ATTRIBUTE_SONG))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER, header)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TIME_SIGNATURE, timeSignature)
		processor.setAttribute(TGChangeTimeSignatureAction.ATTRIBUTE_APPLY_TO_END, applyToEnd)
		processor.processOnNewThread()
	}
}
