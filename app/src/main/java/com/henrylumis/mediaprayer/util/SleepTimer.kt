package com.henrylumis.mediaprayer.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings

/** Service-owned sleep timer using a monotonic deadline. */
class SleepTimer(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val handler = Handler(Looper.getMainLooper())
    private var deadlineElapsedMs = 0L
    private var selectedDurationMs = 0L
    private var expiryPending = false
    private var ticker: Runnable? = null

    var onFire: (() -> Unit)? = null
    var onChanged: (() -> Unit)? = null

    val remainingMs: Long
        get() = if (deadlineElapsedMs > 0L) (deadlineElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L) else 0L
    val durationMs: Long get() = selectedDurationMs

    init { restore() }

    fun start(durationMs: Long) {
        require(durationMs in MIN_DURATION_MS..MAX_DURATION_MS) {
            "Sleep timer duration must be between 1 minute and 23 hours 59 minutes"
        }
        stopTicker()
        selectedDurationMs = durationMs
        expiryPending = false
        deadlineElapsedMs = SystemClock.elapsedRealtime() + durationMs
        persistSafely()
        scheduleTick()
        onChanged?.invoke()
    }

    fun cancel() {
        // Do not report cancellation until the durable deadline has actually
        // been removed. If storage fails, leave the live countdown running so
        // a failed Cancel cannot silently turn into a timer that reappears
        // after the service/process is recreated.
        if (!clearPersistedState()) {
            throw IllegalStateException("Could not save sleep timer cancellation")
        }
        stopTicker()
        deadlineElapsedMs = 0L
        selectedDurationMs = 0L
        onChanged?.invoke()
    }

    fun isRunning(): Boolean = expiryPending || remainingMs > 0L

    /** Stop callbacks without cancelling the persisted same-boot deadline. */
    fun dispose() = stopTicker()

    private fun restore() {
        val savedBoot = prefs.getInt(KEY_BOOT, INVALID_BOOT)
        val currentBoot = currentBootCount()
        val savedDeadline = prefs.getLong(KEY_DEADLINE, 0L)
        val savedDuration = prefs.getLong(KEY_DURATION, 0L)
        val savedExpiryPending = prefs.getBoolean(KEY_EXPIRY_PENDING, false)

        // An unavailable boot count is NOT proof that this is the same boot.
        // Never compare -1 == -1: elapsedRealtime resets after reboot.
        if (savedBoot < 0 || currentBoot < 0 || savedBoot != currentBoot ||
            savedDeadline <= 0L || savedDuration !in MIN_DURATION_MS..MAX_DURATION_MS) {
            clearPersistedState()
            return
        }

        deadlineElapsedMs = savedDeadline
        selectedDurationMs = savedDuration
        expiryPending = savedExpiryPending
        if (expiryPending) {
            // The deadline was reached and durably marked, but the process may
            // have died before the playback pause callback completed. Retry it.
            scheduleTick()
        } else if (remainingMs <= 0L) {
            // A deadline discovered only after service downtime is not replayed:
            // no exact-deadline callback could have run while the service was absent.
            clearExpired()
        } else {
            scheduleTick()
        }
    }

    /** Synchronous commit is intentional: this is a tiny state record, and
     * returning from Start means the deadline has actually reached disk. */
    private fun persistSafely() {
        val boot = currentBootCount()
        val editor = prefs.edit().remove(KEY_EXPIRY_PENDING)
        if (boot < 0) {
            // Keep the timer active for this service lifetime, but do not write
            // an unverifiable monotonic deadline that could survive a reboot.
            if (!editor.remove(KEY_DEADLINE).remove(KEY_BOOT).remove(KEY_DURATION).commit()) {
                deadlineElapsedMs = 0L
                selectedDurationMs = 0L
                throw IllegalStateException("Could not clear an unverifiable sleep timer")
            }
            return
        }
        val saved = editor.putLong(KEY_DEADLINE, deadlineElapsedMs)
            .putInt(KEY_BOOT, boot)
            .putLong(KEY_DURATION, selectedDurationMs)
            .commit()
        if (!saved) {
            deadlineElapsedMs = 0L
            selectedDurationMs = 0L
            clearPersistedState()
            throw IllegalStateException("Could not save sleep timer")
        }
    }

    private fun scheduleTick() {
        stopTicker()
        val task = object : Runnable {
            override fun run() {
                if (expiryPending || remainingMs <= 0L) {
                    fireAndClear()
                    onChanged?.invoke()
                    return
                }
                onChanged?.invoke()
                handler.postDelayed(this, TICK_MS)
            }
        }
        ticker = task
        handler.post(task)
    }

    private fun fireAndClear() {
        stopTicker()
        if (!expiryPending) {
            // Persist the fact that expiry reached its action phase before
            // invoking playback. If the process dies between these operations,
            // a same-boot service recreation retries the pause callback.
            expiryPending = prefs.edit().putBoolean(KEY_EXPIRY_PENDING, true).commit()
        }
        onFire?.invoke()
        deadlineElapsedMs = 0L
        selectedDurationMs = 0L
        expiryPending = false
        // If clearing fails, the persisted pending marker is intentionally
        // retained. Replaying a pause after recreation is safe and preferable
        // to silently losing an expiry action.
        clearPersistedState()
    }

    private fun clearExpired() {
        stopTicker()
        deadlineElapsedMs = 0L
        selectedDurationMs = 0L
        expiryPending = false
        clearPersistedState()
    }

    private fun stopTicker() {
        ticker?.let(handler::removeCallbacks)
        ticker = null
    }

    private fun clearPersistedState(): Boolean =
        prefs.edit().remove(KEY_DEADLINE).remove(KEY_BOOT).remove(KEY_DURATION)
            .remove(KEY_EXPIRY_PENDING).commit()

    private fun currentBootCount(): Int = try {
        Settings.Global.getInt(appContext.contentResolver, Settings.Global.BOOT_COUNT, INVALID_BOOT)
            .takeIf { it >= 0 } ?: INVALID_BOOT
    } catch (_: Exception) { INVALID_BOOT }

    companion object {
        private const val PREFS = "rvh_sleep_timer"
        private const val KEY_DEADLINE = "deadline_elapsed"
        private const val KEY_BOOT = "boot_count"
        private const val KEY_DURATION = "duration_ms_v41"
        private const val KEY_EXPIRY_PENDING = "expiry_action_pending_v42"
        private const val INVALID_BOOT = -1
        private const val TICK_MS = 1000L
        private const val MIN_DURATION_MS = 60_000L
        private const val MAX_DURATION_MS = 23L * 60L * 60_000L + 59L * 60_000L
    }
}
