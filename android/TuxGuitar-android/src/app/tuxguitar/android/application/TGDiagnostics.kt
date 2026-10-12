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
	private const val DUMP_MAX_LINES = 150
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

	/** Writes the state of the screen to the log: window focus, action bar, side menu, fragments and the views. */
	@JvmStatic
	fun dumpState(activity: androidx.appcompat.app.AppCompatActivity, reason: String) {
		runCatching {
			val text = StringBuilder("STATE (").append(reason).append(")\n")
			text.append("  window focus=").append(activity.hasWindowFocus())
				.append(" lifecycle=").append(activity.lifecycle.currentState)
				.append(" actionBar showing=").append(activity.supportActionBar?.isShowing).append('\n')
			val root = activity.findViewById<android.view.View>(app.tuxguitar.android.R.id.root_layout)
			(root as? androidx.drawerlayout.widget.DrawerLayout)?.let {
				text.append("  drawer open=").append(it.isDrawerOpen(androidx.core.view.GravityCompat.START))
					.append(" visible=").append(it.isDrawerVisible(androidx.core.view.GravityCompat.START))
					.append(" lock=").append(it.getDrawerLockMode(androidx.core.view.GravityCompat.START)).append('\n')
			}
			activity.supportFragmentManager.fragments.forEach { f ->
				text.append("  fragment ").append(f.javaClass.simpleName)
					.append(" added=").append(f.isAdded).append(" visible=").append(f.isVisible)
					.append(" state=").append(f.lifecycle.currentState)
					.append(" view=").append(if (f.view == null) "none" else if (f.view!!.isAttachedToWindow) "attached" else "detached")
					.append('\n')
			}
			val lines = mutableListOf<String>()
			dumpView(activity.window.decorView, 1, lines)
			lines.take(DUMP_MAX_LINES).forEach { text.append(it).append('\n') }
			log(text.toString())
		}.onFailure { log("dump failed", it) }
	}

	private fun dumpView(view: android.view.View, depth: Int, lines: MutableList<String>) {
		if (lines.size >= DUMP_MAX_LINES || depth > 14) {
			return
		}
		val id = if (view.id != android.view.View.NO_ID) runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() else null
		val visibility = when (view.visibility) {
			android.view.View.VISIBLE -> "V"
			android.view.View.INVISIBLE -> "I"
			else -> "G"
		}
		val line = StringBuilder("  ".repeat(depth)).append(view.javaClass.simpleName)
		if (id != null) line.append('#').append(id)
		line.append(' ').append(visibility).append(' ').append(view.width).append('x').append(view.height)
			.append(" @").append(view.left).append(',').append(view.top)
		if (view.alpha < 1f) line.append(" alpha=").append(view.alpha)
		if (view.translationY != 0f) line.append(" ty=").append(view.translationY)
		if (view is androidx.compose.ui.platform.AbstractComposeView) line.append(" composed=").append(view.hasComposition)
		lines.add(line.toString())
		if (view is android.view.ViewGroup && view.visibility == android.view.View.VISIBLE) {
			for (i in 0 until view.childCount) {
				dumpView(view.getChildAt(i), depth + 1, lines)
			}
		}
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
