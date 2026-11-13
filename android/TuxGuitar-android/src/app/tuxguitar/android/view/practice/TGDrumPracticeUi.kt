package app.tuxguitar.android.view.practice

import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
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

/** Counts of a pass, with the symbols in the colors of the notes in the score: a legend of them. */
@Composable
private fun passCounts(pass: TGDrumPass): AnnotatedString {
	val (green, amber, red) = TGDrumGrader.gradeColors(isSystemInDarkTheme())
	val extra = stringResource(R.string.practice_drums_extra, pass.extra)
	return buildAnnotatedString {
		fun count(symbol: String, color: Int, value: Int) {
			withStyle(SpanStyle(color = Color(0xFF000000.toInt() or color), fontWeight = FontWeight.Bold)) { append(symbol) }
			append(" $value · ")
		}
		count("✓", green, pass.onTime)
		count("≈", amber, pass.early + pass.late)
		count("✗", red, pass.missed)
		append(extra)
	}
}

/** Header of the practice tiles while grading the drums: the score of this pass and of the last one. */
@Composable
internal fun TGDrumHeader(drums: TGDrumGrader) {
	val current = drums.current
	val last = drums.last?.percent
	val percent = current.percent
	// read as a sentence: the symbols and the dash mean nothing to a screen reader
	val description = stringResource(
		R.string.practice_drums_description,
		percent?.let { "$it%" } ?: stringResource(R.string.practice_drums_no_score),
		current.onTime, current.early + current.late, current.missed, current.extra,
	) + (last?.let { " " + stringResource(R.string.practice_drums_description_last, it) } ?: "")
	Row(
		verticalAlignment = Alignment.CenterVertically,
		modifier = Modifier.padding(bottom = 10.dp).clearAndSetSemantics { contentDescription = description },
	) {
		Column(modifier = Modifier.weight(1f)) {
			Text(
				drumState(drums),
				style = MaterialTheme.typography.titleSmall,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			Text(
				passCounts(current),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
		}
		Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 12.dp)) {
			Text(
				percent?.let { "$it%" } ?: "–",
				fontSize = 28.sp,
				lineHeight = 30.sp,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
			)
			if (last != null) {
				Text(
					stringResource(R.string.practice_drums_last, last),
					style = MaterialTheme.typography.bodySmall,
					color = MaterialTheme.colorScheme.onSurfaceVariant,
					maxLines = 1,
				)
			}
		}
	}
}

/** Options of the drum grading: on / off, the latency and its calibration. */
@Composable
internal fun TGDrumOptions(drums: TGDrumGrader) {
	val androidContext = LocalContext.current
	// shown below the button, readable from the drum stool (a toast goes away too soon)
	var calibration by remember { mutableStateOf<Int?>(null) }
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
		onValueChange = {
			drums.setLatency(it)
			calibration = null
		},
		onValidityChange = {},
		hint = stringResource(R.string.practice_drums_latency_hint),
	)
	Text(
		stringResource(R.string.practice_drums_calibrate_hint),
		style = MaterialTheme.typography.bodyMedium,
		color = MaterialTheme.colorScheme.onSurfaceVariant,
	)
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		OutlinedButton(
			onClick = { if (drums.calibrate()) calibration = drums.latencyMillis },
			enabled = drums.calibrationHits >= TGDrumGrader.MIN_CALIBRATION_HITS,
		) {
			Text(stringResource(R.string.practice_drums_calibrate, drums.calibrationHits))
		}
		calibration?.let {
			Text(
				stringResource(R.string.practice_drums_calibrated, it),
				style = MaterialTheme.typography.bodyLarge,
				fontWeight = FontWeight.Bold,
				color = MaterialTheme.colorScheme.primary,
				modifier = Modifier.weight(1f),
			)
		}
	}
}
