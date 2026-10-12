package app.tuxguitar.android.view.dialog.tremoloBar

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
import app.tuxguitar.editor.action.effect.TGChangeTremoloBarAction
import app.tuxguitar.song.managers.TGSongManager
import app.tuxguitar.song.models.TGBeat
import app.tuxguitar.song.models.TGMeasure
import app.tuxguitar.song.models.TGNote
import app.tuxguitar.song.models.TGString
import app.tuxguitar.song.models.effects.TGEffectTremoloBar

class TGTremoloBarDialog : TGComposeModalFragment(R.string.tremolo_bar_dlg_title) {

	private var presets = listOf<TGTremoloBarPreset>()
	private var selectedPreset by mutableStateOf<Int?>(null)

	/** The curve being edited, kept as a model: the editor stores its points in pixels and loses them on resize. */
	private var tremoloBar: TGEffectTremoloBar? = null
	private var editor: TGTremoloBarEditor? = null

	override fun initState() {
		presets = createPresets()
		val note = note
		if (note != null && note.effect.isTremoloBar) {
			selectedPreset = null
			tremoloBar = note.effect.tremoloBar.clone(songManager.factory)
		} else {
			selectedPreset = 0
			tremoloBar = presets[0].tremoloBar
		}
	}

	@Composable
	override fun Content() {
		TGCurveEditorForm(
			presetLabel = stringResource(R.string.tremolo_bar_dlg_preset_label),
			presets = presets.map { it.name },
			selectedPreset = selectedPreset,
			onSelectPreset = { index ->
				selectedPreset = index
				tremoloBar = presets[index].tremoloBar
				tremoloBar?.let { editor?.loadTremoloBar(it) }
			},
			factory = { context ->
				TGTremoloBarEditor(context, null).apply {
					setBackgroundColor(Color.WHITE)
					setListener {
						selectedPreset = null
						tremoloBar = createTremoloBar(songManager.factory)
					}
					// points are placed on the measured grid, so (re)load them once the size is known
					addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
						if (right - left != oldRight - oldLeft) {
							tremoloBar?.let { loadTremoloBar(it) }
						}
					}
					editor = this
				}
			},
		)
	}

	override fun onOk() {
		// no points left means no tremolo bar, like "Clean"
		val editor = editor
		updateEffect(if (editor != null) editor.createTremoloBar(songManager.factory) else tremoloBar)
	}

	override fun secondaryActionLabel(): Int = R.string.global_button_clean

	override fun onSecondaryAction() = updateEffect(null)

	private fun createPresets(): List<TGTremoloBarPreset> {
		val factory = songManager.factory
		fun preset(nameId: Int, vararg points: Pair<Int, Int>): TGTremoloBarPreset {
			val tremoloBar = factory.newEffectTremoloBar()
			points.forEach { (position, value) -> tremoloBar.addPoint(position, value) }
			return TGTremoloBarPreset(getString(nameId), tremoloBar)
		}
		return listOf(
			preset(R.string.tremolo_bar_dlg_preset_dip, 0 to 0, 6 to -2, 12 to 0),
			preset(R.string.tremolo_bar_dlg_preset_dive, 0 to 0, 9 to -2, 12 to -2),
			preset(R.string.tremolo_bar_dlg_preset_release_up, 0 to -2, 9 to -2, 12 to 0),
			preset(R.string.tremolo_bar_dlg_preset_inverted_dip, 0 to 0, 6 to 2, 12 to 0),
			preset(R.string.tremolo_bar_dlg_preset_return, 0 to 0, 9 to 2, 12 to 2),
			preset(R.string.tremolo_bar_dlg_preset_release_down, 0 to 2, 9 to 2, 12 to 0),
		)
	}

	private fun updateEffect(effect: TGEffectTremoloBar?) {
		val processor = TGActionProcessor(findContext(), TGChangeTremoloBarAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_MEASURE, getAttribute<TGMeasure>(TGDocumentContextAttributes.ATTRIBUTE_MEASURE))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_BEAT, getAttribute<TGBeat>(TGDocumentContextAttributes.ATTRIBUTE_BEAT))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_STRING, getAttribute<TGString>(TGDocumentContextAttributes.ATTRIBUTE_STRING))
		processor.setAttribute(TGChangeTremoloBarAction.ATTRIBUTE_EFFECT, effect)
		processor.process()
	}

	private val note: TGNote?
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_NOTE)

	private val songManager: TGSongManager
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG_MANAGER)
}
