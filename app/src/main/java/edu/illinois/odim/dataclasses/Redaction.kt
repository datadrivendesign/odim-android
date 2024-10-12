package edu.illinois.odim.dataclasses

import android.graphics.Rect

data class Redaction(val startX: Int, val startY: Int, val endX: Int, val endY: Int, var label: String) {

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