package app.tuxguitar.android.view.dialog.tempo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.Text
import app.tuxguitar.android.view.compose.TGNumberField
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
import app.tuxguitar.editor.action.composition.TGChangeTempoRangeAction
import app.tuxguitar.song.models.TGMeasureHeader
import app.tuxguitar.song.models.TGSong
import app.tuxguitar.song.models.TGTempoBase

class TGTempoDialog : TGComposeModalFragment(R.string.tempo_dlg_title) {

	private val tempoBases = TGTempoBase.getTempoBases().toList()

	private var tempo by mutableStateOf(120)
	private var baseIndex by mutableStateOf(0)
	private var applyTo by mutableStateOf(TGChangeTempoRangeAction.APPLY_TO_NEXT)

	private val header: TGMeasureHeader
		get() = getAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER)

	override fun initState() {
		val current = header.tempo
		tempo = current.rawValue
		baseIndex = tempoBases.indexOfFirst { it.base == current.base && it.isDotted == current.isDotted }.coerceAtLeast(0)
	}

	@OptIn(ExperimentalLayoutApi::class)
	@Composable
	override fun Content() {
		val context = LocalContext.current
		TGForm {
			TGNumberField(
				label = stringResource(R.string.tempo_dlg_tempo_label),
				value = tempo,
				range = TGChangeTempoRangeAction.MIN_TEMPO..TGChangeTempoRangeAction.MAX_TEMPO,
				onValueChange = { tempo = it },
				onValidityChange = {},
				hint = null,
			)
			// the beat unit of the tempo, shown with its note symbol
			FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				tempoBases.forEachIndexed { index, base ->
					val icon = context.resources.getIdentifier("duration_" + base.base + (if (base.isDotted) "dotted" else ""), "drawable", context.packageName)
					FilterChip(
						selected = index == baseIndex,
						onClick = { baseIndex = index },
						leadingIcon = if (icon != 0) {
							{ Icon(painterResource(icon), null, modifier = Modifier.size(18.dp)) }
						} else null,
						label = { Text("1/" + base.base + (if (base.isDotted) "•" else "")) },
					)
				}
			}
			TGRadioList(
				listOf(
					TGOption(TGChangeTempoRangeAction.APPLY_TO_ALL, stringResource(R.string.tempo_dlg_options_apply_to_song)),
					TGOption(TGChangeTempoRangeAction.APPLY_TO_END, stringResource(R.string.tempo_dlg_options_apply_to_end)),
					TGOption(TGChangeTempoRangeAction.APPLY_TO_NEXT, stringResource(R.string.tempo_dlg_options_apply_to_next_marker)),
				),
				applyTo, { applyTo = it },
			)
		}
	}

	override fun onOk() {
		val base = tempoBases[baseIndex]
		val processor = TGActionProcessor(findContext(), TGChangeTempoRangeAction.NAME)
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_SONG, getAttribute<TGSong>(TGDocumentContextAttributes.ATTRIBUTE_SONG))
		processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_HEADER, header)
		processor.setAttribute(TGChangeTempoRangeAction.ATTRIBUTE_TEMPO, tempo)
		processor.setAttribute(TGChangeTempoRangeAction.ATTRIBUTE_TEMPO_BASE, base.base)
		processor.setAttribute(TGChangeTempoRangeAction.ATTRIBUTE_TEMPO_BASE_DOTTED, base.isDotted)
		processor.setAttribute(TGChangeTempoRangeAction.ATTRIBUTE_APPLY_TO, applyTo)
		processor.processOnNewThread()
	}
}
