package edu.illinois.odim

data class Gesture(val centerX: Float, val centerY: Float, var scrollDX: Float, val scrollDY: Float) {
    private var x: Float = centerX
    private var y: Float = centerY
    private var scrollDeltaX: Float = scrollDX
    private var scrollDeltaY: Float = scrollDY
    var className: String? = null

    constructor(eventClassName: String) : this(-1F, -1F, -1F, -1F) {
        x = -1F
        y = -1F
        scrollDeltaX = -1F
        scrollDeltaY = -1F
        className = eventClassName
    }

    override fun equals(other: Any?): Boolean {
        val otherGesture = other as Gesture?
        return this.x == otherGesture?.x &&
                this.y == otherGesture.y &&
                this.scrollDeltaX == otherGesture.scrollDeltaX &&
                this.scrollDeltaY == otherGesture.scrollDeltaY
    }

    override fun toString(): String {
        return "$x, $y, $scrollDeltaX, $scrollDeltaY, $className"
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + scrollDeltaX.hashCode()
        result = 31 * result + scrollDeltaY.hashCode()
        return result
    }
}