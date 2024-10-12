package edu.illinois.odim.dataclasses

data class Gesture(
    var centerX: Float,
    var centerY: Float,
    var scrollDX: Float,
    var scrollDY: Float,
    var viewId: String?
) {
    var className: String? = null
    var verified: Boolean = false

    constructor(eventClassName: String) : this(centerX=-1F, centerY=-1F, scrollDX=-1F,scrollDY= -1F,viewId= null) {
        className = eventClassName
    }

    override fun equals(other: Any?): Boolean {
        val otherGesture = other as Gesture?
        return this.centerX == otherGesture?.centerX &&
                this.centerY == otherGesture.centerY &&
                this.scrollDX == otherGesture.scrollDX &&
                this.scrollDY == otherGesture.scrollDY &&
                this.viewId == otherGesture.viewId &&
                this.scrollDY == otherGesture.scrollDY
    }

    override fun toString(): String {
        return "$centerX, $centerY, $scrollDX, $scrollDY, $viewId, $className $verified"
    }

    override fun hashCode(): Int {
        var result = centerX.hashCode()
        result = 31 * result + centerY.hashCode()
        result = 31 * result + scrollDX.hashCode()
        result = 31 * result + scrollDY.hashCode()
        result = 31 * result + (viewId?.hashCode() ?: 0)
        result = 31 * result + (className?.hashCode() ?: 0)
        return result
    }


}