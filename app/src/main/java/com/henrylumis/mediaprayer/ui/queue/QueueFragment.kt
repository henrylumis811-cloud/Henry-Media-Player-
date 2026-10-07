package com.henrylumis.mediaprayer.ui.queue

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar
import com.henrylumis.mediaprayer.MainActivity
import com.henrylumis.mediaprayer.databinding.FragmentQueueBinding

class QueueFragment : Fragment() {
    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!
    private lateinit var adapter: QueueAdapter
    private lateinit var touchHelper: ItemTouchHelper
    private val handler = Handler(Looper.getMainLooper())
    private var refreshRunnable: Runnable? = null
    private var isDragging = false
    private var dragStartPosition = -1
    private var hasPositionedInitial = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val activity = activity as? MainActivity ?: return

        adapter = QueueAdapter(
            onClick = { mediaId -> activity.playQueueMediaId(mediaId) },
            onRemove = { position -> removeWithFeedback(activity, position) },
            onMove = { from, to -> activity.moveQueueItem(from, to) },
            onDragHandleTouch = { handle, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    binding.queueList.findContainingViewHolder(handle)?.let { touchHelper.startDrag(it) }
                }
                true
            }
        )
        binding.queueList.layoutManager = LinearLayoutManager(requireContext())
        binding.queueList.adapter = adapter

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START) {
            override fun isLongPressDragEnabled() = false
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
                adapter.moveLocally(from, to)
                return true
            }
            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
                    isDragging = true
                    dragStartPosition = viewHolder.bindingAdapterPosition
                    viewHolder.itemView.alpha = 0.82f
                }
            }
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1f
                if (isDragging) {
                    val finalPosition = viewHolder.bindingAdapterPosition
                    if (dragStartPosition != -1 && finalPosition != RecyclerView.NO_POSITION && finalPosition != dragStartPosition) {
                        activity.moveQueueItem(dragStartPosition, finalPosition)
                    }
                    isDragging = false
                    dragStartPosition = -1
                    handler.postDelayed({ refreshQueue() }, 250)
                }
            }
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) removeWithFeedback(activity, position)
            }
            override fun onChildDraw(c: Canvas, rv: RecyclerView, vh: RecyclerView.ViewHolder, dX: Float, dY: Float, state: Int, active: Boolean) {
                if (state == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0f) {
                    val density = resources.displayMetrics.density
                    val reveal = (-dX).coerceAtLeast(0f)
                    val right = vh.itemView.right.toFloat()
                    val left = (right - reveal).coerceAtLeast(vh.itemView.left.toFloat())
                    val rect = RectF(left, vh.itemView.top.toFloat(), right, vh.itemView.bottom.toFloat())
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    }
                    c.drawRoundRect(rect, 14f * density, 14f * density, paint)
                    if (reveal > 52f * density) {
                        paint.color = android.graphics.Color.WHITE
                        paint.textAlign = Paint.Align.CENTER
                        paint.textSize = 12f * resources.displayMetrics.scaledDensity
                        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
                        c.drawText("REMOVE", left + reveal / 2f, vh.itemView.top + vh.itemView.height / 2f + 4f * density, paint)
                    }
                }
                super.onChildDraw(c, rv, vh, dX, dY, state, active)
            }
        })
        touchHelper.attachToRecyclerView(binding.queueList)

        binding.btnClearQueue.setOnClickListener {
            if (adapter.itemCount == 0) return@setOnClickListener
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Clear upcoming songs?")
                .setMessage("The currently playing song will stay active. Everything after it will be removed.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear") { _, _ ->
                    activity.clearQueue()
                    handler.postDelayed({ refreshQueue() }, 300)
                    Snackbar.make(binding.root, "Queue cleared", Snackbar.LENGTH_SHORT).show()
                }
                .show()
        }
        binding.btnAddSongs.setOnClickListener {
            activity.openLibraryFromQueue()
        }
        refreshQueue()
        startAutoRefresh()
    }

    private fun removeWithFeedback(activity: MainActivity, position: Int) {
        if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) return
        activity.removeQueueItem(position)
        adapter.removeLocally(position)
        handler.postDelayed({ refreshQueue() }, 300)
        Snackbar.make(binding.root, "Removed from queue", Snackbar.LENGTH_SHORT).show()
    }

    private fun startAutoRefresh() {
        refreshRunnable = object : Runnable {
            override fun run() {
                if (!isDragging) refreshQueue()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(refreshRunnable!!)
    }

    private fun refreshQueue() {
        val activity = activity as? MainActivity ?: return
        if (_binding == null) return
        val queue = activity.getQueue()
        val pendingId = activity.pendingTrack?.mediaId
        val highlightIndex = if (pendingId != null) {
            queue.indexOfFirst { it.mediaId == pendingId }.let { if (it >= 0) it else activity.currentQueueIndex() }
        } else activity.currentQueueIndex()
        adapter.submitList(queue, highlightIndex)
        val upcomingCount = if (highlightIndex >= 0) (queue.size - highlightIndex - 1).coerceAtLeast(0) else queue.size
        binding.queueCount.text = when {
            queue.isEmpty() -> "Nothing queued"
            upcomingCount == 0 -> if (queue.size == 1) "1 song • nothing up next" else "${queue.size} songs • nothing up next"
            else -> "${queue.size} songs • $upcomingCount up next"
        }
        binding.btnClearQueue.visibility = if (queue.isEmpty()) View.INVISIBLE else View.VISIBLE
        binding.queueEmptyState.visibility = if (queue.isEmpty()) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        if (!hasPositionedInitial) {
            hasPositionedInitial = true
            showCurrentTrackImmediately()
        }
    }

    private fun showCurrentTrackImmediately() {
        val activity = activity as? MainActivity ?: return
        val recycler = _binding?.queueList ?: return
        val index = activity.currentQueueIndex()
        if (index < 0 || index >= adapter.itemCount) return
        recycler.post {
            if (_binding == null || !isAdded) return@post
            (recycler.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(index, 0)
        }
    }

    override fun onDestroyView() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        _binding = null
    }

}
