package app.tuxguitar.android.view.main

import app.tuxguitar.android.fragment.impl.openTuner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.Color
import app.tuxguitar.android.action.impl.track.TGGoToTrackAction
import app.tuxguitar.android.view.tablature.TGSongViewController
import app.tuxguitar.document.TGDocumentContextAttributes
import app.tuxguitar.song.models.TGTrack
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import android.view.Menu
import android.view.View
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.ui.unit.sp
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.tuxguitar.android.action.impl.gui.TGOpenDialogAction
import app.tuxguitar.android.action.impl.gui.TGOpenFragmentAction
import app.tuxguitar.android.activity.TGActivity
import app.tuxguitar.android.activity.TGActivityController
import app.tuxguitar.android.fragment.impl.TGPreferencesFragmentController
import app.tuxguitar.android.menu.controller.TGMenuController
import app.tuxguitar.android.menu.controller.impl.contextual.TGBeatMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGCompositionMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGDurationMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGEditMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGEffectMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGMeasureMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGTrackMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGTransportMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGVelocityMenu
import app.tuxguitar.android.menu.controller.impl.contextual.TGViewMenu
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.dialog.tempo.TGTempoDialogController
import app.tuxguitar.android.view.practice.TGAppMode
import app.tuxguitar.android.view.practice.TGAppModeController
import app.tuxguitar.document.TGDocumentManager
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.player.base.MidiPlayer
import app.tuxguitar.util.TGContext
import kotlinx.coroutines.delay

/**
 * Floating menu bar of the main screen, above the player (like Songsterr): side menu, track, tuner, the three modes
 * and the overflow menu. There is no top bar: the score starts under the status bar, with the song title as its header.
 */
object TGMainMenuBar {

	/** Status bar in the score color again, when the main screen is shown after a screen stacked on it. */
	@JvmStatic
	fun restoreStatusBar(activity: TGActivity) {
		val dark = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
			android.content.res.Configuration.UI_MODE_NIGHT_YES
		colorStatusBar(activity.findContext(), activity, dark)
	}
}

/** The floating menu bar, placed by the main screen just above the player sheet. */
@Composable
fun TGMainMenuBarContent(context: TGContext, modifier: Modifier) {
	Box(modifier = modifier) {
		MainMenuBar(context)
	}
}

@Composable
private fun MainMenuBar(context: TGContext) {
	val activity = TGActivityController.getInstance(context).activity
	var tracks by remember { mutableStateOf(listOf<TrackOption>()) }

	// the song and the selected track can change at any time; polling keeps this simple
	LaunchedEffect(Unit) {
		while (true) {
			tracks = runCatching { trackOptions(context) }.getOrNull() ?: listOf()
			delay(500)
		}
	}

	// no top bar: the status bar (and the notch area) takes the color of the score under it; set again when the
	// screen comes back, since the other screens color it like their own top bar
	val dark = isSystemInDarkTheme()
	val lifecycleOwner = LocalLifecycleOwner.current
	DisposableEffect(lifecycleOwner, dark) {
		val apply = { activity?.let { colorStatusBar(context, it, dark) } }
		apply()
		val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) apply() }
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
	}

	// steps aside while the score is scrolled (and comes back shortly after it stops) and while the player is open
	// at its full size; collected in an effect so scrolling does not recompose the bar on every frame
	var scrolling by remember { mutableStateOf(false) }
	LaunchedEffect(Unit) {
		snapshotFlow { TGScoreScrollState.lastScrollMillis }.drop(1).collectLatest {
			scrolling = true
			delay(800)
			scrolling = false
		}
	}

	AnimatedVisibility(
		visible = !scrolling && !TGScoreScrollState.playerExpanded,
		enter = fadeIn() + slideInVertically { it / 2 },
		exit = fadeOut() + slideOutVertically { it / 2 },
	) {
		Surface(
			shape = CircleShape,
			color = MaterialTheme.colorScheme.surfaceContainerHigh,
			tonalElevation = 3.dp,
			shadowElevation = 6.dp,
			// little room below: the player handle stays clearly apart
			modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
		) {
			Row(
				modifier = Modifier.heightIn(min = 52.dp).padding(horizontal = 4.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				IconButton(onClick = { activity?.drawerManager?.openDrawer() }, modifier = Modifier.size(44.dp)) {
					Icon(painterResource(R.drawable.ic_bar_menu_24dp), stringResource(R.string.main_open_side_menu))
				}
				TrackSelector(context, tracks, Modifier.weight(1f))
				if (activity != null) {
					IconButton(onClick = { openTuner(context, activity) }, modifier = Modifier.size(44.dp)) {
						Icon(painterResource(R.drawable.ic_bar_tuner_24dp), stringResource(R.string.tuner_title))
					}
				}
				ModeMenu(TGAppModeController.getInstance(context))
				if (activity != null) {
					OverflowMenu(context, activity, TGAppModeController.getInstance(context).mode)
				}
			}
		}
	}
}

