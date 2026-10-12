package app.tuxguitar.android.view.dialog.grace

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
import app.tuxguitar.editor.action.effect.TGChangeGraceNoteAction
import app.tuxguitar.song.models.TGVelocities
import app.tuxguitar.song.models.effects.TGEffectGrace

class TGGraceDialog : TGComposeModalFragment(R.string.grace_dlg_title) {

	private var fret by mutableStateOf(0)
	private var dead by mutableStateOf(false)
	private var onBeat by mutableStateOf(false)
	private var duration by mutableStateOf(TGEffectGrace.DURATION_SIXTEENTH)
	private var dynamic by mutableStateOf(TGVelocities.DEFAULT)
	private var transition by mutableStateOf(TGEffectGrace.TRANSITION_NONE)

	override fun initState() {
		val note = note ?: return
		val grace = note.effect.takeIf { it.isGrace }?.grace
		fret = grace?.fret ?: note.value
		if (grace != null) {
			dead = grace.isDead
			onBeat = grace.isOnBeat
			duration = grace.duration
			dynamic = grace.dynamic
			transition = grace.transition
		}
	}

	@Composable
	override fun Content() {
		TGForm {
			TGNumberField(
				label = stringResource(R.string.grace_dlg_fret_label),
				value = fret,
				range = 0..100,
				onValueChange = { fret = it },
				onValidityChange = {},
				hint = null,
			)
			TGCheckRow(stringResource(R.string.grace_dlg_dead_note_label), dead, { dead = it })

			TGFormSection(stringResource(R.string.grace_dlg_position_label))
			TGRadioList(
				listOf(
					TGOption(false, stringResource(R.string.grace_dlg_position_before_beat)),
					TGOption(true, stringResource(R.string.grace_dlg_position_on_beat)),
				),
				onBeat, { onBeat = it },
			)

			TGFormSection(stringResource(R.string.grace_dlg_duration_label))
			TGRadioList(
				listOf(
					TGOption(TGEffectGrace.DURATION_SIXTEENTH, stringResource(R.string.grace_dlg_duration_16)),
					TGOption(TGEffectGrace.DURATION_THIRTY_SECOND, stringResource(R.string.grace_dlg_duration_32)),
					TGOption(TGEffectGrace.DURATION_SIXTY_FOURTH, stringResource(R.string.grace_dlg_duration_64)),
				),
				duration, { duration = it },
			)

			TGDropdownField(
				stringResource(R.string.grace_dlg_dynamic_label),
				listOf(
					TGOption(TGVelocities.PIANO_PIANISSIMO, stringResource(R.string.grace_dlg_dynamic_ppp)),
					TGOption(TGVelocities.PIANISSIMO, stringResource(R.string.grace_dlg_dynamic_pp)),
					TGOption(TGVelocities.PIANO, stringResource(R.string.grace_dlg_dynamic_p)),
					TGOption(TGVelocities.MEZZO_PIANO, stringResource(R.string.grace_dlg_dynamic_mp)),
					TGOption(TGVelocities.MEZZO_FORTE, stringResource(R.string.grace_dlg_dynamic_mf)),
					TGOption(TGVelocities.FORTE, stringResource(R.string.grace_dlg_dynamic_f)),
					TGOption(TGVelocities.FORTISSIMO, stringResource(R.string.grace_dlg_dynamic_ff)),
					TGOption(TGVelocities.FORTE_FORTISSIMO, stringResource(R.string.grace_dlg_dynamic_fff)),
				),
				dynamic, { dynamic = it },
			)

			TGFormSection(stringResource(R.string.grace_dlg_transition_label))
			TGRadioList(
				listOf(
					TGOption(TGEffectGrace.TRANSITION_NONE, stringResource(R.string.grace_dlg_transition_none)),
					TGOption(TGEffectGrace.TRANSITION_BEND, stringResource(R.string.grace_dlg_transition_bend)),
					TGOption(TGEffectGrace.TRANSITION_SLIDE, stringResource(R.string.grace_dlg_transition_slide)),
					TGOption(TGEffectGrace.TRANSITION_HAMMER, stringResource(R.string.grace_dlg_transition_hammer)),
				),
				transition, { transition = it },
			)
		}
	}

	override fun onOk() {
		val effect = songManager.factory.newEffectGrace()
		effect.isDead = dead
		effect.isOnBeat = onBeat
		effect.fret = fret
		effect.duration = duration
		effect.dynamic = dynamic
		effect.transition = transition
		updateEffect(effect)
	}

	private val note: TGNote?
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_NOTE)

	private val songManager: TGSongManager
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)

	override fun secondaryActionLabel(): Int = R.string.global_button_clean

	override fun onSecondaryAction() = updateEffect(null)

	private fun updateEffect(effect: TGEffectGrace?) {
		val processor = TGActionProcessor(findContext(), TGChangeGraceNoteAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, getAttribute<TGBeat>(TGDocumentContextAttributes.ATTRIBUTE_BEAT))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_STRING, getAttribute<TGString>(TGDocumentContextAttributes.ATTRIBUTE_STRING))
		processor.setAttribute(TGChangeGraceNoteAction.ATTRIBUTE_EFFECT, effect)
		processor.process()
	}
}
