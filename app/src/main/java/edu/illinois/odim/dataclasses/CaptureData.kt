package edu.illinois.odim.dataclasses

data class CaptureData (
    val id: String,
    val appId: String,
    val otp: String,
    val src: String
)

data class TaskData (
    val id: String,
    val os: String,
    val description: String,
    val traceIds: Array<String>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TaskData

        if (id != other.id) return false
        if (os != other.os) return false
        if (description != other.description) return false
        if (!traceIds.contentEquals(other.traceIds)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + os.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + traceIds.contentHashCode()
        return result
    }
}

data class CaptureTask (
    val capture: CaptureData,
    val task: TaskData
)