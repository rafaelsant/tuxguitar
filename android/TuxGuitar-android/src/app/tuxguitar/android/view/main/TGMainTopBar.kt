package app.tuxguitar.android.view.main

import app.tuxguitar.android.fragment.impl.openTuner
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

/** Top bar of the main screen: side menu, song title, the three modes and the overflow menu. */
object TGMainTopBar {
	@JvmStatic
	fun bind(view: ComposeView, context: TGContext) {
		view.setContent {
			TGComposeTheme {
				MainTopBar(context)
			}
		}
	}
}

@Composable
private fun MainTopBar(context: TGContext) {
	val activity = TGActivityController.getInstance(context).activity
	val defaultTitle = stringResource(R.string.app_name)
	var title by remember { mutableStateOf(SongTitle(defaultTitle, null)) }

	// the song can change at any time (open, new, edit info); polling keeps this simple
	LaunchedEffect(Unit) {
		while (true) {
			title = runCatching { songTitle(context) }.getOrNull() ?: SongTitle(defaultTitle, null)
			delay(500)
		}
	}

	// the status bar (and the notch area around it) takes the color of the bar, so no band is left between them
	val barColor = MaterialTheme.colorScheme.surfaceContainer
	val darkIcons = !isSystemInDarkTheme()
	SideEffect {
		activity?.let {
			it.window.statusBarColor = barColor.toArgb()
			(it.findViewById<View>(R.id.root_layout) as? DrawerLayout)?.setStatusBarBackgroundColor(barColor.toArgb())
			WindowCompat.getInsetsController(it.window, it.window.decorView).isAppearanceLightStatusBars = darkIcons
		}
	}

	Surface(color = barColor, modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.height(56.dp).padding(horizontal = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = { activity?.drawerManager?.openDrawer() }) {
				Icon(painterResource(R.drawable.ic_bar_menu_24dp), stringResource(R.string.main_open_side_menu))
			}
			// at 360dp only ~110dp are left for the title: two small lines instead of one large
			Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
				Text(title.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
				title.artist?.let {
					Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
				}
			}
			ModeSwitcher(TGAppModeController.getInstance(context))
			if (activity != null) {
				OverflowMenu(context, activity, TGAppModeController.getInstance(context).mode)
			}
		}
	}
}

private data class SongTitle(val name: String, val artist: String?)

private fun songTitle(context: TGContext): SongTitle? {
	val song = TGDocumentManager.getInstance(context).song ?: return null
	val name = song.name?.takeIf { it.isNotBlank() } ?: return null
	return SongTitle(name, song.artist?.takeIf { it.isNotBlank() })
}

/** The three modes in a pill; the selected one is filled with the accent container color. */
@Composable
private fun ModeSwitcher(modeController: TGAppModeController) {
	Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
		Row {
			ModeButton(modeController, TGAppMode.PLAY, R.drawable.ic_mode_play_white_24dp, R.string.action_mode_play)
			ModeButton(modeController, TGAppMode.PRACTICE, R.drawable.ic_mode_practice_white_24dp, R.string.action_mode_practice)
			ModeButton(modeController, TGAppMode.EDIT, R.drawable.ic_edit_white_24dp, R.string.action_mode_edit)
		}
	}
}

@Composable
private fun ModeButton(modeController: TGAppModeController, mode: TGAppMode, icon: Int, label: Int) {
	IconToggleButton(
		checked = modeController.mode == mode,
		onCheckedChange = { modeController.setMode(mode) },
		colors = IconButtonDefaults.iconToggleButtonColors(
			checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
			checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
		),
	) {
		Icon(painterResource(icon), stringResource(label))
	}
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
	val tuner = RootEntry(R.string.tuner_title, run = { openTuner(context, activity) })
	if (mode != TGAppMode.EDIT) {
		return listOf(tuner, view, settings)
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
		tuner,
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
