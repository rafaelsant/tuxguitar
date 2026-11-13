package app.tuxguitar.android.view.drums

import android.content.Context
import android.content.pm.PackageManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Handler
import android.os.Looper
import app.tuxguitar.android.application.TGDiagnostics

/** A pad hit: note on from the drum module. [timeNanos] is on the [System.nanoTime] clock. */
data class TGDrumHit(val note: Int, val velocity: Int, val channel: Int, val timeNanos: Long)

/** Connection to the drum module, as shown to the user. */
sealed class TGMidiStatus {
	/** The phone has no MIDI support. */
	object NotSupported : TGMidiStatus()
	object NoDevice : TGMidiStatus()
	/** The device in use was unplugged (or turned off), and no other is there. */
	data class Disconnected(val name: String) : TGMidiStatus()
	data class Connecting(val name: String) : TGMidiStatus()
	data class Connected(val name: String) : TGMidiStatus()
	data class Failed(val name: String) : TGMidiStatus()
}

/**
 * MIDI input from a drum module (like the Yamaha DTX402, on its USB TO HOST port): connects to the first device that
 * sends MIDI, follows devices being plugged in and out, and reports note on messages as hits. USB MIDI devices are
 * handled by the system, with no permission to ask. Callbacks come on the main thread.
 */
class TGMidiInput(
	context: Context,
	private val onStatus: (TGMidiStatus) -> Unit,
	private val onHit: (TGDrumHit) -> Unit,
) {

	private val manager: MidiManager? =
		if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_MIDI)) {
			context.getSystemService(Context.MIDI_SERVICE) as? MidiManager
		} else null

	private val handler = Handler(Looper.getMainLooper())
	private var running = false
	private var deviceInfo: MidiDeviceInfo? = null
	private var device: MidiDevice? = null
	private var port: MidiOutputPort? = null
	// hits of a port closed meanwhile are dropped
	private var session = 0

	private val callback = object : MidiManager.DeviceCallback() {
		override fun onDeviceAdded(info: MidiDeviceInfo) {
			if (running && deviceInfo == null && info.outputPortCount > 0) {
				open(info)
			}
		}

		override fun onDeviceRemoved(info: MidiDeviceInfo) {
			if (running && info.id == deviceInfo?.id) {
				TGDiagnostics.log("midi: device removed: " + nameOf(info))
				close()
				openFirst(removed = nameOf(info))
			}
		}
	}

	@Suppress("DEPRECATION")
	fun start() {
		val manager = manager
		if (manager == null) {
			onStatus(TGMidiStatus.NotSupported)
			return
		}
		if (running) {
			return
		}
		running = true
		// the handler variant is deprecated on API 33 but is the one available since API 23
		manager.registerDeviceCallback(callback, handler)
		openFirst()
	}

	fun stop() {
		if (!running) {
			return
		}
		running = false
		manager?.unregisterDeviceCallback(callback)
		close()
	}

	@Suppress("DEPRECATION")
	private fun openFirst(removed: String? = null) {
		val devices = manager?.devices.orEmpty().filter { it.outputPortCount > 0 }
		// a module on a cable first, then Bluetooth, then apps that offer MIDI
		val info = devices.minByOrNull {
			when (it.type) {
				MidiDeviceInfo.TYPE_USB -> 0
				MidiDeviceInfo.TYPE_BLUETOOTH -> 1
				else -> 2
			}
		}
		if (info == null) {
			onStatus(if (removed != null) TGMidiStatus.Disconnected(removed) else TGMidiStatus.NoDevice)
		} else {
			open(info)
		}
	}

	private fun open(info: MidiDeviceInfo) {
		val manager = manager ?: return
		val name = nameOf(info)
		deviceInfo = info
		val session = ++session
		onStatus(TGMidiStatus.Connecting(name))
		TGDiagnostics.log("midi: opening " + name + " (type " + info.type + ", " + info.outputPortCount + " output ports)")
		manager.openDevice(info, { opened ->
			if (!running || session != this.session) {
				opened?.close()
				return@openDevice
			}
			val port = opened?.openOutputPort(0)
			if (opened == null || port == null) {
				TGDiagnostics.log("midi: could not open " + name)
				opened?.close()
				onStatus(TGMidiStatus.Failed(name))
				return@openDevice
			}
			device = opened
			this.port = port
			port.connect(Receiver(session))
			onStatus(TGMidiStatus.Connected(name))
		}, handler)
	}

	private fun close() {
		session++
		runCatching { port?.close() }
		runCatching { device?.close() }
		port = null
		device = null
		deviceInfo = null
	}

	private fun nameOf(info: MidiDeviceInfo): String {
		val properties = info.properties
		return properties.getString(MidiDeviceInfo.PROPERTY_NAME)
			?: properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT)
			?: "MIDI"
	}

	/**
	 * Splits the incoming bytes into messages: running status (status byte left out when repeated), real time bytes
	 * (clock, active sensing) in between, system exclusive skipped. Called on a MIDI thread.
	 */
	private inner class Receiver(private val session: Int) : MidiReceiver() {
		private var status = 0
		private val data = IntArray(2)
		private var count = 0
		private var sysex = false

		override fun onSend(msg: ByteArray, offset: Int, length: Int, timestamp: Long) {
			for (i in offset until offset + length) {
				val b = msg[i].toInt() and 0xFF
				when {
					b >= 0xF8 -> Unit
					b == 0xF0 -> {
						sysex = true
						status = 0
					}
					b == 0xF7 -> sysex = false
					b >= 0x80 -> {
						sysex = false
						status = if (b < 0xF0) b else 0
						count = 0
					}
					sysex || status == 0 -> Unit
					else -> {
						data[count++] = b
						val type = status and 0xF0
						if (count == (if (type == 0xC0 || type == 0xD0) 1 else 2)) {
							count = 0
							// a note on with velocity 0 is a note off
							if (type == 0x90 && data[1] > 0) {
								val hit = TGDrumHit(data[0], data[1], status and 0x0F, timestamp)
								handler.post { if (running && session == this@TGMidiInput.session) onHit(hit) }
							}
						}
					}
				}
			}
		}
	}
}