private data class TrackOption(val track: TGTrack, val name: String, val selected: Boolean)

private fun trackOptions(context: TGContext): List<TrackOption> {
	val song = TGDocumentManager.getInstance(context).song ?: return listOf()
	val selected = TGSongViewController.getInstance(context).caret.track
	return (0 until song.countTracks()).map { i ->
		val track = song.getTrack(i)
		TrackOption(track, track.name, track == selected)
	}
}

/** Name of the track shown in the score; a tap lists the others. */
@Composable
private fun TrackSelector(context: TGContext, tracks: List<TrackOption>, modifier: Modifier) {
	var expanded by remember { mutableStateOf(false) }
	val current = tracks.firstOrNull { it.selected }
	Box(modifier = modifier) {
		val description = stringResource(R.string.main_track_description, current?.name ?: "")
		TextButton(
			onClick = { expanded = true },
			modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
		) {
			Text(
				current?.name ?: "",
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f, fill = false),
			)
			Icon(painterResource(R.drawable.ic_bar_expand_more_24dp), contentDescription = null)
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			tracks.forEach { option ->
				DropdownMenuItem(
					text = { Text(option.name, fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Normal) },
					trailingIcon = if (option.selected) {
						{ Icon(painterResource(R.drawable.ic_bar_check_24dp), null, tint = MaterialTheme.colorScheme.primary) }
					} else null,
					onClick = {
						expanded = false
						val processor = TGActionProcessor(context, TGGoToTrackAction.NAME)
						processor.setAttribute(TGDocumentContextAttributes.ATTRIBUTE_TRACK, option.track)
						processor.process()
					},
				)
			}
		}
	}
}

private class ModeOption(val mode: TGAppMode, val icon: Int, val label: Int)

private val MODES = listOf(
	ModeOption(TGAppMode.PLAY, R.drawable.ic_mode_play_white_24dp, R.string.action_mode_play),
	ModeOption(TGAppMode.PRACTICE, R.drawable.ic_mode_practice_white_24dp, R.string.action_mode_practice),
	ModeOption(TGAppMode.EDIT, R.drawable.ic_edit_white_24dp, R.string.action_mode_edit),
)

/**
 * The current mode as one button; the modes are picked from a menu with their names. Three adjacent icons were too
 * easy to hit by mistake (changing the mode stops playback) and left no room for the track name.
 */
@Composable
private fun ModeMenu(modeController: TGAppModeController) {
	var expanded by remember { mutableStateOf(false) }
	val current = MODES.first { it.mode == modeController.mode }
	val description = stringResource(R.string.main_mode_description, stringResource(current.label))
	Box {
		Surface(
			onClick = { expanded = true },
			shape = CircleShape,
			color = MaterialTheme.colorScheme.primaryContainer,
			contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
			modifier = Modifier.height(40.dp).semantics { contentDescription = description },
		) {
			Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 10.dp, end = 4.dp)) {
				Icon(painterResource(current.icon), contentDescription = null, modifier = Modifier.size(22.dp))
				Icon(painterResource(R.drawable.ic_bar_expand_more_24dp), contentDescription = null, modifier = Modifier.size(20.dp))
			}
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
			MODES.forEach { option ->
				val selected = option.mode == current.mode
				DropdownMenuItem(
					text = { Text(stringResource(option.label), fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
					leadingIcon = { Icon(painterResource(option.icon), contentDescription = null) },
					trailingIcon = if (selected) {
						{ Icon(painterResource(R.drawable.ic_bar_check_24dp), null, tint = MaterialTheme.colorScheme.primary) }
					} else null,
					onClick = {
						expanded = false
						modeController.setMode(option.mode)
					},
					modifier = Modifier.semantics { this.selected = selected },
				)
			}
		}
	}
}

