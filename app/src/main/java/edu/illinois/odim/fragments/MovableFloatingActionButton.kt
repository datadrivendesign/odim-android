package edu.illinois.odim.fragments

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs

class MovableFloatingActionButton : FloatingActionButton, View.OnTouchListener {
    private var downRawX = 0f
    private var downRawY = 0f
    private var dX = 0f
    private var dY = 0f

    constructor(context: Context?) : super(context!!) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context!!,
        attrs,
        defStyleAttr
    ) {
        init()
    }

    private fun init() {
        setOnTouchListener(this)
    }

    interface OnPositionChangeListener {
        fun onPositionChanged(newX: Float, newY: Float)
    }

    private var onPositionChangeListener: OnPositionChangeListener? = null

    fun setOnPositionChangeListener(listener: OnPositionChangeListener) {
        onPositionChangeListener = listener
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        val layoutParams = view.layoutParams as MarginLayoutParams
        val action = motionEvent.action
        return if (action == MotionEvent.ACTION_DOWN) {
            downRawX = motionEvent.rawX
            downRawY = motionEvent.rawY
            dX = view.x - downRawX
            dY = view.y - downRawY
            true // Consumed
        } else if (action == MotionEvent.ACTION_MOVE) {
            val viewWidth: Int = view.width
            val viewHeight: Int = view.height
            val viewParent: View = view.parent as View
            val parentWidth: Int = viewParent.width
            val parentHeight: Int = viewParent.height
            var newX = motionEvent.rawX + dX
            newX = layoutParams.leftMargin.toFloat().coerceAtLeast(newX) // Don't allow the FAB past the left hand side of the parent
            newX = (parentWidth - viewWidth - layoutParams.rightMargin).toFloat().coerceAtMost(newX) // Don't allow the FAB past the right hand side of the parent
            var newY = motionEvent.rawY + dY
            newY = layoutParams.topMargin.toFloat().coerceAtLeast(newY) // Don't allow the FAB past the top of the parent
            newY = (parentHeight - viewHeight - layoutParams.bottomMargin).toFloat().coerceAtMost(newY) // Don't allow the FAB past the bottom of the parent
            view.animate().x(newX).y(newY).setDuration(0).start()
            // Notify listener of position change
            onPositionChangeListener?.onPositionChanged(newX, newY)
            true // Consumed
        } else if (action == MotionEvent.ACTION_UP) {
            val upRawX = motionEvent.rawX
            val upRawY = motionEvent.rawY
            val upDX = upRawX - downRawX
            val upDY = upRawY - downRawY
            if (abs(upDX) < CLICK_DRAG_TOLERANCE && abs(upDY) < CLICK_DRAG_TOLERANCE) { // A click
                performClick()
            } else { // A drag
                true // Consumed
            }
        } else {
            super.onTouchEvent(motionEvent)
        }
    }

    companion object {
        private const val CLICK_DRAG_TOLERANCE =
            10f // Often, there will be a slight, unintentional, drag when the user taps the FAB, so we need to account for this.
    }
}