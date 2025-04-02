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
)

data class CaptureTask (
    val capture: CaptureData,
    val task: TaskData
)