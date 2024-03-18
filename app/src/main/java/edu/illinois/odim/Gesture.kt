package edu.illinois.odim

data class Gesture(val centerX: Float, val centerY: Float, val scrollDX: Float, val scrollDY: Float) {
    private val x: Float = centerX
    private val y: Float = centerY
    private val scrollDeltaX: Float = scrollDX
    private val scrollDeltaY: Float = scrollDY

    override fun equals(other: Any?): Boolean {
        val otherGesture = other as Gesture?
        return this.x == otherGesture?.x &&
                this.y == otherGesture.y &&
                this.scrollDeltaX == otherGesture.scrollDeltaX &&
                this.scrollDeltaY == otherGesture.scrollDeltaY
    }

    override fun toString(): String {
        return "$x, $y, $scrollDeltaX, $scrollDeltaY"
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + scrollDeltaX.hashCode()
        result = 31 * result + scrollDeltaY.hashCode()
        return result
    }
}