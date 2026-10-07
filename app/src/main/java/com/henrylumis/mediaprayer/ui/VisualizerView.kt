package com.henrylumis.mediaprayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.os.SystemClock
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * RVH Music visual engine.
 *
 * The renderer deliberately does not own playback timing. It consumes the
 * Android audio session only for visualization and remains best-effort: any
 * capture failure falls back to a quiet ambient motion instead of touching
 * the playback engine.
 *
 * Signal pipeline:
 *   FFT -> logarithmic perceptual bands -> attack/release smoothing ->
 *   bass/energy/onset envelope -> Aura renderer.
 *
 * Aura is the single permanent RVH visual language. The analysis pipeline is
 * retained underneath it so its response stays musical and smooth.
 */
class VisualizerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var visualizer: Visualizer? = null
    private var fftBytes: ByteArray? = null
    private var fallbackMode = false
    private var fallbackPhase = 0f

    private var tempoBpm = 120f
    private var tempoConfidence = 0f
    private var lastEnergy = 0f
    private var lastPeakAtMs = 0L
    private val beatIntervalsMs = ArrayDeque<Long>()
    private var adaptiveFluxMean = 0.035f
    private var adaptiveFluxDeviation = 0.02f
    private var beatPulse = 0f
    private var beatPhase = 0f
    private var previousSpectrum = FloatArray(0)
    private var motionPhase = 0f
    private var lastFrameAtMs = 0L

    private var attachedPlayer: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private var isPlaying = true
    private var levelScale = 1f
    private var lowEnergy = 0f
    private var highEnergy = 0f

    private val bandCount = 64
    private val bands = FloatArray(bandCount)
    private val peakBands = FloatArray(bandCount)
    private val targetBands = FloatArray(bandCount)
    private var bass = 0f
    private var mids = 0f
    private var treble = 0f
    private var rms = 0f
    private var onset = 0f
    private var previousBass = 0f
    private var lastOnsetAtMs = 0L

    /** Optional external amplitude listener (0f..1f), used by the shader background. */
    var onAmplitude: ((Float) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    fun attachTo(player: ExoPlayer) {
        if (attachedPlayer === player) {
            setPlaying(player.isPlaying)
            return
        }
        detachPlayerListener()
        attachedPlayer = player
        resetSignalState()

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY && visualizer == null && !fallbackMode) {
                    trySetupVisualizer(player.audioSessionId)
                }
            }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                resetSignalState()
            }
        }
        playerListener = listener
        player.addListener(listener)

        if (player.audioSessionId != androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) {
            trySetupVisualizer(player.audioSessionId)
        }
        setPlaying(player.isPlaying)
    }

    fun setPlaying(playing: Boolean) {
        val wasPlaying = isPlaying
        isPlaying = playing
        if (playing && !wasPlaying) {
            levelScale = 1f
            postInvalidateOnAnimation()
        } else if (!playing && wasPlaying) {
            postInvalidateOnAnimation()
        }
    }

    private fun trySetupVisualizer(sessionId: Int) {
        if (sessionId == 0 || sessionId == androidx.media3.common.C.AUDIO_SESSION_ID_UNSET) return
        try {
            releaseVisualizerOnly()
            val v = Visualizer(sessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                }
                override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                    fftBytes = fft
                    if (isPlaying) updateSignalModel(fft)
                    postInvalidateOnAnimation()
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true)
            v.enabled = true
            visualizer = v
            fallbackMode = false
        } catch (e: Exception) {
            Log.w("VisualizerView", "Real audio capture unavailable; using ambient fallback", e)
            releaseVisualizerOnly()
            fallbackMode = true
        }
    }

    private fun releaseVisualizerOnly() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        } finally {
            visualizer = null
        }
    }

    private fun detachPlayerListener() {
        val player = attachedPlayer
        val listener = playerListener
        if (player != null && listener != null) {
            try { player.removeListener(listener) } catch (_: Exception) { }
        }
        attachedPlayer = null
        playerListener = null
    }

    private fun resetSignalState() {
        tempoBpm = 120f
        tempoConfidence = 0f
        lastEnergy = 0f
        lastPeakAtMs = 0L
        beatIntervalsMs.clear()
        motionPhase = 0f
        lastFrameAtMs = SystemClock.elapsedRealtime()
        fallbackPhase = 0f
        bass = 0f
        mids = 0f
        treble = 0f
        rms = 0f
        onset = 0f
        previousBass = 0f
        lastOnsetAtMs = 0L
        adaptiveFluxMean = 0.035f
        adaptiveFluxDeviation = 0.02f
        beatPulse = 0f
        beatPhase = 0f
        previousSpectrum = FloatArray(0)
        lowEnergy = 0f
        highEnergy = 0f
        bands.fill(0f)
        peakBands.fill(0f)
        targetBands.fill(0f)
        postInvalidateOnAnimation()
    }

    fun release() {
        detachPlayerListener()
        releaseVisualizerOnly()
    }

    /** Builds perceptual bands plus an onset signal from positive spectral flux. */
    private fun updateSignalModel(bytes: ByteArray?) {
        if (bytes == null || bytes.size < 16) return
        try {
            val binCount = bytes.size / 2
            val maxBin = (binCount - 1).coerceAtLeast(2)
            val spectrum = FloatArray(binCount)
            var totalEnergy = 0f
            var flux = 0f
            var bassEnergy = 0f
            var midEnergy = 0f
            var trebleEnergy = 0f

            for (bin in 1..maxBin) {
                val idx = bin * 2
                val re = bytes[idx].toInt()
                val im = bytes[idx + 1].toInt()
                val magnitude = (hypot(re.toDouble(), im.toDouble()).toFloat() / 128f).coerceIn(0f, 1f)
                spectrum[bin] = magnitude
                val previous = if (bin < previousSpectrum.size) previousSpectrum[bin] else 0f
                flux += max(0f, magnitude - previous)
            }

            for (i in 0 until bandCount) {
                val lo = max(1, expBin(i.toFloat() / bandCount, maxBin))
                val hi = max(lo + 1, expBin((i + 1f) / bandCount, maxBin))
                var sum = 0f
                var count = 0
                for (bin in lo..min(hi, maxBin)) {
                    sum += spectrum[bin]
                    count++
                }
                val value = (sum / count.coerceAtLeast(1)).coerceIn(0f, 1f)
                targetBands[i] = sqrt(value)
                totalEnergy += value
                val normalized = (i + 0.5f) / bandCount
                when {
                    normalized < 0.20f -> bassEnergy += value
                    normalized < 0.65f -> midEnergy += value
                    else -> trebleEnergy += value
                }
            }

            for (i in 0 until bandCount) {
                val target = targetBands[i]
                val current = bands[i]
                val coefficient = if (target > current) 0.42f else 0.105f
                bands[i] = current + (target - current) * coefficient
                peakBands[i] = max(bands[i], peakBands[i] - 0.015f)
            }

            val rawBass = (bassEnergy / 13f).coerceIn(0f, 1f)
            val rawMid = (midEnergy / 29f).coerceIn(0f, 1f)
            val rawTreble = (trebleEnergy / 23f).coerceIn(0f, 1f)
            val rawRms = (totalEnergy / bandCount).coerceIn(0f, 1f)
            bass = bass * 0.72f + rawBass * 0.28f
            mids = mids * 0.76f + rawMid * 0.24f
            treble = treble * 0.79f + rawTreble * 0.21f
            rms = rms * 0.80f + rawRms * 0.20f
            lowEnergy = lowEnergy * 0.84f + rawBass * 0.16f
            highEnergy = highEnergy * 0.88f + rawTreble * 0.12f

            // Adaptive spectral-flux threshold: a loud sustained section does not
            // continually trigger Aura, while genuine new transients still punch through.
            val normalizedFlux = (flux / max(1f, maxBin * 0.22f)).coerceIn(0f, 1f)
            adaptiveFluxMean = adaptiveFluxMean * 0.96f + normalizedFlux * 0.04f
            val deviation = abs(normalizedFlux - adaptiveFluxMean)
            adaptiveFluxDeviation = adaptiveFluxDeviation * 0.96f + deviation * 0.04f
            val threshold = adaptiveFluxMean + max(0.012f, adaptiveFluxDeviation * 2.0f)
            val now = SystemClock.elapsedRealtime()
            if (normalizedFlux > threshold && normalizedFlux > 0.018f && now - lastOnsetAtMs > 105L) {
                onset = (normalizedFlux - threshold).coerceAtLeast(0f) * 8f
                onset = onset.coerceIn(0f, 1f)
                beatPulse = max(beatPulse, onset)
                lastOnsetAtMs = now
                updateTempoFromEnergy(normalizedFlux, now)
                beatPhase = 0f
            }
            onset *= 0.84f
            beatPulse *= 0.88f
            previousSpectrum = spectrum
            previousBass = bass
            lastEnergy = lastEnergy * 0.84f + rawRms * 0.16f
        } catch (_: Exception) {
            // Visualization must never affect playback.
        }
    }

    private fun expBin(normalized: Float, maxBin: Int): Int {
        // 30 Hz .. Nyquist-ish visual range, compressed into available FFT bins.
        val minBin = 1f
        val max = maxBin.toFloat().coerceAtLeast(2f)
        return (minBin * (max / minBin).let { ratio -> kotlin.math.exp(ln(ratio) * normalized) })
            .toInt().coerceIn(1, maxBin)
    }

    private fun updateTempoFromEnergy(energy: Float, now: Long) {
        val threshold = (lastEnergy * 1.18f + 0.035f).coerceAtLeast(0.10f)
        val isPeak = energy > threshold && energy > lastEnergy + 0.018f
        if (isPeak && lastPeakAtMs > 0L) {
            val interval = now - lastPeakAtMs
            if (interval in 300L..1000L) {
                beatIntervalsMs.addLast(interval)
                while (beatIntervalsMs.size > 8) beatIntervalsMs.removeFirst()
                val sorted = beatIntervalsMs.toList().sorted()
                val median = sorted[sorted.size / 2].toFloat()
                val detected = (60_000f / median).coerceIn(60f, 180f)
                val weight = if (tempoConfidence < 0.5f) 0.30f else 0.12f
                tempoBpm += (detected - tempoBpm) * weight
                tempoConfidence = (tempoConfidence + 0.10f).coerceAtMost(1f)
            }
        }
        if (isPeak) lastPeakAtMs = now
        lastEnergy = lastEnergy * 0.84f + energy * 0.16f
    }

    private fun advanceMotionClock() {
        val now = SystemClock.elapsedRealtime()
        if (lastFrameAtMs == 0L) lastFrameAtMs = now
        val dt = ((now - lastFrameAtMs).coerceIn(0L, 50L)) / 1000f
        lastFrameAtMs = now
        if (isPlaying) {
            val beatsPerSecond = tempoBpm / 60f
            motionPhase = (motionPhase + dt * beatsPerSecond * (2f * PI.toFloat())) % (2f * PI.toFloat())
            beatPhase = (beatPhase + dt * beatsPerSecond).let { it - kotlin.math.floor(it) }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        if (!isPlaying) {
            levelScale *= 0.88f
            if (levelScale < 0.01f) levelScale = 0f
        }
        advanceMotionClock()

        if (fallbackMode || fftBytes == null) {
            fallbackPhase = motionPhase
            val ambient = (0.10f + 0.08f * (0.5f + 0.5f * sin(fallbackPhase))).coerceIn(0f, 1f)
            bass = bass * 0.92f + ambient * 0.08f
            rms = rms * 0.92f + ambient * 0.08f
        }

        onAmplitude?.invoke(rms.coerceIn(0f, 1f))

        drawAura(canvas, w, h)

        onset *= if (isPlaying) 0.91f else 0.82f
        if (isPlaying || levelScale > 0f) postInvalidateOnAnimation()
    }

    private fun paletteGradient(h: Float): Shader = LinearGradient(
        0f, h, 0f, 0f,
        intArrayOf(0xFF00E5FF.toInt(), 0xFF7C4DFF.toInt(), 0xFFFF4081.toInt()),
        null, Shader.TileMode.CLAMP
    )

    /** Signature RVH mode: the album remains the hero and light breathes around it. */
    private fun drawAura(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val base = min(w, h) * 0.285f
        val pulse = 1f + bass * 0.14f + beatPulse * 0.10f
        val musicalBreath = 0.5f + 0.5f * sin(beatPhase * 2f * PI.toFloat())
        val detail = (mids * 0.65f + treble * 0.35f).coerceIn(0f, 1f)

        paint.style = Paint.Style.FILL
        paint.shader = RadialGradient(
            cx, cy, base * 2.1f * pulse,
            intArrayOf(0x0000E5FF, 0x441A8CFF, 0x2B7C4DFF, 0x00101830),
            floatArrayOf(0f, 0.25f, 0.58f, 1f), Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, base * 2.1f * pulse, paint)

        // A thin spectral halo replaces the old bar fan. It reacts to frequency
        // groups while leaving the artwork's center visually quiet.
        strokePaint.strokeWidth = 2.2f
        strokePaint.shader = paletteGradient(h)
        val inner = base * 1.12f
        val outer = base * (1.18f + bass * 0.13f + beatPulse * 0.08f + musicalBreath * 0.025f)
        canvas.drawCircle(cx, cy, outer, strokePaint)
        strokePaint.shader = null

        val particleCount = 28
        for (i in 0 until particleCount) {
            val angle = i * (2f * PI.toFloat() / particleCount) + motionPhase * 0.035f
            val band = bands[(i * bandCount / particleCount).coerceIn(0, bandCount - 1)]
            val radius = inner + band * min(w, h) * (0.12f + detail * 0.07f) + sin(i * 2.7f + motionPhase) * (1.5f + detail * 2.0f)
            val x = cx + cos(angle) * radius
            val y = cy + sin(angle) * radius
            val size = 1.0f + band * (2.8f + detail * 1.5f) + beatPulse * 1.8f
            paint.shader = RadialGradient(x, y, size * 3f, 0xBBFFFFFF.toInt(), 0x0000E5FF, Shader.TileMode.CLAMP)
            canvas.drawCircle(x, y, size * 2.4f, paint)
        }
        paint.shader = null
    }
}
