package app.tuxguitar.android.view.compose

import android.view.View
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Layout of the bend and tremolo bar dialogs: preset chips on top and the point editor (an Android view) below.
 * The editor is not inside a scroll container, so dragging a point never scrolls the screen.
 * Its height follows its width (about half of it), so the width is limited to fit the space left.
 */
@Composable
fun <V : View> TGCurveEditorForm(
	presetLabel: String,
	presets: List<String>,
	selectedPreset: Int?,
	onSelectPreset: (Int) -> Unit,
	factory: (android.content.Context) -> V,
) {
	Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
		TGFormSection(presetLabel.trimEnd(':', ' '))
		Row(
			horizontalArrangement = Arrangement.spacedBy(8.dp),
			modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 16.dp),
		) {
			presets.forEachIndexed { index, name ->
				FilterChip(
					selected = index == selectedPreset,
					onClick = { onSelectPreset(index) },
					label = { Text(name) },
				)
			}
		}
		BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
			AndroidView(
				factory = factory,
				modifier = Modifier
					.widthIn(max = maxHeight * 2)
					.fillMaxWidth()
					.clip(RoundedCornerShape(12.dp)),
			)
		}
	}
}
