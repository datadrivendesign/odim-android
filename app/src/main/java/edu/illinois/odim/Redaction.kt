package edu.illinois.odim

import android.graphics.Rect

class Redaction(coords: Rect, label: String) {
    var rect: Rect? = coords
    var label: String? = label

    override fun toString(): String {
        return "${rect.toString()}, $label"
    }

    override fun equals(other: Any?): Boolean {
        val otherRedaction = other as Redaction?
        return this.rect?.top  == otherRedaction?.rect?.top &&
                this.rect?.left == otherRedaction?.rect?.left &&
                this.rect?.bottom == otherRedaction?.rect?.bottom &&
                this.rect?.right == otherRedaction?.rect?.right &&
                this.label == otherRedaction?.label
    }

    override fun hashCode(): Int {
        var result = rect?.hashCode() ?: 0
        result = 31 * result + (label?.hashCode() ?: 0)
        return result
    }
}