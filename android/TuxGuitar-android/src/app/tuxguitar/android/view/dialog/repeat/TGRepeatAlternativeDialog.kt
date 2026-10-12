package app.tuxguitar.android.view.dialog.repeat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGCheckRow
import app.tuxguitar.android.view.compose.TGForm
import app.tuxguitar.android.view.compose.TGNumberField
import app.tuxguitar.android.view.compose.TGOption
import app.tuxguitar.android.view.compose.TGRadioList
import app.tuxguitar.android.view.dialog.fragment.TGComposeModalFragment
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.song.models.TGMeasure
import app.tuxguitar.song.models.TGMeasureHeader
import app.tuxguitar.song.models.TGSong
import app.tuxguitar.song.models.TGTrack
import app.tuxguitar.editor.action.composition.TGRepeatAlternativeAction

/** Endings (1 to 8) of a repeat; the ones already used before this measure are disabled. */
class TGRepeatAlternativeDialog : TGComposeModalFragment(R.string.repeat_alternative_dlg_title) {

	private var existing by mutableStateOf(0)
	private var selected by mutableStateOf(0)

	private val song: TGSong
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG)

	private val header: TGMeasureHeader
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER)

	override fun initState() {
		var endings = 0
		val headers = song.measureHeaders
		while (headers.hasNext()) {
			val h = headers.next()
			if (h.number == header.number) break
			if (h.isRepeatOpen) endings = 0
			endings = endings or h.repeatAlternative
		}
		existing = endings
		selected = if (header.repeatAlternative > 0) header.repeatAlternative else (0 until 8).map { 1 shl it }.firstOrNull { (endings and it) == 0 } ?: 0
	}

	@Composable
	override fun Content() {
		TGForm {
			for (i in 0 until 8) {
				val bit = 1 shl i
				val available = (existing and bit) == 0
				TGCheckRow(
					(i + 1).toString(),
					available && (selected and bit) != 0,
					{ selected = if (it) selected or bit else selected and bit.inv() },
					enabled = available,
				)
			}
		}
	}

	private fun change(value: Int) {
		val processor = TGActionProcessor(findContext(), TGRepeatAlternativeAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, song)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER, header)
		processor.setAttribute(TGRepeatAlternativeAction.ATTRIBUTE_REPEAT_ALTERNATIVE, value)
		processor.processOnNewThread()
	}

	override fun onOk() = change(selected and existing.inv())

	override fun secondaryActionLabel(): Int = R.string.global_button_clean

	override fun onSecondaryAction() = change(0)
}
