package edu.illinois.odim

class Gesture {
    private val x: Float
    private val y: Float
    private val scrollDeltaX: Float
    private val scrollDeltaY: Float

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

    constructor(centerX: Float, centerY: Float, scrollDX: Float, scrollDY: Float) {
        this.x = centerX
        this.y = centerY
        this.scrollDeltaX = scrollDX
        this.scrollDeltaY = scrollDY
    }

    constructor() {
        this.x = 0F
        this.y = 0F
        this.scrollDeltaX = 0F
        this.scrollDeltaY = 0F
    }
}