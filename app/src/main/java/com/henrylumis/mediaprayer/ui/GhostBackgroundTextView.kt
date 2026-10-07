package com.henrylumis.mediaprayer.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

/**
 * Quiet decorative metadata layer for Now Playing.
 *
 * The cinematic wallpaper is intentionally removed from that screen. This layer
 * preserves a little visual depth by repeating the current track metadata at
 * very low opacity, without becoming interactive or competing with playback UI.
 */
class GhostBackgroundTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textSize = 18f * resources.displayMetrics.scaledDensity
        alpha = 24
    }

    private var title = "NOTHING PLAYING"
    private var artist = "<UNKNOWN>"

    fun setTrackText(title: String?, artist: String?) {
        this.title = title?.takeIf { it.isNotBlank() } ?: "NOTHING PLAYING"
        this.artist = artist?.takeIf { it.isNotBlank() } ?: "<UNKNOWN>"
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return

        val rowStep = max(82f * resources.displayMetrics.density, 72f)
        val left = -width * 0.05f
        var y = 92f * resources.displayMetrics.density
        var row = 0

        // Keep the text sparse and ghost-like. It is deliberately behind every
        // real control and never forms a second readable information panel.
        while (y < height + rowStep) {
            val shift = if (row % 2 == 0) 0f else width * 0.24f
            paint.textSize = (16f + (row % 3) * 2f) * resources.displayMetrics.scaledDensity
            paint.alpha = if (row % 3 == 0) 26 else 20

            canvas.drawText(title, left + shift, y, paint)
            paint.alpha = 15
            canvas.drawText(artist, left + shift + width * 0.02f, y + paint.textSize + 7f, paint)

            y += rowStep
            row++
        }

        // A second, very faint horizontal layer adds the subtle floating-text
        // feel seen in premium music-player backgrounds without restoring artwork.
        paint.textSize = 13f * resources.displayMetrics.scaledDensity
        paint.alpha = 13
        var x = width * 0.55f
        var t = 0
        while (x < width + 220f) {
            canvas.drawText("RVH MUSIC  •  ${if (t % 2 == 0) "NOW PLAYING" else "AUDIO"}", x, height * 0.36f + (t % 3) * 34f, paint)
            x += width * 0.72f
            t++
        }
    }
}
