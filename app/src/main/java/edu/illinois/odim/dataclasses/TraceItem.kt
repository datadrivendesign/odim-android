package edu.illinois.odim.dataclasses

data class TraceItem(
    var traceLabel: String,
    val traceTask: String,
    val numEvents: Int,
    var isSelected: Boolean = false
)