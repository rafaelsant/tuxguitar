package app.tuxguitar.android.view.practice

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.compose.TGNumberField
import app.tuxguitar.util.TGContext
import kotlinx.coroutines.delay

/** Bottom bars of the main screen: the play bar (play mode) and the practice bar (practice mode). */
object TGBottomBars {
	@JvmStatic
	fun bind(view: ComposeView, context: TGContext) {
		val player = TGPracticePlayer(context)
		val modeController = TGAppModeController.getInstance(context)
		view.setContent {
			TGComposeTheme {
				TGBottomBarsContent(player, modeController)
			}
		}
	}
}

@Composable
private fun TGBottomBarsContent(player: TGPracticePlayer, modeController: TGAppModeController) {
	val mode = modeController.mode
	var settings by remember { mutableStateOf(player.loadSettings()) }
	var playLoop by rememberSaveable { mutableStateOf(false) }
	var snapshot by remember { mutableStateOf<TGPlayerSnapshot?>(null) }
	val latestSettings by rememberUpdatedState(settings)

	// apply the player mode of the selected screen mode; playback stops when switching
	LaunchedEffect(mode) {
		snapshot?.let { if (it.running) player.stop() }
		if (mode == TGAppMode.PRACTICE) player.applyPractice(settings) else player.applyPlay(playLoop)
	}

	// the player has no listener for these values, so poll them while the bars are shown
	LaunchedEffect(mode) {
		while (mode != TGAppMode.EDIT) {
			// no snapshot (and no bar) while there is no song or caret yet
			snapshot = runCatching { player.snapshot(if (mode == TGAppMode.PRACTICE) latestSettings else null) }.getOrNull()
			delay(200)
		}
	}

	val current = snapshot ?: return
	when (mode) {
		TGAppMode.PLAY -> PlayBar(
			snapshot = current,
			loop = playLoop,
			onPlayPause = { player.playPause(null) },
			onStop = { player.stop() },
			onLoopChange = {
				playLoop = it
				player.applyPlay(it)
			},
		)
		TGAppMode.PRACTICE -> PracticeBar(
			snapshot = current,
			settings = settings,
			onSettingsChange = {
				settings = it
				player.saveSettings(it)
				player.applyPractice(it)
			},
			onPlayPause = { player.playPause(settings) },
			onMetronome = { player.toggleMetronome() },
			onCountIn = { player.toggleCountIn() },
		)
		TGAppMode.EDIT -> Unit
	}
}

// ---------------------------------------------------------------- play bar

@Composable
private fun PlayBar(
	snapshot: TGPlayerSnapshot,
	loop: Boolean,
	onPlayPause: () -> Unit,
	onStop: () -> Unit,
	onLoopChange: (Boolean) -> Unit,
) {
	Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			PlayPauseButton(snapshot.running, onPlayPause, size = 52)
			IconButton(onClick = onStop) {
				Icon(painterResource(R.drawable.ic_bar_stop_24dp), stringResource(R.string.practice_stop))
			}
			Text(
				stringResource(R.string.practice_bpm, TGTempoMath.bpm(snapshot.baseTempo, if (snapshot.running) snapshot.currentPercent else 100)),
				style = MaterialTheme.typography.titleLarge,
				fontWeight = FontWeight.Bold,
				modifier = Modifier.weight(1f),
			)
			ToggleIcon(R.drawable.ic_bar_loop_24dp, R.string.practice_loop, loop, onChange = onLoopChange)
		}
	}
}

// ---------------------------------------------------------------- practice bar