/** Status bar in the color of the score background, with icons that stay readable on it. */
private fun colorStatusBar(context: TGContext, activity: TGActivity, dark: Boolean) {
	val background = runCatching { TGSongViewController.getInstance(context).styles.backgroundColor }.getOrNull()
	val color = if (background != null) {
		android.graphics.Color.rgb(background.red, background.green, background.blue)
	} else if (dark) 0xFF121212.toInt() else android.graphics.Color.WHITE
	activity.window.statusBarColor = color
	(activity.findViewById<View>(R.id.root_layout) as? DrawerLayout)?.setStatusBarBackgroundColor(color)
	WindowCompat.getInsetsController(activity.window, activity.window.decorView).isAppearanceLightStatusBars = !dark
}

// ---------------------------------------------------------------- overflow menu

/** A menu level: a list of entries of ours, or a menu filled by one of the app's menu controllers. */
private class MenuLevel(val title: String, val entries: List<RootEntry>? = null, val menu: Menu? = null)

/** Entry of our own levels: opens a controller menu, opens a group of entries, or runs something directly. */
private class RootEntry(
	val label: Int,
	val controller: ((TGActivity) -> TGMenuController)? = null,
	val group: List<RootEntry>? = null,
	val run: (() -> Unit)? = null,
)

/**
 * Entries for the current mode: Play and Practice only need the view options and settings (the bottom bars
 * handle playback); Edit has every menu, with the note level ones grouped.
 */
private fun rootEntries(mode: TGAppMode, context: TGContext, activity: TGActivity): List<RootEntry> {
	val view = RootEntry(R.string.action_menu_view, { TGViewMenu(it) })
	val settings = RootEntry(R.string.action_menu_settings, run = { openSettings(context, activity) })
	if (mode != TGAppMode.EDIT) {
		return listOf(view, settings)
	}
	return listOf(
		RootEntry(R.string.action_menu_edit, { TGEditMenu(it) }),
		view,
		RootEntry(R.string.action_menu_composition, { TGCompositionMenu(it) }),
		RootEntry(R.string.action_menu_track, { TGTrackMenu(it) }),
		RootEntry(R.string.action_menu_measure, { TGMeasureMenu(it) }),
		RootEntry(R.string.main_menu_note, group = listOf(
			RootEntry(R.string.action_menu_beat, { TGBeatMenu(it) }),
			RootEntry(R.string.action_menu_duration, { TGDurationMenu(it) }),
			RootEntry(R.string.action_menu_dynamic, { TGVelocityMenu(it) }),
			RootEntry(R.string.action_menu_effects, { TGEffectMenu(it) }),
		)),
		RootEntry(R.string.action_menu_transport, { TGTransportMenu(it) }),
		RootEntry(R.string.main_edit_song_tempo, run = { openTempoDialog(context, activity) }),
		settings,
	)
}

