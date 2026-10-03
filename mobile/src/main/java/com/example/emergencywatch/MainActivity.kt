package com.example.emergencywatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.emergencywatch.ui.CompanionApp
import com.example.emergencywatch.ui.theme.EmergencyWatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EmergencyWatchTheme {
                CompanionApp()
            }
        }
    }
}
