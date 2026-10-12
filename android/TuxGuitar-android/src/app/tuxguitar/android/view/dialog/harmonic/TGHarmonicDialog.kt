package app.tuxguitar.android.view.dialog.harmonic

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
import app.tuxguitar.editor.action.effect.TGChangeHarmonicNoteAction
import app.tuxguitar.song.models.effects.TGEffectHarmonic

class TGHarmonicDialog : TGComposeModalFragment(R.string.harmonic_dlg_title) {

	private var type by mutableStateOf(TGEffectHarmonic.TYPE_NATURAL)
	private var data by mutableStateOf(0)

	/** Natural harmonics only exist on some frets. */
	private val naturalAvailable: Boolean
		get() = note?.let { n -> TGEffectHarmonic.NATURAL_FREQUENCIES.any { (n.value % 12) == (it[0] % 12) } } ?: false

	override fun initState() {
		val effect = note?.effect
		if (effect != null && effect.isHarmonic) {
			type = effect.harmonic.type
			data = effect.harmonic.data
		} else {
			type = if (naturalAvailable) TGEffectHarmonic.TYPE_NATURAL else TGEffectHarmonic.TYPE_ARTIFICIAL
		}
	}

	private fun typeKey(type: Int): String = when (type) {
		TGEffectHarmonic.TYPE_NATURAL -> TGEffectHarmonic.KEY_NATURAL
		TGEffectHarmonic.TYPE_ARTIFICIAL -> TGEffectHarmonic.KEY_ARTIFICIAL
		TGEffectHarmonic.TYPE_TAPPED -> TGEffectHarmonic.KEY_TAPPED
		TGEffectHarmonic.TYPE_PINCH -> TGEffectHarmonic.KEY_PINCH
		TGEffectHarmonic.TYPE_SEMI -> TGEffectHarmonic.KEY_SEMI
		else -> ""
	}

	@Composable
	override fun Content() {
		TGForm {
			val types = listOf(
				TGOption(TGEffectHarmonic.TYPE_NATURAL, stringResource(R.string.harmonic_dlg_type_nh)),
				TGOption(TGEffectHarmonic.TYPE_ARTIFICIAL, stringResource(R.string.harmonic_dlg_type_ah)),
				TGOption(TGEffectHarmonic.TYPE_TAPPED, stringResource(R.string.harmonic_dlg_type_th)),
				TGOption(TGEffectHarmonic.TYPE_PINCH, stringResource(R.string.harmonic_dlg_type_ph)),
				TGOption(TGEffectHarmonic.TYPE_SEMI, stringResource(R.string.harmonic_dlg_type_sh)),
			).filter { it.value != TGEffectHarmonic.TYPE_NATURAL || naturalAvailable }
			TGRadioList(types, type, {
				type = it
				data = 0
			})
			// every type but natural also needs the harmonic interval
			if (type != TGEffectHarmonic.TYPE_NATURAL) {
				val key = typeKey(type)
				TGDropdownField(
					stringResource(R.string.harmonic_dlg_title),
					TGEffectHarmonic.NATURAL_FREQUENCIES.indices.map { TGOption(it, key + "(" + TGEffectHarmonic.NATURAL_FREQUENCIES[it][0] + ")") },
					data, { data = it },
				)
			}
		}
	}

	override fun onOk() {
		val effect = songManager.factory.newEffectHarmonic()
		effect.type = type
		effect.data = data
		updateEffect(effect)
	}

	private val note: TGNote?
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_NOTE)

	private val songManager: TGSongManager
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)

	override fun secondaryActionLabel(): Int = R.string.global_button_clean

	override fun onSecondaryAction() = updateEffect(null)

	private fun updateEffect(effect: TGEffectHarmonic?) {
		val processor = TGActionProcessor(findContext(), TGChangeHarmonicNoteAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, getAttribute<TGBeat>(TGDocumentContextAttributes.ATTRIBUTE_BEAT))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_STRING, getAttribute<TGString>(TGDocumentContextAttributes.ATTRIBUTE_STRING))
		processor.setAttribute(TGChangeHarmonicNoteAction.ATTRIBUTE_EFFECT, effect)
		processor.process()
	}
}