/** The ⋮ button opens a bottom sheet (reachable with the thumb) that drills into the menus level by level. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverflowMenu(context: TGContext, activity: TGActivity, mode: TGAppMode) {
	var open by remember { mutableStateOf(false) }
	var levels by remember { mutableStateOf(listOf<MenuLevel>()) }
	val rootTitle = stringResource(R.string.main_more_options)

	fun close() {
		open = false
		levels = listOf()
	}

	IconButton(onClick = { levels = listOf(MenuLevel(rootTitle, entries = rootEntries(mode, context, activity))); open = true }) {
		Icon(painterResource(R.drawable.ic_bar_more_24dp), rootTitle)
	}
	if (!open) return

	ModalBottomSheet(onDismissRequest = { close() }) {
		val level = levels.last()
		// header stays in place while the items scroll
		Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
			if (levels.size > 1) {
				IconButton(onClick = { levels = levels.dropLast(1) }) {
					Icon(painterResource(R.drawable.ic_bar_back_24dp), stringResource(R.string.main_menu_back))
				}
			}
			Text(
				level.title,
				style = MaterialTheme.typography.titleMedium,
				fontWeight = FontWeight.Bold,
				modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
			)
		}
		HorizontalDivider()
		LazyColumn(modifier = Modifier.padding(bottom = 16.dp)) {
			val entries = level.entries
			val menu = level.menu
			if (entries != null) {
				items(entries) { entry ->
					val label = stringResource(entry.label)
					SheetRow(label, enabled = true, hasSubmenu = entry.controller != null || entry.group != null, checked = false) {
						val controller = entry.controller
						val group = entry.group
						when {
							controller != null -> levels = levels + MenuLevel(label, menu = buildMenu(activity, controller(activity)))
							group != null -> levels = levels + MenuLevel(label, entries = group)
							else -> {
								close()
								entry.run?.invoke()
							}
						}
					}
				}
			} else if (menu != null) {
				val visibleItems = (0 until menu.size()).map { menu.getItem(it) }.filter { it.isVisible }
				items(visibleItems) { item ->
					val title = item.title?.toString() ?: ""
					SheetRow(title, enabled = item.isEnabled, hasSubmenu = item.hasSubMenu(), checked = item.isCheckable && item.isChecked) {
						val subMenu = item.subMenu
						if (item.hasSubMenu() && subMenu != null) {
							levels = levels + MenuLevel(title, menu = subMenu)
						} else {
							close()
							// runs the click listener the menu controller set on the item
							menu.performIdentifierAction(item.itemId, 0)
						}
					}
				}
			}
		}
	}
}

@Composable
private fun SheetRow(label: String, enabled: Boolean, hasSubmenu: Boolean, checked: Boolean, onClick: () -> Unit) {
	ListItem(
		headlineContent = { Text(label) },
		trailingContent = when {
			hasSubmenu -> {
				{ Icon(painterResource(R.drawable.ic_bar_chevron_right_24dp), null) }
			}
			checked -> {
				{ Icon(painterResource(R.drawable.ic_bar_check_24dp), null, tint = MaterialTheme.colorScheme.primary) }
			}
			else -> null
		},
		colors = if (enabled) ListItemDefaults.colors() else ListItemDefaults.colors(
			headlineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
		),
		modifier = Modifier.heightIn(min = 56.dp).clickable(enabled = enabled, onClick = onClick),
	)
}

/** Lets a menu controller fill a plain Android menu, without showing it. */
private fun buildMenu(activity: TGActivity, controller: TGMenuController): Menu {
	val menu = PopupMenu(activity, View(activity)).menu
	controller.inflate(menu, activity.menuInflater)
	return menu
}

private fun openTempoDialog(context: TGContext, activity: TGActivity) {
	if (MidiPlayer.getInstance(context).isRunning) {
		Toast.makeText(activity, R.string.main_stop_playback_first, Toast.LENGTH_SHORT).show()
		return
	}
	val processor = TGActionProcessor(context, TGOpenDialogAction.NAME)
	processor.setAttribute(TGOpenDialogAction.ATTRIBUTE_DIALOG_ACTIVITY, activity)
	processor.setAttribute(TGOpenDialogAction.ATTRIBUTE_DIALOG_CONTROLLER, TGTempoDialogController())
	processor.process()
}

private fun openSettings(context: TGContext, activity: TGActivity) {
	val processor = TGActionProcessor(context, TGOpenFragmentAction.NAME)
	processor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_CONTROLLER, TGPreferencesFragmentController())
	processor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_ACTIVITY, activity)
	processor.process()
}