@Composable
private fun PracticeBar(
	snapshot: TGPlayerSnapshot,
	settings: TGPracticeSettings,
	onSettingsChange: (TGPracticeSettings) -> Unit,
	onPlayPause: () -> Unit,
	onMetronome: () -> Unit,
	onCountIn: () -> Unit,
) {
	var expanded by rememberSaveable { mutableStateOf(false) }
	val base = snapshot.baseTempo

	Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
		Column {
			AnimatedVisibility(visible = expanded) {
				Column {
					PracticeOptions(snapshot, settings, onSettingsChange, onCountIn)
					HorizontalDivider()
				}
			}
			Row(
				modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
				verticalAlignment = Alignment.CenterVertically,
				horizontalArrangement = Arrangement.spacedBy(4.dp),
			) {
				PlayPauseButton(snapshot.running, onPlayPause, size = 56)
				Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
					val percent = when {
						snapshot.running -> snapshot.currentPercent
						settings.training -> settings.fromPercent
						else -> settings.fixedPercent
					}
					Text(
						stringResource(R.string.practice_bpm, TGTempoMath.bpm(base, percent)),
						style = MaterialTheme.typography.headlineSmall,
						fontWeight = FontWeight.Bold,
					)
					if (settings.training) {
						val step = trainingStep(settings, if (snapshot.running) snapshot.currentPercent else settings.fromPercent)
						Text(
							stringResource(
								R.string.practice_training_progress,
								TGTempoMath.bpm(base, settings.fromPercent), TGTempoMath.bpm(base, settings.toPercent), step.first, step.second,
							),
							style = MaterialTheme.typography.bodySmall,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
						LinearProgressIndicator(
							progress = { step.first / step.second.toFloat() },
							modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
						)
					} else {
						Text(
							stringResource(R.string.practice_fixed_summary, settings.fixedPercent),
							style = MaterialTheme.typography.bodySmall,
							maxLines = 1,
							overflow = TextOverflow.Ellipsis,
						)
					}
				}
				ToggleIcon(R.drawable.ic_bar_loop_24dp, R.string.practice_loop, settings.isLooping, enabled = !settings.training) {
					onSettingsChange(settings.copy(loop = it))
				}
				ToggleIcon(R.drawable.ic_bar_metronome_24dp, R.string.practice_metronome, snapshot.metronome) { onMetronome() }
				IconButton(onClick = { expanded = !expanded }) {
					Icon(
						painterResource(if (expanded) R.drawable.ic_bar_expand_more_24dp else R.drawable.ic_bar_expand_less_24dp),
						stringResource(if (expanded) R.string.practice_collapse else R.string.practice_expand),
					)
				}
			}
		}
	}
}

