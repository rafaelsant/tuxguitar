package app.tuxguitar.android.fragment.impl

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.Fragment
import app.tuxguitar.android.R
import app.tuxguitar.android.action.impl.gui.TGOpenFragmentAction
import app.tuxguitar.android.activity.TGActivity
import app.tuxguitar.android.fragment.TGFragmentController
import app.tuxguitar.android.view.compose.TGComposeTheme
import app.tuxguitar.android.view.compose.TGScreenTopBar
import app.tuxguitar.android.view.drums.TGDrumHit
import app.tuxguitar.android.view.drums.TGDrumKit
import app.tuxguitar.android.view.drums.TGDrumPiece
import app.tuxguitar.android.view.drums.TGMidiInput
import app.tuxguitar.android.view.drums.TGMidiStatus
import app.tuxguitar.editor.action.TGActionProcessor
import app.tuxguitar.util.TGContext

class TGDrumMonitorFragmentController : TGFragmentController<TGDrumMonitorFragment> {
	override fun getFragment(): TGDrumMonitorFragment = TGDrumMonitorFragment()
}

/** Opens the drum kit screen (side menu). */
fun openDrumMonitor(context: TGContext, activity: TGActivity) {
	val processor = TGActionProcessor(context, TGOpenFragmentAction.NAME)
	processor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_CONTROLLER, TGDrumMonitorFragmentController())
	processor.setAttribute(TGOpenFragmentAction.ATTRIBUTE_ACTIVITY, activity)
	processor.process()
}

/**
 * Drum kit monitor: shows the hits of a MIDI drum module (connected by USB) as they arrive, lighting up the piece that
 * was hit, with the list of the last notes. It checks the connection and which note each pad sends, before the hits
 * are compared with the score.
 */
class TGDrumMonitorFragment : Fragment() {

	private companion object {
		const val MAX_HITS = 50
	}

	/** A hit as listed: [interval] is the time since the hit before, in ms (null for the first one). */
	private class ListedHit(val id: Long, val hit: TGDrumHit, val interval: Long?)

	private var status by mutableStateOf<TGMidiStatus?>(null)
	private val hits = mutableStateListOf<ListedHit>()
	/** Per piece: how many hits so far (each one flashes the pad) and the velocity of the last one. */
	private val padHits = mutableStateMapOf<TGDrumPiece, Pair<Int, Int>>()
	private var input: TGMidiInput? = null
	private var nextId = 0L

