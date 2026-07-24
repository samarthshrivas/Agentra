package com.agentra.app.tools

/**
 * Defines a single agent tool that can be toggled on/off and tested in isolation.
 *
 * @param id Unique identifier for the tool (matches ActionType enum name)
 * @param name Human-readable display name
 * @param description Short explanation of what the tool does
 * @param testParams Default parameters used when the user presses "Test"
 * @param isEnabled Whether this tool is currently enabled
 */
data class ToolDefinition(
    val id: String,
    val name: String,
    val description: String,
    val testParams: Map<String, Any?> = emptyMap(),
    val isEnabled: Boolean = true
)
