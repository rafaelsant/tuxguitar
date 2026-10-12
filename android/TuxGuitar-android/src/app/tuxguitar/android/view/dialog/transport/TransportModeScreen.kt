package app.tuxguitar.android.view.dialog.transport

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.compose.TGNumberField
import kotlinx.coroutines.delay

object TransportModeCompose {
	/** Shows the play mode screen inside [view], editing [state]. Called from the Java dialog fragment. */
	@JvmStatic
	fun bind(view: ComposeView, state: TransportModeState) {
		view.setContent {
			TGComposeTheme {
				Surface(modifier = Modifier.fillMaxSize()) {
					TransportModeScreen(state)
				}
			}
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportModeScreen(state: TransportModeState) {
	Column(
		modifier = Modifier
			.fillMaxSize()
			.verticalScroll(rememberScrollState())
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(16.dp),
	) {
		val modes = listOf(R.string.transport_mode_dlg_simple, R.string.transport_mode_dlg_trainer)
		SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
			modes.forEachIndexed { index, label ->
				SegmentedButton(
					selected = state.isTrainer == (index == 1),
					onClick = { state.isTrainer = (index == 1) },
					shape = SegmentedButtonDefaults.itemShape(index, modes.size),
				) {
					Text(stringResource(label))
				}
			}
		}

		if (state.isTrainer) {
			TrainerSection(state)
		} else {
			SimpleSection(state)
		}

		if (state.isLoopEnabled) {
			LoopSection(state)
		}
	}
}

@Composable
private fun SimpleSection(state: TransportModeState) {
	Section(title = stringResource(R.string.transport_mode_dlg_simple)) {
		TGNumberField(
			label = stringResource(R.string.transport_mode_dlg_simple_tempo_percent_label),
			value = state.simplePercent,
			range = TransportModeState.MIN_PERCENT..TransportModeState.MAX_PERCENT,
			onValueChange = { state.simplePercent = it },
			onValidityChange = { state.setFieldValid("simplePercent", it) },
			hint = stringResource(R.string.transport_mode_dlg_tempo_bpm_hint, state.simplePercent, state.percentToBpm(state.simplePercent)),
		)
		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Text(stringResource(R.string.transport_mode_dlg_simple_loop), modifier = Modifier.weight(1f))
			Switch(checked = state.isSimpleLoop, onCheckedChange = { state.isSimpleLoop = it })
		}
	}
}

@Composable
private fun TrainerSection(state: TransportModeState) {
	val range = TransportModeState.MIN_PERCENT..TransportModeState.MAX_PERCENT
	Section(title = stringResource(R.string.transport_mode_dlg_trainer)) {
		TGNumberField(
			label = stringResource(R.string.transport_mode_dlg_trainer_tempo_percent_from_label),
			value = state.trainerFrom,
			range = range,
			onValueChange = { state.trainerFrom = it },
			onValidityChange = { state.setFieldValid("trainerFrom", it) },
			hint = stringResource(R.string.transport_mode_dlg_tempo_bpm_hint, state.trainerFrom, state.percentToBpm(state.trainerFrom)),
		)
		TGNumberField(
			label = stringResource(R.string.transport_mode_dlg_trainer_tempo_percent_to_label),
			value = state.trainerTo,
			range = range,
			onValueChange = { state.trainerTo = it },
			onValidityChange = { state.setFieldValid("trainerTo", it) },
			hint = stringResource(R.string.transport_mode_dlg_tempo_bpm_hint, state.trainerTo, state.percentToBpm(state.trainerTo)),
		)
		TGNumberField(
			label = stringResource(R.string.transport_mode_dlg_trainer_tempo_increment_label),
			value = state.trainerIncrement,
			range = range,
			onValueChange = { state.trainerIncrement = it },
			onValidityChange = { state.setFieldValid("trainerIncrement", it) },
			hint = stringResource(R.string.transport_mode_dlg_trainer_tempo_increment_hint, state.trainerIncrement, state.percentToBpm(state.trainerIncrement)),
		)
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			TransportModeState.INCREMENT_PRESETS.forEach { preset ->
				FilterChip(
					selected = state.trainerIncrement == preset,
					onClick = { state.trainerIncrement = preset },
					label = { Text("+$preset%") },
				)
			}
		}
		ErrorText(state.trainerError)
	}
}

@Composable
private fun LoopSection(state: TransportModeState) {
	val range = 1..state.measureCount
	Section(title = stringResource(R.string.transport_mode_dlg_loop_range_label)) {
		TGNumberField(
			label = stringResource(R.string.transport_mode_dlg_loop_range_from_label),
			value = state.loopFrom,
			range = range,
			onValueChange = { state.loopFrom = it },
			onValidityChange = { state.setFieldValid("loopFrom", it) },
			hint = state.markerAt(state.loopFrom),
			trailing = {
				OutlinedButton(onClick = { state.loopFrom = state.caretMeasure }) {
					Text(stringResource(R.string.transport_mode_dlg_loop_range_current_measure))
				}
			},
		)
		val endHint = stringResource(R.string.transport_mode_dlg_loop_range_end_hint)
		TGNumberField(
			label = stringResource(R.string.transport_mode_dlg_loop_range_to_label),
			value = state.loopTo,
			range = range,
			onValueChange = { state.loopTo = it },
			onValidityChange = { state.setFieldValid("loopTo", it) },
			hint = listOfNotNull(
				state.markerAt(state.loopTo),
				endHint.takeIf { state.loopTo == state.measureCount },
			).joinToString(" · ").ifEmpty { null },
			trailing = {
				OutlinedButton(onClick = { state.loopTo = state.caretMeasure }) {
					Text(stringResource(R.string.transport_mode_dlg_loop_range_current_measure))
				}
			},
		)
		ErrorText(state.loopError)
	}
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
	Card(modifier = Modifier.fillMaxWidth()) {
		Column(
			modifier = Modifier.padding(16.dp),
			verticalArrangement = Arrangement.spacedBy(12.dp),
		) {
			Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
			content()
		}
	}
}

@Composable
private fun ErrorText(error: Int?) {
	if (error != null) {
		Text(stringResource(error), color = MaterialTheme.colorScheme.error)
	}
}