	private val tgActivity: TGActivity
		get() = requireActivity() as TGActivity

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
		return ComposeView(requireContext()).apply {
			// hands are on the sticks
			keepScreenOn = true
			setContent {
				TGComposeTheme {
					Surface(modifier = Modifier.fillMaxSize()) {
						Column {
							TGScreenTopBar(stringResource(R.string.drums_title), { tgActivity.callBackAction() })
							DrumScreen()
						}
					}
				}
			}
		}
	}

	override fun onResume() {
		super.onResume()
		tgActivity.setLegacyActionBarVisible(false)
		val input = TGMidiInput(requireContext().applicationContext, { status = it }, ::onHit)
		this.input = input
		input.start()
	}

	override fun onPause() {
		input?.stop()
		input = null
		super.onPause()
	}

	private fun onHit(hit: TGDrumHit) {
		val previous = hits.firstOrNull()?.hit
		val interval = previous?.let { (hit.timeNanos - it.timeNanos) / 1_000_000 }
		hits.add(0, ListedHit(nextId++, hit, interval))
		while (hits.size > MAX_HITS) {
			hits.removeAt(hits.lastIndex)
		}
		TGDrumPiece.of(hit.note)?.let { piece ->
			padHits[piece] = (padHits[piece]?.first ?: 0) + 1 to hit.velocity
		}
	}

	// ---- UI

	@Composable
	private fun DrumScreen() {
		// one list, so a short or landscape screen scrolls instead of squeezing the hits out
		LazyColumn(contentPadding = PaddingValues(16.dp), modifier = Modifier.fillMaxSize()) {
			item(key = "status") { StatusCard() }
			// laid out like a kit seen from the stool: cymbals, toms, then snare and kick
			item(key = "pads") {
				Column(
					verticalArrangement = Arrangement.spacedBy(PAD_SPACING),
					modifier = Modifier.padding(top = 16.dp),
				) {
					PadRow(TGDrumPiece.HI_HAT, TGDrumPiece.CRASH, TGDrumPiece.RIDE)
					PadRow(TGDrumPiece.TOM_1, TGDrumPiece.TOM_2, TGDrumPiece.FLOOR_TOM)
					PadRow(TGDrumPiece.SNARE, TGDrumPiece.KICK)
				}
			}
			item(key = "header") {
				Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
					Text(
						stringResource(R.string.drums_last_hits),
						style = MaterialTheme.typography.titleMedium,
						fontWeight = FontWeight.Bold,
						modifier = Modifier.weight(1f),
					)
					TextButton(
						onClick = {
							hits.clear()
							padHits.clear()
						},
						enabled = hits.isNotEmpty(),
					) {
						Text(stringResource(R.string.drums_clear))
					}
				}
			}
			if (hits.isEmpty()) {
				item(key = "hint") {
					Text(
						stringResource(R.string.drums_hint),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(top = 8.dp),
					)
				}
			}
			items(hits, key = { it.id }) { HitRow(it) }
		}
	}

	@Composable
	private fun StatusCard() {
		val status = status
		val text = when (status) {
			null, TGMidiStatus.NoDevice -> stringResource(R.string.drums_no_device)
			is TGMidiStatus.Disconnected -> stringResource(R.string.drums_disconnected, status.name)
			TGMidiStatus.NotSupported -> stringResource(R.string.drums_not_supported)
			is TGMidiStatus.Connecting -> stringResource(R.string.drums_connecting, status.name)
			is TGMidiStatus.Connected -> stringResource(R.string.drums_connected, status.name)
			is TGMidiStatus.Failed -> stringResource(R.string.drums_open_failed, status.name)
		}
		val colors = MaterialTheme.colorScheme
		val (background, content) = when (status) {
			is TGMidiStatus.Connected -> colors.primaryContainer to colors.onPrimaryContainer
			is TGMidiStatus.Failed, TGMidiStatus.NotSupported -> colors.errorContainer to colors.onErrorContainer
			else -> colors.surfaceContainerHigh to colors.onSurface
		}
		Surface(shape = RoundedCornerShape(16.dp), color = background, contentColor = content, modifier = Modifier.fillMaxWidth()) {
			Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
				if (status is TGMidiStatus.Connected) {
					Icon(
						painterResource(R.drawable.ic_bar_check_24dp),
						contentDescription = null,
						modifier = Modifier.padding(end = 12.dp).size(24.dp),
					)
				}
				Text(
					text,
					style = MaterialTheme.typography.bodyLarge,
					modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
				)
			}
		}
	}

	@Composable
	private fun PadRow(vararg pieces: TGDrumPiece) {
		Row(horizontalArrangement = Arrangement.spacedBy(PAD_SPACING), modifier = Modifier.fillMaxWidth()) {
			pieces.forEach { Pad(it) }
		}
	}

	/** A piece of the kit: it lights up on each hit, brighter for a harder one, and fades out. */
	@Composable
	private fun RowScope.Pad(piece: TGDrumPiece) {
		val (count, velocity) = padHits[piece] ?: (0 to 0)
		val flash = remember { Animatable(0f) }
		LaunchedEffect(count) {
			if (count > 0) {
				flash.snapTo(0.35f + 0.65f * velocity / 127f)
				flash.animateTo(0f, tween(FLASH_MILLIS))
			} else {
				flash.snapTo(0f)
			}
		}
		val colors = MaterialTheme.colorScheme
		val background = lerp(colors.surfaceContainerHigh, colors.primary, flash.value)
		// the text follows the background as it fades, readable in both themes
		val content = if (background.luminance() > 0.4f) Color.Black.copy(alpha = 0.87f) else Color.White
		Surface(
			shape = RoundedCornerShape(16.dp),
			color = background,
			contentColor = content,
			modifier = Modifier.weight(1f).height(PAD_HEIGHT),
		) {
			Column(
				horizontalAlignment = Alignment.CenterHorizontally,
				verticalArrangement = Arrangement.Center,
				modifier = Modifier.padding(4.dp),
			) {
				Text(
					stringResource(piece.label),
					style = MaterialTheme.typography.titleMedium,
					fontWeight = FontWeight.Bold,
					textAlign = TextAlign.Center,
					maxLines = 2,
					overflow = TextOverflow.Ellipsis,
				)
				// the velocity of the last hit, as a number: large enough to read from the stool
				Text(
					if (count > 0) velocity.toString() else " ",
					style = MaterialTheme.typography.titleLarge,
					maxLines = 1,
				)
			}
		}
	}

	@Composable
	private fun HitRow(item: ListedHit) {
		val hit = item.hit
		val piece = TGDrumPiece.of(hit.note)
		// the General MIDI name tells what the pad sends, so it comes first
		val name = TGDrumKit.noteName(hit.note)
		Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text(
					piece?.let { stringResource(it.label) } ?: stringResource(R.string.drums_other),
					style = MaterialTheme.typography.bodyLarge,
					fontWeight = FontWeight.Bold,
				)
				Text(
					name?.let { " · $it" } ?: "",
					style = MaterialTheme.typography.bodyLarge,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
					modifier = Modifier.weight(1f),
				)
				item.interval?.let {
					Text(
						stringResource(R.string.drums_interval, it),
						style = MaterialTheme.typography.bodyMedium,
						color = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.padding(start = 8.dp),
					)
				}
			}
			// drum modules send on channel 10: another one is worth seeing
			val detail = stringResource(R.string.drums_hit_detail, hit.note, hit.velocity) +
				(if (hit.channel != DRUM_CHANNEL) " · " + stringResource(R.string.drums_channel, hit.channel + 1) else "")
			Text(
				detail,
				style = MaterialTheme.typography.bodyMedium,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
			)
		}
		HorizontalDivider()
	}
}

private val PAD_HEIGHT = 88.dp
private val PAD_SPACING = 10.dp
private const val FLASH_MILLIS = 350
// channel 10, counted from 0
private const val DRUM_CHANNEL = 9
