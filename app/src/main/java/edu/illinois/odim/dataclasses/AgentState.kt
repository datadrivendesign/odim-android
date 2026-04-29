package edu.illinois.odim.dataclasses

import android.graphics.Bitmap
import com.fasterxml.jackson.databind.JsonNode

data class AgentState(
    val screenshot: Bitmap,
    val vh: JsonNode,
    val packageName: String
)
