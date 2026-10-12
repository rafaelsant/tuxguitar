package app.tuxguitar.android.view.practice

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.sp
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tuxguitar.android.R
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.main.TGScoreScrollState
import app.tuxguitar.android.view.compose.TGNumberField
import app.tuxguitar.util.TGContext
import kotlinx.coroutines.delay

/** Bottom bars of the main screen: the play bar (play mode) and the practice bar (practice mode). */
object TGBottomBars {
	@JvmStatic
	fun bind(view: ComposeView, context: TGContext) {
		val session = TGPracticeSession.getInstance(context)
		val modeController = TGAppModeController.getInstance(context)
		view.setContent {
			TGComposeTheme {
				TGBottomBarsContent(session, modeController)
			}
		}
	}
}

@Composable
private fun TGBottomBarsContent(session: TGPracticeSession, modeController: TGAppModeController) {
	val mode = modeController.mode
	val player = session.player
	// shared with the tablature, where long press and drag over measures sets the loop
	val settings = session.settings
	var playLoop by rememberSaveable { mutableStateOf(false) }
	var snapshot by remember { mutableStateOf<TGPlayerSnapshot?>(null) }
	val latestSettings by rememberUpdatedState(settings)

	// apply the player mode of the selected screen mode; playback stops when switching
	val androidContext = LocalContext.current
	val stoppedMessage = stringResource(R.string.practice_stopped_on_mode_change)
	LaunchedEffect(mode) {
		snapshot?.let {
			if (it.running) {
				player.stop()
				Toast.makeText(androidContext, stoppedMessage, Toast.LENGTH_SHORT).show()
			}
		}
		if (mode == TGAppMode.PRACTICE) session.apply() else player.applyPlay(playLoop)
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
			onSettingsChange = { session.settings = it },
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
	val sheet = rememberBarSheetState()
	val bpm = TGTempoMath.bpm(snapshot.baseTempo, if (snapshot.running) snapshot.currentPercent else 100)
	BarSheet(sheet) {
		Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING), verticalAlignment = Alignment.CenterVertically) {
			// plain text, not a tile: it is not a control
			Column(modifier = Modifier.width(TILE_SIZE), horizontalAlignment = Alignment.CenterHorizontally) {
				Text(
					bpm.toString(),
					fontSize = 24.sp,
					fontWeight = FontWeight.Bold,
				)
				Text(stringResource(R.string.practice_bpm_unit), fontSize = LABEL_SIZE)
			}
			IconTile(R.drawable.ic_bar_stop_24dp, R.string.practice_stop, checked = false, onClick = onStop)
			IconTile(R.drawable.ic_bar_loop_24dp, R.string.practice_loop, checked = loop, onClick = { onLoopChange(!loop) })
			PlayTile(snapshot.running, onPlayPause, Modifier.weight(1f))
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
	val sheet = rememberBarSheetState()
	val expanded = sheet.level == SheetLevel.OPTIONS
	val base = snapshot.baseTempo
	// the floating menu bar steps aside while the options are open
	DisposableEffect(expanded) {
		TGScoreScrollState.playerExpanded = expanded
		onDispose { TGScoreScrollState.playerExpanded = false }
	}

	BarSheet(sheet, options = { PracticeOptions(snapshot, settings, onSettingsChange) }) {
		if (settings.training) {
			val step = trainingStep(settings, if (snapshot.running) snapshot.currentPercent else settings.fromPercent)
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					stringResource(R.string.practice_training_range, TGTempoMath.bpm(base, settings.fromPercent), TGTempoMath.bpm(base, settings.toPercent)),
					style = MaterialTheme.typography.titleMedium,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				Text(
					stringResource(R.string.practice_training_step, step.first, step.second),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold,
					color = MaterialTheme.colorScheme.primary,
				)
			}
			LinearProgressIndicator(
				progress = { step.first / step.second.toFloat() },
				modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp),
			)
		}

		// row 1: speed and play
		Row(horizontalArrangement = Arrangement.spacedBy(TILE_SPACING), verticalAlignment = Alignment.CenterVertically) {
			val sounding = when {
				snapshot.running -> snapshot.currentPercent
				settings.training -> settings.fromPercent
				else -> settings.fixedPercent
			}
			// while playing, a new fixed speed only applies on the next loop pass: show it as pending
			val pending = snapshot.running && !settings.training && settings.fixedPercent != snapshot.currentPercent
			val fixedBpm = TGTempoMath.bpm(base, settings.fixedPercent)
			fun stepFixed(delta: Int) = onSettingsChange(settings.copy(fixedPercent = TGTempoMath.stepPercent(base, settings.fixedPercent, fixedBpm + delta)))
			if (!settings.training) {
				TextTile("−5", stringResource(R.string.practice_slower), showLabel = false) { stepFixed(-5) }
			}
			// the speed tile opens the options, for 1 BPM steps and training
			ValueTile(
				value = TGTempoMath.bpm(base, sounding).toString(),
				label = if (pending) stringResource(R.string.practice_next_speed, fixedBpm) else stringResource(R.string.practice_bpm_unit),
				checked = expanded || pending,
				onClick = { sheet.moveTo(if (expanded) SheetLevel.TILES else SheetLevel.OPTIONS) },
			)
			if (!settings.training) {
				TextTile("+5", stringResource(R.string.practice_faster), showLabel = false) { stepFixed(5) }
			}
			PlayTile(snapshot.running, onPlayPause, Modifier.weight(1f))
		}

		// row 2: practice aids
		Row(
			horizontalArrangement = Arrangement.spacedBy(TILE_SPACING),
			modifier = Modifier.padding(top = TILE_SPACING),
		) {
			val wide = Modifier.weight(1f).height(TILE_SIZE)
			IconTile(R.drawable.ic_bar_metronome_24dp, R.string.practice_metronome, checked = snapshot.metronome, modifier = wide, onClick = onMetronome)
			TextTile("3", stringResource(R.string.practice_count_in), checked = snapshot.countIn, modifier = wide, onClick = onCountIn)
			// training always loops, so its loop tile would only be a disabled one
			if (!settings.training) {
				IconTile(R.drawable.ic_bar_loop_24dp, R.string.practice_loop, checked = settings.loop, modifier = wide) {
					onSettingsChange(settings.copy(loop = !settings.loop))
				}
			}
			if (settings.isLooping) {
				val start = if (settings.loopStart > 0) settings.loopStart else 1
				val end = if (settings.loopEnd > 0) settings.loopEnd else snapshot.measureCount
				TextTile(
					text = if (start <= 1 && end >= snapshot.measureCount) stringResource(R.string.practice_whole_song_short) else "$start–$end",
					label = stringResource(R.string.practice_section),
					modifier = Modifier.weight(1.4f).height(TILE_SIZE),
					onClick = { sheet.moveTo(SheetLevel.OPTIONS) },
				)
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
) {
	val base = snapshot.baseTempo
	val minBpm = TGTempoMath.bpm(base, TGPracticePlayer.MIN_PERCENT).coerceAtLeast(1)
	val maxBpm = TGTempoMath.bpm(base, TGPracticePlayer.MAX_PERCENT)

	Column(
		modifier = Modifier
			.heightIn(max = 420.dp)
			.verticalScroll(rememberScrollState())
			.padding(top = 4.dp, bottom = 12.dp),
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
				val presets = listOf(1, 2, 5, 10)
				// the base tempo changes with the loop start, so select the closest preset rather than an exact match
				val incrementBpm = TGTempoMath.bpm(base, settings.incrementPercent)
				val closest = presets.minByOrNull { kotlin.math.abs(it - incrementBpm) }
				presets.forEach { bpm ->
					val percent = TGTempoMath.percent(base, bpm).coerceAtLeast(1)
					FilterChip(
						selected = bpm == closest,
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
			// quickest: place the cursor on a measure of the tab and use it as start or end
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
		}
	}
}

// ---------------------------------------------------------------- shared pieces

private val TILE_SIZE = 60.dp
private val TILE_SPACING = 10.dp
private val TILE_SHAPE = RoundedCornerShape(16.dp)
private val LABEL_SIZE = 12.sp

/** Sheet sizes: only the handle (hidden, the most room for the score), the tiles, and the tiles with the options. */
private enum class SheetLevel { HIDDEN, TILES, OPTIONS }

/** Size of a bar sheet, dragged with the finger (Compose AnchoredDraggable) and saved across recompositions. */
@OptIn(ExperimentalFoundationApi::class)
private class BarSheetState(val draggable: AnchoredDraggableState<SheetLevel>, private val scope: CoroutineScope) {
	val level: SheetLevel
		get() = draggable.currentValue

	fun moveTo(level: SheetLevel) {
		scope.launch { draggable.animateTo(level) }
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberBarSheetState(): BarSheetState {
	val density = LocalDensity.current
	var saved by rememberSaveable { mutableStateOf(SheetLevel.TILES) }
	val decay = rememberSplineBasedDecay<Float>()
	val draggable = remember {
		AnchoredDraggableState(
			initialValue = saved,
			// past 40% of the way, or flung, it goes to the next size
			positionalThreshold = { distance -> distance * 0.4f },
			velocityThreshold = { with(density) { 120.dp.toPx() } },
			snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
			decayAnimationSpec = decay,
		)
	}
	LaunchedEffect(draggable) {
		snapshotFlow { draggable.currentValue }.collect { saved = it }
	}
	val scope = rememberCoroutineScope()
	return remember(draggable) { BarSheetState(draggable, scope) }
}

/**
 * Bottom sheet of the player. It follows the finger and snaps to the nearest size (or the next one when flung), like
 * the Material bottom sheets: hidden (handle only), the [tiles], and the tiles with the [options]. Inside the options,
 * scrolling and dragging cooperate: at the top of the options, dragging down shrinks the sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarSheet(
	sheet: BarSheetState,
	options: (@Composable ColumnScope.() -> Unit)? = null,
	tiles: @Composable ColumnScope.() -> Unit,
) {
	val state = sheet.draggable
	val density = LocalDensity.current
	var handleHeight by remember { mutableIntStateOf(0) }
	var tilesHeight by remember { mutableIntStateOf(0) }
	var optionsHeight by remember { mutableIntStateOf(0) }
	val hasOptions = options != null

	// the sizes come from the measured content
	LaunchedEffect(handleHeight, tilesHeight, optionsHeight, hasOptions) {
		if (handleHeight > 0 && tilesHeight > 0) {
			val hidden = handleHeight.toFloat()
			val withTiles = hidden + tilesHeight
			state.updateAnchors(DraggableAnchors {
				SheetLevel.HIDDEN at hidden
				SheetLevel.TILES at withTiles
				if (hasOptions && optionsHeight > 0) {
					SheetLevel.OPTIONS at withTiles + optionsHeight
				}
			})
		}
	}

	val nestedScroll = remember(state) { sheetNestedScroll(state) }
	val offset = state.offset
	Surface(
		shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
		tonalElevation = 3.dp,
		modifier = Modifier
			.fillMaxWidth()
			.nestedScroll(nestedScroll)
			// the offset is the visible height: dragging up makes it grow
			.anchoredDraggable(state = state, reverseDirection = true, orientation = Orientation.Vertical),
	) {
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.then(if (offset.isNaN()) Modifier else Modifier.height(with(density) { offset.toDp() }))
				.clipToBounds(),
		) {
			Column(modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).padding(horizontal = 16.dp)) {
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.height(28.dp)
						.onSizeChanged { handleHeight = it.height }
						.clickable(onClickLabel = stringResource(if (sheet.level == SheetLevel.HIDDEN) R.string.practice_expand else R.string.practice_collapse)) {
							// a tap opens one size more, and from the largest goes back to the tiles
							sheet.moveTo(
								when (sheet.level) {
									SheetLevel.HIDDEN -> SheetLevel.TILES
									SheetLevel.TILES -> if (hasOptions) SheetLevel.OPTIONS else SheetLevel.HIDDEN
									SheetLevel.OPTIONS -> SheetLevel.TILES
								}
							)
						},
					contentAlignment = Alignment.Center,
				) {
					Box(
						Modifier
							.width(40.dp)
							.height(4.dp)
							.clip(RoundedCornerShape(2.dp))
							.background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)),
					)
				}
				Column(modifier = Modifier.onSizeChanged { tilesHeight = it.height }.padding(bottom = 12.dp)) {
					tiles()
				}
				if (options != null) {
					Column(modifier = Modifier.onSizeChanged { optionsHeight = it.height }) {
						options()
					}
				}
			}
		}
	}
}

/** Scrolling inside the sheet first resizes it (up to the largest size, down at the top of the content). */
@OptIn(ExperimentalFoundationApi::class)
private fun sheetNestedScroll(state: AnchoredDraggableState<SheetLevel>) = object : NestedScrollConnection {
	override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
		val delta = available.y
		// finger up: grow the sheet before scrolling its content
		return if (delta < 0 && source == NestedScrollSource.Drag) Offset(0f, -state.dispatchRawDelta(-delta)) else Offset.Zero
	}

	override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
		// finger down with the content already at its top: shrink the sheet
		return if (source == NestedScrollSource.Drag) Offset(0f, -state.dispatchRawDelta(-available.y)) else Offset.Zero
	}

	override suspend fun onPreFling(available: Velocity): Velocity {
		val offset = state.offset
		return if (available.y < 0 && !offset.isNaN() && offset < state.anchors.maxAnchor()) {
			state.settle(-available.y)
			available
		} else {
			Velocity.Zero
		}
	}

	override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
		state.settle(-available.y)
		return available
	}
}

@Composable
private fun Tile(
	description: String,
	checked: Boolean,
	enabled: Boolean,
	onClick: (() -> Unit)?,
	modifier: Modifier,
	content: @Composable () -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	val modifier = modifier.semantics { contentDescription = description }
	val color = if (checked) colors.primaryContainer else colors.surfaceVariant
	val contentColor = when {
		!enabled -> colors.onSurfaceVariant.copy(alpha = 0.6f)
		checked -> colors.onPrimaryContainer
		else -> colors.onSurfaceVariant
	}
	if (onClick != null) {
		Surface(onClick = onClick, enabled = enabled, shape = TILE_SHAPE, color = color, contentColor = contentColor, modifier = modifier) {
			Box(contentAlignment = Alignment.Center) { content() }
		}
	} else {
		Surface(shape = TILE_SHAPE, color = color, contentColor = contentColor, modifier = modifier) {
			Box(contentAlignment = Alignment.Center) { content() }
		}
	}
}

/** Square tile with an icon and a small label below it; highlighted while on. */
@Composable
private fun IconTile(
	icon: Int,
	label: Int,
	checked: Boolean,
	enabled: Boolean = true,
	modifier: Modifier = Modifier.size(TILE_SIZE),
	onClick: () -> Unit,
) {
	val text = stringResource(label)
	Tile(text, checked, enabled, onClick, modifier) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(26.dp))
			Text(text, fontSize = LABEL_SIZE, maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
	}
}

/** Tile with a short bold text (like "+5" or "3"), and an optional small label below it. */
@Composable
private fun TextTile(
	text: String,
	label: String,
	checked: Boolean = false,
	modifier: Modifier = Modifier.size(TILE_SIZE),
	showLabel: Boolean = true,
	onClick: () -> Unit,
) {
	Tile(label, checked, true, onClick, modifier) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
			if (showLabel) {
				Text(label, fontSize = LABEL_SIZE, maxLines = 1, overflow = TextOverflow.Ellipsis)
			}
		}
	}
}

/** Square tile showing a value (like the BPM) with a small label below it. */
@Composable
private fun ValueTile(value: String, label: String, checked: Boolean = false, onClick: (() -> Unit)? = null) {
	Tile("$value $label", checked, true, onClick, Modifier.size(TILE_SIZE)) {
		Column(horizontalAlignment = Alignment.CenterHorizontally) {
			Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
			Text(label, fontSize = LABEL_SIZE, maxLines = 1, overflow = TextOverflow.Ellipsis)
		}
	}
}

/** Large accent colored play / pause tile. */
@Composable
private fun PlayTile(running: Boolean, onClick: () -> Unit, modifier: Modifier) {
	Surface(
		onClick = onClick,
		shape = TILE_SHAPE,
		color = MaterialTheme.colorScheme.primary,
		contentColor = MaterialTheme.colorScheme.onPrimary,
		modifier = modifier.height(TILE_SIZE),
	) {
		Box(contentAlignment = Alignment.Center) {
			Icon(
				painterResource(if (running) R.drawable.ic_bar_pause_24dp else R.drawable.ic_bar_play_24dp),
				stringResource(if (running) R.string.practice_pause else R.string.practice_play),
				modifier = Modifier.size(36.dp),
			)
		}
	}
}
