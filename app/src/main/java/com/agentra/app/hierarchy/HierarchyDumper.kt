package com.agentra.app.hierarchy

import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Rect
import java.util.Locale

/**
 * Walks the AccessibilityNodeInfo tree and produces a structured text dump
 * of the UI hierarchy — Android's built-in equivalent of uiautomator2.dump_hierarchy().
 *
 * Each node includes:
 *   text, contentDescription, className, packageName, clickable, editable,
 *   bounds, checked, enabled, and children recursively.
 */
object HierarchyDumper {

    private const val MAX_DEPTH = 10
    private const val MAX_SIBLINGS = 40
    private const val MAX_TOTAL_NODES = 200

    /**
     * Dumps the full accessibility tree rooted at [root].
     * Returns a structured indented string.
     */
    fun dump(root: AccessibilityNodeInfo?): String {
        if (root == null) return "[No accessibility root available]"

        val sb = StringBuilder()
        var nodeCount = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (nodeCount >= MAX_TOTAL_NODES) return
            if (depth > MAX_DEPTH) return

            // Indentation
            val indent = buildString { repeat(depth * 2) { append(' ') } }
            val prefix = if (depth == 0) "├─ " else "│ ".repeat(depth - 1) + "├─ "

            // Node label
            val label = buildNodeLabel(node)
            sb.append(indent)
            if (depth > 0) sb.append(prefix)
            sb.appendLine(label)

            nodeCount++

            // Children
            val childCount = minOf(node.childCount, MAX_SIBLINGS)
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    walk(child, depth + 1)
                } finally {
                    // Avoid leaking nodes
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
        }

        try {
            walk(root, 0)
        } catch (e: Exception) {
            sb.appendLine("[Error during hierarchy walk: ${e.message}]")
        }

        // Trim trailing whitespace lines
        return sb.toString().trimEnd()
                .ifEmpty { "[Empty hierarchy]" }
    }

    /**
     * Dumps only the interactive elements (clickable, focusable, editable, checked, etc.)
     * - more concise version for LLM consumption.
     */
    fun dumpInteractive(root: AccessibilityNodeInfo?): String {
        if (root == null) return "[No accessibility root available]"

        val sb = StringBuilder()
        var nodeCount = 0

        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (nodeCount >= MAX_TOTAL_NODES) return
            if (depth > MAX_DEPTH) return

            val isInteractive = node.isClickable || node.isFocusable || node.isEditable ||
                    node.isCheckable || node.isScrollable || node.isChecked ||
                    !node.text.isNullOrBlank() ||
                    !node.contentDescription.isNullOrBlank()

            if (isInteractive) {
                val indent = buildString { repeat(depth * 2) { append(' ') } }
                val prefix = if (depth == 0) "" else "│ ".repeat(depth - 1) + "├─ "
                sb.append(indent)
                if (depth > 0) sb.append(prefix)
                sb.appendLine(buildNodeLabel(node))
                nodeCount++
            }

            val childCount = minOf(node.childCount, MAX_SIBLINGS)
            for (i in 0 until childCount) {
                val child = node.getChild(i) ?: continue
                try {
                    walk(child, if (isInteractive) depth + 1 else depth)
                } finally {
                    try { child.recycle() } catch (_: Exception) {}
                }
            }
        }

        try {
            walk(root, 0)
        } catch (e: Exception) {
            sb.appendLine("[Error: ${e.message}]")
        }

        return sb.toString().trimEnd().ifEmpty { "[No interactive elements found]" }
    }

    /**
     * Finds an element by text or content description and returns its center coordinates.
     */
    fun findNodeByText(root: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        if (root == null) return null
        return try {
            val lowerQuery = query.lowercase(Locale.ROOT)
            var found: AccessibilityNodeInfo? = null

            fun search(node: AccessibilityNodeInfo) {
                if (found != null) return
                val text = node.text?.toString()?.lowercase(Locale.ROOT) ?: ""
                val desc = node.contentDescription?.toString()?.lowercase(Locale.ROOT) ?: ""
                if (text.contains(lowerQuery) || desc.contains(lowerQuery)) {
                    found = node
                    return
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    search(child)
                    child.recycle()
                }
            }

            search(root)
            found
        } catch (e: Exception) {
            null
        }
    }

    // ─── Private helpers ───

    private fun buildNodeLabel(node: AccessibilityNodeInfo): String {
        val parts = mutableListOf<String>()

        // Class name (simplified)
        val className = node.className?.toString()?.substringAfterLast('.') ?: "Unknown"
        parts.add(className)

        // Text
        val text = node.text?.toString()?.take(80)
        if (!text.isNullOrBlank()) parts.add("text=\"$text\"")

        // Content description
        val desc = node.contentDescription?.toString()?.take(80)
        if (!desc.isNullOrBlank()) parts.add("desc=\"$desc\"")

        // Package
        val pkg = node.packageName?.toString()
        if (!pkg.isNullOrBlank()) parts.add("pkg=$pkg")

        // Bounds
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (!bounds.isEmpty) {
            parts.add("bounds=[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
        }

        // Boolean flags
        if (node.isClickable) parts.add("clickable")
        if (node.isFocusable) parts.add("focusable")
        if (node.isEditable) parts.add("editable")
        if (node.isChecked) parts.add("checked")
        if (node.isCheckable) parts.add("checkable")
        if (node.isSelected) parts.add("selected")
        if (node.isScrollable) parts.add("scrollable")
        if (node.isEnabled) parts.add("enabled")
        if (node.isPassword) parts.add("password")
        if (node.isLongClickable) parts.add("longClickable")

        return parts.joinToString(" | ")
    }
}
