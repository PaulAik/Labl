package com.paulaik.labl

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.paulaik.labl.ui.screens.CameraScreen
import com.paulaik.labl.ui.theme.LablTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LablTheme {
                CameraScreen()
            }
        }
    }
}
