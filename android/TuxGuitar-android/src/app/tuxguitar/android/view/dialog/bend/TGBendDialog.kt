package app.tuxguitar.android.view.dialog.bend

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGCurveEditorForm
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.editor.action.effect.TGChangeBendNoteAction
import app.tuxguitar.song.managers.TGSongManager
import app.tuxguitar.song.models.TGBeat
import app.tuxguitar.song.models.TGMeasure
import app.tuxguitar.song.models.TGNote
import app.tuxguitar.song.models.TGString
import app.tuxguitar.song.models.effects.TGEffectBend

class TGBendDialog : TGComposeModalFragment(R.string.bend_dlg_title) {

	private var presets = listOf<TGBendPreset>()
	private var selectedPreset by mutableStateOf<Int?>(null)

	/** The curve being edited, kept as a model: the editor stores its points in pixels and loses them on resize. */
	private var bend: TGEffectBend? = null
	private var editor: TGBendEditor? = null

	override fun initState() {
		presets = createPresets()
		val note = note
		if (note != null && note.effect.isBend) {
			selectedPreset = null
			bend = note.effect.bend.clone(songManager.factory)
		} else {
			selectedPreset = 0
			bend = presets[0].bend
		}
	}

	@Composable
	override fun Content() {
		TGCurveEditorForm(
			presetLabel = stringResource(R.string.bend_dlg_preset_label),
			presets = presets.map { it.name },
			selectedPreset = selectedPreset,
			onSelectPreset = { index ->
				selectedPreset = index
				bend = presets[index].bend
				bend?.let { editor?.loadBend(it) }
			},
			factory = { context ->
				TGBendEditor(context, null).apply {
					setBackgroundColor(Color.WHITE)
					setListener {
						selectedPreset = null
						bend = createBend(songManager.factory)
					}
					// points are placed on the measured grid, so (re)load them once the size is known
					addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
						if (right - left != oldRight - oldLeft) {
							bend?.let { loadBend(it) }
						}
					}
					editor = this
				}
			},
		)
	}

	override fun onOk() {
		// no points left means no bend, like "Clean"
		val editor = editor
		updateEffect(if (editor != null) editor.createBend(songManager.factory) else bend)
	}

	override fun secondaryActionLabel(): Int = R.string.global_button_clean

	override fun onSecondaryAction() = updateEffect(null)

	private fun createPresets(): List<TGBendPreset> {
		val factory = songManager.factory
		val full = TGEffectBend.SEMITONE_LENGTH * 4
		fun preset(nameId: Int, vararg points: Pair<Int, Int>): TGBendPreset {
			val bend = factory.newEffectBend()
			points.forEach { (position, value) -> bend.addPoint(position, value) }
			return TGBendPreset(getString(nameId), bend)
		}
		return listOf(
			preset(R.string.bend_dlg_preset_bend, 0 to 0, 6 to full, 12 to full),
			preset(R.string.bend_dlg_preset_bend_release, 0 to 0, 3 to full, 6 to full, 9 to 0, 12 to 0),
			preset(R.string.bend_dlg_preset_bend_release_bend, 0 to 0, 2 to full, 4 to full, 6 to 0, 8 to 0, 10 to full, 12 to full),
			preset(R.string.bend_dlg_preset_prebend, 0 to full, 12 to full),
			preset(R.string.bend_dlg_preset_prebend_release, 0 to full, 4 to full, 8 to 0, 12 to 0),
		)
	}

	private fun updateEffect(effect: TGEffectBend?) {
		val processor = TGActionProcessor(findContext(), TGChangeBendNoteAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, getAttribute<TGBeat>(TGDocumentContextAttributes.ATTRIBUTE_BEAT))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_STRING, getAttribute<TGString>(TGDocumentContextAttributes.ATTRIBUTE_STRING))
		processor.setAttribute(TGChangeBendNoteAction.ATTRIBUTE_EFFECT, effect)
		processor.process()
	}

	private val note: TGNote?
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_NOTE)

	private val songManager: TGSongManager
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)
}
