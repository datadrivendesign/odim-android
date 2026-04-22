package edu.illinois.odim.dataclasses

data class Gesture(
    var centerX: Float,
    var centerY: Float,
    var scrollDX: Float,
    var scrollDY: Float,
    var viewId: String?,
    var actionType: String = "click",
    var text: String? = null
) {
    var className: String? = null
    var verified: Boolean = false

    constructor(eventClassName: String) : this(
        centerX = -1F,
        centerY = -1F,
        scrollDX = -1F,
        scrollDY = -1F,
        viewId = null,
        actionType = "unknown"
    ) {
        className = eventClassName
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Gesture) return false
        return this.centerX == other.centerX &&
                this.centerY == other.centerY &&
                this.scrollDX == other.scrollDX &&
                this.scrollDY == other.scrollDY &&
                this.viewId == other.viewId &&
                this.actionType == other.actionType &&
                this.text == other.text
    }

    override fun toString(): String {
        return "$actionType: $centerX, $centerY, $scrollDX, $scrollDY, $viewId, $text, $className, $verified"
    }

    override fun hashCode(): Int {
        var result = centerX.hashCode()
        result = 31 * result + centerY.hashCode()
        result = 31 * result + scrollDX.hashCode()
        result = 31 * result + scrollDY.hashCode()
        result = 31 * result + (viewId?.hashCode() ?: 0)
        result = 31 * result + actionType.hashCode()
        result = 31 * result + (text?.hashCode() ?: 0)
        result = 31 * result + (className?.hashCode() ?: 0)
        return result
    }
}
