package com.henrylumis.mediaprayer.ui

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

/**
 * RVH Music atmospheric fluid field.
 *
 * This is intentionally an environment layer, not another visualizer. It sits
 * behind the album artwork and the Aura renderer. Motion is slow and organic;
 * audio amplitude only modulates the field's breathing so the artwork and
 * lyrics remain the visual focus.
 *
 * The implementation uses blurred metaball-like lobes and a few flowing paths
 * rather than a collection of rigid circles. It is deliberately Canvas based
 * so it remains safe on devices where GPU/effect support is limited.
 */
class ShaderBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var amplitude = 0.18f
    private var phase = 0f
    private var lastFrameNs = 0L
    private var colors = intArrayOf(
        0xFF00E5FF.toInt(),
        0xFF7C4DFF.toInt(),
        0xFFFF4081.toInt()
    )

    private val fluidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(92f, BlurMaskFilter.Blur.NORMAL)
    }

    private val ribbonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f
        maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
    }

    fun setAmplitude(value: Float) {
        amplitude = value.coerceIn(0f, 1f)
    }

    fun setPaletteColors(newColors: List<Int>) {
        if (newColors.isNotEmpty()) {
            colors = newColors.take(3).toIntArray()
            if (colors.size < 3) {
                colors = IntArray(3) { colors[it % colors.size] }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameNs = 0L
        postInvalidateOnAnimation()
    }

    override fun onDetachedFromWindow() {
        lastFrameNs = 0L
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val now = System.nanoTime()
        if (lastFrameNs == 0L) lastFrameNs = now
        val dt = ((now - lastFrameNs) / 1_000_000_000f).coerceIn(0f, 0.05f)
        lastFrameNs = now
        phase += dt * 0.34f

        val energy = 0.28f + amplitude * 0.72f
        val minSide = minOf(w, h)

        // Large, overlapping lobes create a soft liquid-light field. Their
        // paths deliberately have different frequencies so the movement never
        // looks like three circles following the same animation curve.
        drawFluidLobe(canvas, w * 0.25f, h * 0.30f, minSide * (0.40f + energy * 0.07f), colors[0], 30,
            phase * 0.82f, 0.80f, 1.20f)
        drawFluidLobe(canvas, w * 0.73f, h * 0.38f, minSide * (0.37f + energy * 0.06f), colors[1], 26,
            phase * 0.63f + 1.7f, 1.12f, 0.72f)
        drawFluidLobe(canvas, w * 0.48f, h * 0.76f, minSide * (0.46f + energy * 0.08f), colors[2], 24,
            phase * 0.51f + 3.1f, 0.66f, 0.91f)

        drawFluidRibbon(canvas, w, h, colors[0], phase * 0.72f, 0.25f, 0.23f, 0.07f)
        drawFluidRibbon(canvas, w, h, colors[1], phase * 0.55f + 2f, 0.71f, 0.19f, -0.05f)
        drawFluidRibbon(canvas, w, h, colors[2], phase * 0.43f + 4f, 0.48f, 0.16f, 0.08f)

        postInvalidateOnAnimation()
    }

    private fun drawFluidLobe(
        canvas: Canvas,
        baseX: Float,
        baseY: Float,
        radius: Float,
        color: Int,
        alpha: Int,
        motion: Float,
        xFactor: Float,
        yFactor: Float
    ) {
        val x = baseX + radius * 0.38f * sin(motion * xFactor)
        val y = baseY + radius * 0.30f * cos(motion * yFactor + 0.8f)
        val breathe = 1f + amplitude * 0.10f + 0.055f * sin(motion * 1.17f)
        fluidPaint.color = color
        fluidPaint.alpha = alpha
        canvas.drawCircle(x, y, radius * breathe, fluidPaint)
    }

    private fun drawFluidRibbon(
        canvas: Canvas,
        w: Float,
        h: Float,
        color: Int,
        motion: Float,
        yPosition: Float,
        amplitudeFactor: Float,
        phaseOffset: Float
    ) {
        val path = Path()
        val points = 28
        val vertical = h * amplitudeFactor
        val baseY = h * yPosition
        for (i in 0..points) {
            val t = i / points.toFloat()
            val x = t * w
            val wave = sin(t * 5.2f + motion) * vertical * 0.15f
            val secondary = sin(t * 10.5f - motion * 0.67f + phaseOffset) * vertical * 0.055f
            val y = baseY + wave + secondary
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        ribbonPaint.color = color
        ribbonPaint.alpha = 18 + (amplitude * 18f).toInt()
        canvas.drawPath(path, ribbonPaint)
    }
}
