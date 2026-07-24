package com.agentra.app.ui

import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.agentra.app.R
import com.agentra.app.databinding.ActivityTestToolsBinding
import com.agentra.app.tools.ToolRegistry
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TestToolsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTestToolsBinding
    private lateinit var registry: ToolRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTestToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        registry = ToolRegistry(this)

        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.btnResetAll.setOnClickListener {
            registry.resetAll()
            rebuildToolList()
            Toast.makeText(this, R.string.tools_reset, Toast.LENGTH_SHORT).show()
        }

        rebuildToolList()
    }

    private fun rebuildToolList() {
        binding.layoutToolList.removeAllViews()
        val tools = registry.getAllTools()
        val defaultResultColor = ContextCompat.getColor(this, R.color.on_surface_variant)
        val successColor = ContextCompat.getColor(this, R.color.status_success)
        val errorColor = ContextCompat.getColor(this, R.color.status_error)

        for (tool in tools) {
            val card = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }
                radius = 20f
                cardElevation = 0f
                setCardBackgroundColor(ContextCompat.getColor(this@TestToolsActivity, R.color.surface))
            }

            val innerLayout = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding(20, 16, 20, 16)
            }

            // Row 1: Name + Toggle
            val topRow = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val nameText = android.widget.TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                text = tool.name
                setTextColor(ContextCompat.getColor(this@TestToolsActivity, R.color.on_surface))
                textSize = 15f
            }

            val toggleSwitch = MaterialSwitch(this).apply {
                isChecked = tool.isEnabled
                tag = tool.id
            }

            toggleSwitch.setOnCheckedChangeListener { _, isChecked ->
                registry.setEnabled(tool.id, isChecked)
                // Optionally update appearance
            }

            topRow.addView(nameText)
            topRow.addView(toggleSwitch)
            innerLayout.addView(topRow)

            // Description
            val descText = android.widget.TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 4, 0, 0) }
                text = tool.description
                setTextColor(ContextCompat.getColor(this@TestToolsActivity, R.color.on_surface_variant))
                textSize = 12f
            }
            innerLayout.addView(descText)

            // Row 2: Test button + result
            val bottomRow = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 12, 0, 0) }
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val resultText = android.widget.TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                ).apply { setMargins(0, 0, 8, 0) }
                text = ""
                textSize = 12f
            }

            val testButton = MaterialButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = getString(R.string.test_tool)
                textSize = 12f
                tag = tool.id
                isAllCaps = false
                cornerRadius = 16
            }

            testButton.setOnClickListener {
                val btn = it as MaterialButton
                val toolId = btn.tag as String
                btn.isEnabled = false
                btn.text = getString(R.string.testing)
                resultText.text = ""
                resultText.setTextColor(defaultResultColor)

                CoroutineScope(Dispatchers.Main).launch {
                    val shellResult = withContext(Dispatchers.IO) {
                        registry.testTool(toolId)
                    }
                    resultText.text = if (shellResult.success) {
                        getString(R.string.test_success)
                    } else {
                        getString(R.string.test_failed, shellResult.output.take(120))
                    }
                    resultText.setTextColor(
                        if (shellResult.success) successColor else errorColor
                    )
                    btn.isEnabled = true
                    btn.text = getString(R.string.test_tool)

                    // Also show a Toast with the full output
                    Toast.makeText(
                        this@TestToolsActivity,
                        shellResult.output.take(200),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            bottomRow.addView(resultText)
            bottomRow.addView(testButton)
            innerLayout.addView(bottomRow)

            card.addView(innerLayout)
            binding.layoutToolList.addView(card)
        }
    }
}
