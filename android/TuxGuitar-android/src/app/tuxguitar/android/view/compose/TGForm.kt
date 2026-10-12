package app.tuxguitar.android.view.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Scrollable form body of a dialog, with the standard padding and spacing. */
@Composable
fun TGForm(content: @Composable ColumnScope.() -> Unit) {
	Column(
		modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp),
		content = content,
	)
}

/** Section title inside a form. */
@Composable
fun TGFormSection(title: String) {
	Text(
		title,
		style = MaterialTheme.typography.titleSmall,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.padding(top = 8.dp),
	)
}

/** An option for [TGRadioList] and [TGDropdownField]. */
class TGOption<T>(val value: T, val label: String)

/** Single choice shown as radio rows with large touch targets; for a few options. */
@Composable
fun <T> TGRadioList(options: List<TGOption<T>>, selected: T, onSelect: (T) -> Unit, enabled: Boolean = true) {
	Column {
		options.forEach { option ->
			Row(
				verticalAlignment = Alignment.CenterVertically,
				modifier = Modifier
					.fillMaxWidth()
					.heightIn(min = 48.dp)
					.selectable(selected = option.value == selected, enabled = enabled, role = Role.RadioButton) { onSelect(option.value) },
			) {
				RadioButton(selected = option.value == selected, onClick = null, enabled = enabled)
				Text(option.label, modifier = Modifier.padding(start = 12.dp))
			}
		}
	}
}

/** Single choice in a dropdown; for longer lists of options. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> TGDropdownField(label: String, options: List<TGOption<T>>, selected: T, onSelect: (T) -> Unit, enabled: Boolean = true) {
	var expanded by remember { mutableStateOf(false) }
	ExposedDropdownMenuBox(expanded = expanded && enabled, onExpandedChange = { if (enabled) expanded = it }) {
		OutlinedTextField(
			value = options.firstOrNull { it.value == selected }?.label ?: "",
			onValueChange = {},
			readOnly = true,
			enabled = enabled,
			label = { Text(label) },
			trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
			modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
		)
		ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
			options.forEach { option ->
				DropdownMenuItem(
					text = { Text(option.label) },
					onClick = {
						onSelect(option.value)
						expanded = false
					},
				)
			}
		}
	}
}

/** Checkbox with its label; the whole row toggles it. */
@Composable
fun TGCheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 48.dp)
			.toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = onChange),
	) {
		Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
		Text(label, modifier = Modifier.padding(start = 12.dp))
	}
}

/** Switch with its label; the whole row toggles it. */
@Composable
fun TGSwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 48.dp)
			.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
	) {
		Text(label, modifier = Modifier.weight(1f))
		Switch(checked = checked, onCheckedChange = null, enabled = enabled)
	}
}

/** Text input with a label. */
@Composable
fun TGTextField(label: String, value: String, onChange: (String) -> Unit, singleLine: Boolean = true) {
	OutlinedTextField(
		value = value,
		onValueChange = onChange,
		label = { Text(label) },
		singleLine = singleLine,
		modifier = Modifier.fillMaxWidth(),
	)
}

/** A row that runs an action when tapped (e.g. open a sub dialog). */
@Composable
fun TGActionRow(label: String, onClick: () -> Unit) {
	Text(
		label,
		color = MaterialTheme.colorScheme.primary,
		modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onClick).padding(vertical = 14.dp),
	)
}
