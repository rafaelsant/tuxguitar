package app.tuxguitar.android.view.dialog.tremoloPicking

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
import app.tuxguitar.editor.action.effect.TGChangeTremoloPickingAction
import app.tuxguitar.song.models.effects.TGEffectTremoloPicking

class TGTremoloPickingDialog : TGComposeModalFragment(R.string.tremolo_picking_dlg_title) {

	private var duration by mutableStateOf(TGDuration.EIGHTH)

	override fun initState() {
		note?.effect?.takeIf { it.isTremoloPicking }?.let { duration = it.tremoloPicking.duration.value }
	}

	@Composable
	override fun Content() {
		TGForm {
			TGRadioList(
				listOf(
					TGOption(TGDuration.EIGHTH, stringResource(R.string.tremolo_picking_dlg_duration_8)),
					TGOption(TGDuration.SIXTEENTH, stringResource(R.string.tremolo_picking_dlg_duration_16)),
					TGOption(TGDuration.THIRTY_SECOND, stringResource(R.string.tremolo_picking_dlg_duration_32)),
				),
				duration, { duration = it },
			)
		}
	}

	override fun onOk() {
		val effect = songManager.factory.newEffectTremoloPicking()
		effect.duration.value = duration
		updateEffect(effect)
	}

	private val note: TGNote?
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_NOTE)

	private val songManager: TGSongManager
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)

	override fun secondaryActionLabel(): Int = R.string.global_button_clean

	override fun onSecondaryAction() = updateEffect(null)

	private fun updateEffect(effect: TGEffectTremoloPicking?) {
		val processor = TGActionProcessor(findContext(), TGChangeTremoloPickingAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, getAttribute<TGBeat>(TGDocumentContextAttributes.ATTRIBUTE_BEAT))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_STRING, getAttribute<TGString>(TGDocumentContextAttributes.ATTRIBUTE_STRING))
		processor.setAttribute(TGChangeTremoloPickingAction.ATTRIBUTE_EFFECT, effect)
		processor.process()
	}
}
