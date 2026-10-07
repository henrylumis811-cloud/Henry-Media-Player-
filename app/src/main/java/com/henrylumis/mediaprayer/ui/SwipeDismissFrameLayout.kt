package com.henrylumis.mediaprayer.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.SeekBar
import kotlin.math.abs
import kotlin.math.max

/**
 * Small, deliberate in-app back gesture for the persistent fragment shell.
 *
 * Android's system back gesture remains the primary path on gesture-navigation
 * devices. This view supplies the same "swipe back" affordance on devices or
 * configurations where the system gesture is not available: a gesture must
 * begin near the left edge and move to the right. Restricting it to the edge
 * prevents conflicts with RecyclerView horizontal actions and vertical
 * scrolling inside individual screens.
 */
class SwipeDismissFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val edgeWidth = (32f * resources.displayMetrics.density).toInt()
    private val commitDistance = (72f * resources.displayMetrics.density)
    private val commitVelocity = 900f
    private val maxBackScale = 0.965f
    private var enabledForDismiss = false
    private var tracking = false
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var startTranslationX = 0f
    private var dismissListener: (() -> Unit)? = null
    private var velocityTracker: VelocityTracker? = null
    private var gestureStartedOnInteractiveControl = false

    private fun contentView(): View? = if (childCount > 0) getChildAt(childCount - 1) else null

    fun setSwipeDismissEnabled(enabled: Boolean) {
        enabledForDismiss = enabled
        if (!enabled) resetGesture(true)
    }

    fun setOnSwipeDismissListener(listener: (() -> Unit)?) {
        dismissListener = listener
    }

    private fun resetGesture(animateBack: Boolean) {
        tracking = false
        dragging = false
        gestureStartedOnInteractiveControl = false
        velocityTracker?.recycle()
        velocityTracker = null
        val content = contentView() ?: return
        if (animateBack) {
            content.animate()
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(150L)
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                .start()
        } else {
            content.translationX = 0f
            content.scaleX = 1f
            content.scaleY = 1f
            content.alpha = 1f
        }
    }

    private fun findTouchTarget(view: View, x: Float, y: Float): View? {
        if (view.visibility != View.VISIBLE || !view.isShown) return null
        if (x < 0f || y < 0f || x > view.width || y > view.height) return null
        if (view is android.view.ViewGroup) {
            for (index in view.childCount - 1 downTo 0) {
                val child = view.getChildAt(index)
                val hit = findTouchTarget(child, x - child.left, y - child.top)
                if (hit != null) return hit
            }
        }
        return view
    }

    private fun shouldBlockDismissAt(x: Float, y: Float): Boolean {
        var target = contentView()?.let { findTouchTarget(it, x, y) } ?: return false
        // A touch may land on a non-clickable child inside a clickable container
        // (for example an icon/text inside a button). Walk upward so the gesture
        // guard respects the actual interactive control that owns the touch.
        while (true) {
            if (target is SeekBar || target.isClickable || target.isLongClickable || target is EditText) {
                return true
            }
            val parentView = target.parent as? View ?: break
            if (parentView === contentView()) break
            target = parentView
        }
        return false
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (!enabledForDismiss) return super.onInterceptTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Only claim the gesture when it originates at the conventional
                // left back-gesture edge. This is intentionally narrow so Queue
                // swipe-to-remove, horizontal carousels, and normal scrolling
                // remain untouched.
                if (event.x > edgeWidth) return super.onInterceptTouchEvent(event)

                tracking = true
                dragging = false
                downX = event.x
                downY = event.y
                startTranslationX = contentView()?.translationX ?: 0f
                gestureStartedOnInteractiveControl = shouldBlockDismissAt(event.x, event.y)
                velocityTracker?.recycle()
                velocityTracker = VelocityTracker.obtain().also { it.addMovement(event) }
            }

            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                if (!tracking || gestureStartedOnInteractiveControl) return super.onInterceptTouchEvent(event)

                val dx = event.x - downX
                val dy = event.y - downY
                val distance = max(abs(dx), abs(dy))
                if (distance < touchSlop) return false

                // Back is a left-edge -> right gesture. Once vertical intent wins,
                // the child keeps ownership of the gesture.
                if (dx > 0f && abs(dx) > abs(dy) * 1.20f) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }

                resetGesture(false)
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> resetGesture(false)
        }

        return super.onInterceptTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!enabledForDismiss || !dragging) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.x - downX).coerceAtLeast(0f)
                val progress = (dx / width.coerceAtLeast(1)).coerceIn(0f, 1f)
                val content = contentView() ?: return true

                // Keep the screen physically attached to the finger while adding
                // only a very subtle depth cue. The previous screen remains
                // visually stable underneath, matching Android's predictive-back
                // principle without turning the gesture into a carousel.
                content.translationX = startTranslationX + dx
                val scale = 1f - ((1f - maxBackScale) * progress)
                content.scaleX = scale
                content.scaleY = scale
                content.alpha = 1f - (0.035f * progress)
                return true
            }

            MotionEvent.ACTION_UP -> {
                velocityTracker?.addMovement(event)
                velocityTracker?.computeCurrentVelocity(1000)
                val velocityX = velocityTracker?.xVelocity ?: 0f
                val displacement = contentView()?.translationX ?: 0f
                val velocityEnough = velocityX > commitVelocity && displacement > touchSlop * 1.5f
                val distanceEnough = displacement > commitDistance && displacement > width * 0.20f

                tracking = false
                dragging = false
                velocityTracker?.recycle()
                velocityTracker = null

                if (velocityEnough || distanceEnough) {
                    val content = contentView()
                    val remaining = (width - displacement).coerceAtLeast(0f)
                    val duration = (120L + (remaining / width.coerceAtLeast(1) * 80L)).toLong()
                    content?.animate()
                        ?.translationX(width.toFloat())
                        ?.scaleX(maxBackScale)
                        ?.scaleY(maxBackScale)
                        ?.alpha(0.965f)
                        ?.setDuration(duration)
                        ?.setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                        ?.withEndAction { dismissListener?.invoke() }
                        ?.start()
                } else {
                    // Cancellation must restore every property changed during the
                    // interactive gesture, not only horizontal translation.
                    contentView()?.animate()
                        ?.translationX(0f)
                        ?.scaleX(1f)
                        ?.scaleY(1f)
                        ?.alpha(1f)
                        ?.setDuration(180L)
                        ?.setInterpolator(android.view.animation.DecelerateInterpolator(1.6f))
                        ?.start()
                }
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                resetGesture(true)
                return true
            }
        }

        return true
    }
}
