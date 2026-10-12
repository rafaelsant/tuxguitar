package app.tuxguitar.android.view.main

import android.view.Menu
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
	var title by remember { mutableStateOf(defaultTitle) }

	// the song can change at any time (open, new, edit info); polling keeps this simple
	LaunchedEffect(Unit) {
		while (true) {
			title = runCatching { songTitle(context) }.getOrNull() ?: defaultTitle
			delay(500)
		}
	}

	Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
		Row(
			modifier = Modifier.height(56.dp).padding(horizontal = 4.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			IconButton(onClick = { activity?.drawerManager?.openDrawer() }) {
				Icon(painterResource(R.drawable.ic_bar_menu_24dp), stringResource(R.string.main_open_side_menu))
			}
			Text(
				title,
				style = MaterialTheme.typography.titleLarge,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				modifier = Modifier.weight(1f).padding(start = 4.dp),
			)
			ModeSwitcher(TGAppModeController.getInstance(context))
			if (activity != null) {
				OverflowMenu(context, activity)
			}
		}
	}
}

private fun songTitle(context: TGContext): String? {
	val song = TGDocumentManager.getInstance(context).song ?: return null
	return song.name?.takeIf { it.isNotBlank() }
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

/** A menu level: the root list, or a menu filled by one of the app's menu controllers. */
private class MenuLevel(val title: String, val menu: Menu)

/** Root entries: open a submenu (controller), or run something directly. */
private class RootEntry(val label: Int, val controller: ((TGActivity) -> TGMenuController)? = null, val run: (() -> Unit)? = null)

@Composable
private fun OverflowMenu(context: TGContext, activity: TGActivity) {
	var expanded by remember { mutableStateOf(false) }
	var levels by remember { mutableStateOf(listOf<MenuLevel>()) }

	val entries = remember {
		listOf(
			RootEntry(R.string.action_change_tempo, run = { openTempoDialog(context, activity) }),
			RootEntry(R.string.action_menu_edit, { TGEditMenu(it) }),
			RootEntry(R.string.action_menu_view, { TGViewMenu(it) }),
			RootEntry(R.string.action_menu_composition, { TGCompositionMenu(it) }),
			RootEntry(R.string.action_menu_track, { TGTrackMenu(it) }),
			RootEntry(R.string.action_menu_measure, { TGMeasureMenu(it) }),
			RootEntry(R.string.action_menu_beat, { TGBeatMenu(it) }),
			RootEntry(R.string.action_menu_duration, { TGDurationMenu(it) }),
			RootEntry(R.string.action_menu_dynamic, { TGVelocityMenu(it) }),
			RootEntry(R.string.action_menu_effects, { TGEffectMenu(it) }),
			RootEntry(R.string.action_menu_transport, { TGTransportMenu(it) }),
			RootEntry(R.string.action_menu_settings, run = { openSettings(context, activity) }),
		)
	}

	fun close() {
		expanded = false
		levels = listOf()
	}

	Box {
		IconButton(onClick = { levels = listOf(); expanded = true }) {
			Icon(painterResource(R.drawable.ic_bar_more_24dp), stringResource(R.string.main_more_options))
		}
		DropdownMenu(expanded = expanded, onDismissRequest = { close() }) {
			val level = levels.lastOrNull()
			if (level == null) {
				entries.forEach { entry ->
					val label = stringResource(entry.label)
					DropdownMenuItem(
						text = { Text(label) },
						trailingIcon = if (entry.controller != null) {
							{ Icon(painterResource(R.drawable.ic_bar_chevron_right_24dp), null) }
						} else null,
						onClick = {
							val controller = entry.controller
							if (controller != null) {
								levels = levels + MenuLevel(label, buildMenu(activity, controller(activity)))
							} else {
								close()
								entry.run?.invoke()
							}
						},
					)
				}
			} else {
				DropdownMenuItem(
					text = { Text(level.title, fontWeight = FontWeight.Bold) },
					leadingIcon = { Icon(painterResource(R.drawable.ic_bar_back_24dp), stringResource(R.string.main_menu_back)) },
					onClick = { levels = levels.dropLast(1) },
				)
				HorizontalDivider()
				MenuItems(level.menu,
					onOpenSubmenu = { title, menu -> levels = levels + MenuLevel(title, menu) },
					onItemDone = { close() },
				)
			}
		}
	}
}

/** Items of an Android menu filled by a menu controller, with their enabled / checked state. */
@Composable
private fun MenuItems(menu: Menu, onOpenSubmenu: (String, Menu) -> Unit, onItemDone: () -> Unit) {
	for (i in 0 until menu.size()) {
		val item = menu.getItem(i)
		if (!item.isVisible) continue
		val title = item.title?.toString() ?: ""
		DropdownMenuItem(
			text = { Text(title) },
			enabled = item.isEnabled,
			trailingIcon = when {
				item.hasSubMenu() -> {
					{ Icon(painterResource(R.drawable.ic_bar_chevron_right_24dp), null) }
				}
				item.isCheckable && item.isChecked -> {
					{ Icon(painterResource(R.drawable.ic_bar_check_24dp), null, tint = MaterialTheme.colorScheme.primary) }
				}
				else -> null
			},
			onClick = {
				val subMenu = item.subMenu
				if (item.hasSubMenu() && subMenu != null) {
					onOpenSubmenu(title, subMenu)
				} else {
					onItemDone()
					// runs the click listener the menu controller set on the item
					menu.performIdentifierAction(item.itemId, 0)
				}
			},
		)
	}
}

/** Lets a menu controller fill a plain Android menu, without showing it. */
private fun buildMenu(activity: TGActivity, controller: TGMenuController): Menu {
	val menu = PopupMenu(activity, View(activity)).menu
	controller.inflate(menu, activity.menuInflater)
	return menu
}

private fun openTempoDialog(context: TGContext, activity: TGActivity) {
	if (MidiPlayer.getInstance(context).isRunning) return
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
