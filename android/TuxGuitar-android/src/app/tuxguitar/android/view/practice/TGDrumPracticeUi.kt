package app.tuxguitar.android.view.practice

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGNumberField
import app.tuxguitar.android.view.drums.TGDrumGrader
import app.tuxguitar.android.view.drums.TGDrumPass
import app.tuxguitar.android.view.drums.TGMidiStatus

/** Short state of the drum grading: the module, or what is missing. */
@Composable
private fun drumState(drums: TGDrumGrader): String {
	val track = drums.trackName
	return when {
		drums.status !is TGMidiStatus.Connected -> stringResource(R.string.practice_drums_no_module)
		track == null -> stringResource(R.string.practice_drums_no_track)
		else -> stringResource(R.string.practice_drums_track, track)
	}
}

/** Counts of a pass: on time, early or late, missed, extra hits. */
@Composable
private fun passCounts(pass: TGDrumPass): String =
	stringResource(R.string.practice_drums_counts, pass.onTime, pass.early + pass.late, pass.missed, pass.extra)

/** Header of the practice tiles while grading the drums: the score of this pass and of the last one. */
@Composable
internal fun TGDrumHeader(drums: TGDrumGrader) {
	val current = drums.current
	val last = drums.last
	Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp).semantics(mergeDescendants = true) {}) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				drumState(drums),
				style = MaterialTheme.typography.titleSmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				passCounts(current) + (last?.percent?.let { " · " + stringResource(R.string.practice_drums_last, it) } ?: ""),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		Text(
			current.percent?.let { "$it%" } ?: "–",
			fontSize = 28.sp,
			fontWeight = FontWeight.Bold,
			color = MaterialTheme.colorScheme.primary,
			modifier = Modifier.padding(start = 12.dp),
		)
	}
}

/** Options of the drum grading: on / off, the latency and its calibration. */
@Composable
internal fun TGDrumOptions(drums: TGDrumGrader) {
	val androidContext = LocalContext.current
	HorizontalDivider()
	Row(verticalAlignment = Alignment.CenterVertically) {
		Column(modifier = Modifier.weight(1f)) {
			Text(stringResource(R.string.practice_drums), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
			Text(
				stringResource(R.string.practice_drums_summary),
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		Switch(
			checked = drums.enabled,
			onCheckedChange = { drums.setEnabled(androidContext, it) },
			modifier = Modifier.padding(start = 12.dp),
		)
	}
	if (!drums.enabled) {
		return
	}
	Text(drumState(drums), style = MaterialTheme.typography.bodyMedium)
	Text(
		stringResource(R.string.practice_drums_mute_hint),
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
	TGNumberField(
		label = stringResource(R.string.practice_drums_latency),
		value = drums.latencyMillis,
		range = 0..TGDrumGrader.MAX_LATENCY_MILLIS,
		onValueChange = { drums.setLatency(it) },
		onValidityChange = {},
		hint = stringResource(R.string.practice_drums_latency_hint),
	)
	val calibrated = stringResource(R.string.practice_drums_calibrated)
	val notEnough = stringResource(R.string.practice_drums_calibrate_more)
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
		OutlinedButton(onClick = {
			val done = drums.calibrate()
			Toast.makeText(androidContext, if (done) calibrated.format(drums.latencyMillis) else notEnough, Toast.LENGTH_LONG).show()
		}) {
			Text(stringResource(R.string.practice_drums_calibrate))
		}
		Text(
			stringResource(R.string.practice_drums_calibrate_hint),
			style = MaterialTheme.typography.bodySmall,
			color = MaterialTheme.colorScheme.onSurfaceVariant,
			modifier = Modifier.weight(1f),
		)
	}
}
