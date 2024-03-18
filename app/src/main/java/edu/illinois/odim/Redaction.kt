package edu.illinois.odim

import android.graphics.Rect

class Redaction(startX: Int, startY: Int, endX: Int, endY: Int, label: String) {
    var startX: Int = startX
    var startY: Int = startY
    var endX: Int = endX
    var endY: Int = endY
    var label: String = label

    constructor(rect: Rect, label: String) : this(rect.left, rect.top, rect.right, rect.bottom, label)

    override fun toString(): String {
        return "$startX, $startY, $endX, $endY, $label"
    }

    override fun equals(other: Any?): Boolean {
        val otherRedaction = other as Redaction?
        return this.startX == otherRedaction?.startX &&
                this.startY == otherRedaction.startY &&
                this.endX == otherRedaction.endX &&
                this.endY == otherRedaction.endY &&
                this.label == otherRedaction.label
    }

    override fun hashCode(): Int {
        var result = label.hashCode()
        result = 31 * result + startX
        result = 31 * result + startY
        result = 31 * result + endX
        result = 31 * result + endY
        return result
    }


}