/** Current loop pass and number of passes of the training ramp, from the current tempo percent. */
private fun trainingStep(settings: TGPracticeSettings, currentPercent: Int): Pair<Int, Int> {
	val range = (settings.toPercent - settings.fromPercent).coerceAtLeast(0)
	val increment = settings.incrementPercent.coerceAtLeast(1)
	val total = (range + increment - 1) / increment + 1
	val step = ((currentPercent - settings.fromPercent) / increment + 1).coerceIn(1, total)
	return step to total
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PracticeOptions(
	snapshot: TGPlayerSnapshot,
	settings: TGPracticeSettings,
	onSettingsChange: (TGPracticeSettings) -> Unit,
	onCountIn: () -> Unit,
) {
	val base = snapshot.baseTempo
	val minBpm = TGTempoMath.bpm(base, TGPracticePlayer.MIN_PERCENT).coerceAtLeast(1)
	val maxBpm = TGTempoMath.bpm(base, TGPracticePlayer.MAX_PERCENT)

	Column(
		modifier = Modifier
			.heightIn(max = 420.dp)
			.verticalScroll(rememberScrollState())
			.padding(16.dp),
		verticalArrangement = Arrangement.spacedBy(12.dp),
	) {
		// ---- speed
		Text(stringResource(R.string.practice_speed), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
		val modes = listOf(R.string.practice_fixed, R.string.practice_training)
		SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
			modes.forEachIndexed { index, label ->
				SegmentedButton(
					selected = settings.training == (index == 1),
					onClick = { onSettingsChange(settings.copy(training = index == 1)) },
					shape = SegmentedButtonDefaults.itemShape(index, modes.size),
				) {
					Text(stringResource(label))
				}
			}
		}
		if (settings.training) {
			TGNumberField(
				label = stringResource(R.string.practice_from),
				value = TGTempoMath.bpm(base, settings.fromPercent),
				range = minBpm..maxBpm,
				onValueChange = { onSettingsChange(settings.copy(fromPercent = TGTempoMath.stepPercent(base, settings.fromPercent, it))) },
				onValidityChange = {},
				hint = null,
			)
			TGNumberField(
				label = stringResource(R.string.practice_to),
				value = TGTempoMath.bpm(base, settings.toPercent),
				range = minBpm..maxBpm,
				onValueChange = { onSettingsChange(settings.copy(toPercent = TGTempoMath.stepPercent(base, settings.toPercent, it))) },
				onValidityChange = {},
				hint = null,
			)
			Text(stringResource(R.string.practice_increment), style = MaterialTheme.typography.bodyMedium)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				listOf(1, 2, 5, 10).forEach { bpm ->
					val percent = TGTempoMath.percent(base, bpm).coerceAtLeast(1)
					FilterChip(
						selected = settings.incrementPercent == percent,
						onClick = { onSettingsChange(settings.copy(incrementPercent = percent)) },
						label = { Text("+$bpm") },
					)
				}
			}
			val error = when {
				settings.fromPercent >= settings.toPercent -> R.string.transport_mode_dlg_trainer_error_range
				settings.incrementPercent > settings.toPercent - settings.fromPercent -> R.string.transport_mode_dlg_trainer_error_increment
				else -> null
			}
			if (error != null) {
				Text(stringResource(error), color = MaterialTheme.colorScheme.error)
			}
		} else {
			TGNumberField(
				label = stringResource(R.string.practice_bpm_label),
				value = TGTempoMath.bpm(base, settings.fixedPercent),
				range = minBpm..maxBpm,
				onValueChange = { onSettingsChange(settings.copy(fixedPercent = TGTempoMath.stepPercent(base, settings.fixedPercent, it))) },
				onValidityChange = {},
				hint = stringResource(R.string.practice_fixed_summary, settings.fixedPercent),
			)
		}

		// ---- section
		if (settings.isLooping) {
			HorizontalDivider()
			Text(stringResource(R.string.practice_section), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
			val count = snapshot.measureCount
			val start = if (settings.loopStart > 0) settings.loopStart.coerceAtMost(count) else 1
			val end = if (settings.loopEnd > 0) settings.loopEnd.coerceAtMost(count) else count
			fun withRange(newStart: Int, newEnd: Int) = settings.copy(
				loopStart = if (newStart <= 1) -1 else newStart,
				loopEnd = if (newEnd >= count) -1 else newEnd,
			)
			TGNumberField(
				label = stringResource(R.string.practice_start),
				value = start,
				range = 1..count,
				onValueChange = { onSettingsChange(withRange(it, maxOf(it, end))) },
				onValidityChange = {},
				hint = null,
			)
			TGNumberField(
				label = stringResource(R.string.practice_end),
				value = end,
				range = 1..count,
				onValueChange = { onSettingsChange(withRange(minOf(start, it), it)) },
				onValidityChange = {},
				hint = null,
			)
			Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
				OutlinedButton(onClick = { onSettingsChange(withRange(snapshot.caretMeasure, maxOf(snapshot.caretMeasure, end))) }) {
					Text(stringResource(R.string.practice_start_here))
				}
				OutlinedButton(onClick = { onSettingsChange(withRange(minOf(start, snapshot.caretMeasure), snapshot.caretMeasure)) }) {
					Text(stringResource(R.string.practice_end_here))
				}
			}
			OutlinedButton(onClick = { onSettingsChange(withRange(1, count)) }) {
				Text(stringResource(R.string.practice_whole_song))
			}
		}

		// ---- count-in
		HorizontalDivider()
		Row(verticalAlignment = Alignment.CenterVertically) {
			Text(stringResource(R.string.practice_count_in), modifier = Modifier.weight(1f))
			Switch(checked = snapshot.countIn, onCheckedChange = { onCountIn() })
		}
	}
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun PlayPauseButton(running: Boolean, onClick: () -> Unit, size: Int) {
	FilledIconButton(onClick = onClick, modifier = Modifier.size(size.dp)) {
		Icon(
			painterResource(if (running) R.drawable.ic_bar_pause_24dp else R.drawable.ic_bar_play_24dp),
			stringResource(if (running) R.string.practice_pause else R.string.practice_play),
			modifier = Modifier.size((size / 2).dp),
		)
	}
}

/** Icon that is filled with the accent color while on, so its state is visible at a glance. */
@Composable
private fun ToggleIcon(icon: Int, description: Int, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
	IconToggleButton(
		checked = checked,
		onCheckedChange = onChange,
		enabled = enabled,
		colors = IconButtonDefaults.iconToggleButtonColors(
			checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
			checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
		),
	) {
		Icon(painterResource(icon), stringResource(description))
	}
}
