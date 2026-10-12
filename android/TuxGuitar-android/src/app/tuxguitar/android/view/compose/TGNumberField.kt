package app.tuxguitar.android.view.compose

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuxguitar.android.R
import kotlinx.coroutines.delay

/**
 * A number with large "-" and "+" buttons; holding a button repeats the step.
 * The text can also be typed: out of range text is reported through [onValueChange] only once it is valid.
 */
@Composable
fun TGNumberField(
	label: String,
	value: Int,
	range: IntRange,
	onValueChange: (Int) -> Unit,
	onValidityChange: (Boolean) -> Unit,
	hint: String?,
	trailing: (@Composable () -> Unit)? = null,
) {
	var text by remember { mutableStateOf(value.toString()) }
	var valid by remember { mutableStateOf(true) }
	val focusManager = LocalFocusManager.current

	// follow changes made by the buttons or other fields
	LaunchedEffect(value) {
		if (text.toIntOrNull() != value) {
			text = value.toString()
			if (!valid) {
				valid = true
				onValidityChange(true)
			}
		}
	}

	fun step(delta: Int): Boolean {
		val next = (value + delta).coerceIn(range)
		if (next == value) return false
		onValueChange(next)
		return true
	}

	Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
		Text(label, style = MaterialTheme.typography.bodyMedium)
		Row(verticalAlignment = Alignment.CenterVertically) {
			TGRepeatingButton(
				text = "−",
				description = stringResource(R.string.number_stepper_decrease_description),
				enabled = value > range.first,
				onStep = { step(-1) },
			)
			OutlinedTextField(
				value = text,
				onValueChange = { newText ->
					val digits = newText.filter { it.isDigit() }.take(4)
					text = digits
					val parsed = digits.toIntOrNull()
					val nowValid = parsed != null && parsed in range
					if (nowValid) onValueChange(parsed!!)
					if (nowValid != valid) {
						valid = nowValid
						onValidityChange(nowValid)
					}
				},
				isError = !valid,
				singleLine = true,
				textStyle = MaterialTheme.typography.titleLarge.copy(textAlign = TextAlign.Center),
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
				keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
				modifier = Modifier
					.width(96.dp)
					.padding(horizontal = 8.dp)
					.onFocusChanged { focus ->
						// leaving the field restores the last valid value
						if (!focus.isFocused && !valid) {
							text = value.toString()
							valid = true
							onValidityChange(true)
						}
					},
			)
			TGRepeatingButton(
				text = "+",
				description = stringResource(R.string.number_stepper_increase_description),
				enabled = value < range.last,
				onStep = { step(1) },
			)
		}
		val message = if (valid) hint else stringResource(R.string.number_stepper_error_range, range.first, range.last)
		if (message != null) {
			Text(
				message,
				style = MaterialTheme.typography.bodySmall,
				color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
			)
		}
		if (trailing != null) {
			trailing()
		}
	}
}

/** Steps once on tap, and keeps stepping while held. [onStep] returns false when the limit is reached. */
@Composable
fun TGRepeatingButton(text: String, description: String, enabled: Boolean, onStep: () -> Boolean) {
	val interactionSource = remember { MutableInteractionSource() }
	val pressed by interactionSource.collectIsPressedAsState()
	val currentOnStep by rememberUpdatedState(onStep)
	var steppedByPress by remember { mutableStateOf(false) }

	LaunchedEffect(pressed) {
		if (pressed) {
			steppedByPress = true
			if (!currentOnStep()) return@LaunchedEffect
			delay(400)
			while (currentOnStep()) {
				delay(60)
			}
		}
	}

	FilledTonalIconButton(
		onClick = {
			// accessibility services click without pressing
			if (!steppedByPress) currentOnStep()
			steppedByPress = false
		},
		enabled = enabled,
		interactionSource = interactionSource,
		modifier = Modifier
			.size(56.dp)
			.semantics { contentDescription = description },
	) {
		Text(text, fontSize = 26.sp)
	}
}
