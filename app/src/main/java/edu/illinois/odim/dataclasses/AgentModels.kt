@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package edu.illinois.odim.dataclasses

import android.graphics.Bitmap
import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.serialization.Serializable

/**
 * Represents the state captured for the agent at a single step.
 */
data class AgentState(
    val screenshot: Bitmap,
    val vh: JsonNode,
    val packageName: String
)

/**
 * A simplified representation of a UI element for the LLM.
 */
@Serializable
data class ActionableElement(
    val index: Int,
    val className: String,
    val text: String,
    val contentDescription: String,
    val resourceId: String?,
    val bounds: RectData,
    val center: PointData,
    val isClickable: Boolean,
    val isScrollable: Boolean,
    val isEditable: Boolean,
    val isFocused: Boolean
)

@Serializable
data class RectData(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

@Serializable
data class PointData(
    val x: Int,
    val y: Int
)

/**
 * Action types supported by the agent.
 */
@Serializable
data class AgentAction(
    @JsonProperty("actionType") @JsonAlias("action_type")
    val actionType: String,
    val index: Int? = null,
    val text: String? = null,
    val direction: String? = null,
    @JsonProperty("goalStatus") @JsonAlias("goal_status")
    val goalStatus: String? = null
)

/**
 * Response from the agent's brain (LLM).
 */
@Serializable
data class AgentResponse(
    val reason: String,
    val reflection: String,
    val action: AgentAction
)

/**
 * Record of a single step in the agent's history.
 */
@Serializable
data class StepRecord(
    val step: Int,
    val action: AgentAction,
    val reason: String,
    val reflection: String? = null
)
