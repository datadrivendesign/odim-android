package edu.illinois.odim.activities

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import edu.illinois.odim.MyAccessibilityService
import edu.illinois.odim.databinding.ActivityAgentBinding
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class AgentActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAgentBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAgentBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnStart.setOnClickListener {
            startAgent()
        }

        binding.btnStop.setOnClickListener {
            stopAgent()
        }

        observeAgentState()
    }

    private fun observeAgentState() {
        lifecycleScope.launch {
            try {
                // Wait for service to be ready or just check if it is
                val service = MyAccessibilityService.instance
                service.agentController.isRunning.collect { isRunning ->
                    Log.i("AgentActivity", "Observing Agent State: $isRunning")
                    updateUi(isRunning)
                }
            } catch (e: Exception) {
                // Service might not be initialized yet
            }
        }
    }

    private fun updateUi(isRunning: Boolean) {
        if (isRunning) {
            binding.btnStart.visibility = View.GONE
            binding.btnStop.visibility = View.VISIBLE
            binding.txtStatus.text = "Status: Running..."
        } else {
            binding.btnStart.visibility = View.VISIBLE
            binding.btnStop.visibility = View.GONE
            binding.txtStatus.text = "Status: Ready"
        }
    }

    private fun startAgent() {
        val goal = binding.editGoal.text.toString()
        if (goal.isEmpty()) {
            Toast.makeText(this, "Please enter a goal", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val service = MyAccessibilityService.instance
            
            // Set the selected brain
            val useOllama = binding.radioOllama.isChecked
            service.agentController.setBrain(useOllama)

            service.agentController.startAgent(goal)
            // Navigate to home screen
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            startActivity(intent)
        } catch (e: UninitializedPropertyAccessException) {
            Toast.makeText(this, "Accessibility Service is not running", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopAgent() {
        try {
            MyAccessibilityService.instance.agentController.stopAgent()
        } catch (e: Exception) {}
    }
}
