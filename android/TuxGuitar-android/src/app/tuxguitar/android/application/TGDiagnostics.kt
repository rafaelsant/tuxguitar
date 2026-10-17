package app.tuxguitar.android.application

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Freeze and crash reports for testing on the phone, where there is no logcat at hand. A watchdog thread checks that
 * the main thread keeps running; when it is stuck for a few seconds, the stack of every thread is written to a file
 * (what the main thread waits for, and who holds it). Uncaught exceptions are written the same way. The last report
 * can be shared from Settings.
 */
object TGDiagnostics {

	private const val FILE = "diagnostic-report.txt"
	private const val LOG_FILE = "debug-log.txt"
	private const val LOG_MAX_BYTES = 256 * 1024
	private const val CHECK_MILLIS = 1000L
	private const val STALL_MILLIS = 5000L
	private const val MAX_FRAMES = 40

	@Volatile
	private var lastMainTick = 0L
	private var started = false
	private var appContext: Context? = null
	private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

	@JvmStatic
	@Synchronized
	fun start(context: Context) {
		if (started) {
			return
		}
		started = true
		val appContext = context.applicationContext
		this.appContext = appContext
		log("app started")
		installCrashHandler(appContext)

		val main = Handler(Looper.getMainLooper())
		lastMainTick = SystemClock.uptimeMillis()
		val thread = Thread({
			var reported = false
			while (true) {
				main.post { lastMainTick = SystemClock.uptimeMillis() }
				try {
					Thread.sleep(CHECK_MILLIS)
				} catch (e: InterruptedException) {
					return@Thread
				}
				val stalled = SystemClock.uptimeMillis() - lastMainTick
				if (stalled >= STALL_MILLIS) {
					// one report per freeze
					if (!reported) {
						reported = true
						write(appContext, "Main thread stuck for ${stalled / 1000} s", null)
						log("FREEZE: main thread stuck for ${stalled / 1000} s")
					}
				} else {
					reported = false
				}
			}
		}, "tg-freeze-watchdog")
		thread.isDaemon = true
		thread.start()
	}

	private fun installCrashHandler(context: Context) {
		val previous = Thread.getDefaultUncaughtExceptionHandler()
		Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
			runCatching { write(context, "Crash in thread '${thread.name}'", throwable) }
			log("CRASH in '${thread.name}'", throwable)
			previous?.uncaughtException(thread, throwable)
		}
	}

	@JvmStatic
	fun reportFile(context: Context): File = File(context.applicationContext.filesDir, FILE)

	@JvmStatic
	fun hasReport(context: Context): Boolean = reportFile(context).exists()

	@JvmStatic
	fun readReport(context: Context): String? = runCatching { reportFile(context).readText() }.getOrNull()

	@JvmStatic
	fun deleteReport(context: Context) {
		reportFile(context).delete()
	}

	// ---- navigation log: what happened before a broken screen, kept in a file across restarts

	@JvmStatic
	fun logFile(context: Context): File = File(context.applicationContext.filesDir, LOG_FILE)

	@JvmStatic
	fun readLog(context: Context): String? = runCatching { logFile(context).readText() }.getOrNull()

	@JvmStatic
	@Synchronized
	fun log(message: String) {
		val context = appContext ?: return
		runCatching {
			val file = logFile(context)
			if (file.exists() && file.length() > LOG_MAX_BYTES) {
				// keep the newest half
				val text = file.readText()
				file.writeText(text.substring(text.length / 2))
			}
			file.appendText(timeFormat.format(Date()) + " [" + Thread.currentThread().name + "] " + message + "\n")
		}
	}

	@JvmStatic
	fun log(message: String, throwable: Throwable) {
		log(message + "\n" + android.util.Log.getStackTraceString(throwable))
	}

	/**
	 * Who holds the editor lock (the actions that change the song or the screens take it) and what that thread is
	 * doing: an action on the main thread waits for it, retrying, without blocking the thread.
	 */
	@JvmStatic
	fun lockInfo(context: app.tuxguitar.util.TGContext): String {
		return runCatching { describeLock(context) }.getOrElse { "editor lock: unknown (" + it + ")" }
	}

	private fun describeLock(context: app.tuxguitar.util.TGContext): String {
		val lock = app.tuxguitar.editor.TGEditorManager.getInstance(context).lockControl ?: return "editor lock: none"
		val ownerField = lock.javaClass.getDeclaredField("lockThread").apply { isAccessible = true }
		val countField = lock.javaClass.getDeclaredField("lockCount").apply { isAccessible = true }
		val owner = ownerField.get(lock) ?: return "editor lock: free"
		val text = StringBuilder("editor lock: held ").append(countField.get(lock)).append("x by thread id ").append(owner)
		Thread.getAllStackTraces().entries.firstOrNull { it.key.id == owner }?.let { (thread, frames) ->
			text.append(" \"").append(thread.name).append("\" ").append(thread.state).append('\n')
			frames.take(25).forEach { text.append("      at ").append(it).append('\n') }
		} ?: text.append(" (thread not alive)")
		return text.toString()
	}

	private fun write(context: Context, title: String, throwable: Throwable?) {
		val text = StringBuilder()
		text.append("TuxGuitar diagnostic report\n")
		text.append(title).append('\n')
		text.append("Time: ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append('\n')
		text.append("Device: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
			.append(", Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n\n")
		if (throwable != null) {
			text.append(android.util.Log.getStackTraceString(throwable)).append('\n')
		}
		// main thread first, then the threads that wait or are blocked (lock holders show up among the others)
		val stacks = Thread.getAllStackTraces().entries.sortedWith(compareBy(
			{ if (it.key == Looper.getMainLooper().thread) 0 else 1 },
			{ if (it.key.state == Thread.State.BLOCKED || it.key.state == Thread.State.WAITING) 0 else 1 },
			{ it.key.name },
		))
		for ((thread, frames) in stacks) {
			if (frames.isEmpty()) {
				continue
			}
			text.append("\"").append(thread.name).append("\" ").append(thread.state).append('\n')
			frames.take(MAX_FRAMES).forEach { text.append("    at ").append(it).append('\n') }
			if (frames.size > MAX_FRAMES) {
				text.append("    ... ").append(frames.size - MAX_FRAMES).append(" more\n")
			}
			text.append('\n')
		}
		reportFile(context).writeText(text.toString())
	}
}
