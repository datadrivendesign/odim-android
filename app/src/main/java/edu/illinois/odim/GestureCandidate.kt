package edu.illinois.odim

import android.graphics.Rect

data class GestureCandidate(val rect: Rect, val viewId: String) {

    override fun toString(): String {
        return "${rect.left}, ${rect.top}, ${rect.right}, ${rect.bottom}, $viewId"
    }

    override fun equals(other: Any?): Boolean {
        val otherCandidate = other as GestureCandidate?
        return this.rect.left == otherCandidate?.rect?.left &&
                this.rect.top == otherCandidate.rect.top &&
                this.rect.right == otherCandidate.rect.right &&
                this.rect.bottom == otherCandidate.rect.bottom &&
                this.viewId == otherCandidate.viewId
    }

    override fun hashCode(): Int {
        var result = viewId.hashCode()
        result = 31 * result + rect.left
        result = 31 * result + rect.top
        result = 31 * result + rect.right
        result = 31 * result + rect.bottom
        return result
    }
}
