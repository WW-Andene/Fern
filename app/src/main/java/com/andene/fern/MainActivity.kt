package com.andene.fern

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.asPaddingValues
import com.andene.fern.canvas.CanvasState
import com.andene.fern.canvas.DrawingCanvas
import com.andene.fern.canvas.Toolbar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = Color(0xFFF7F5F0)) {
                    val canvasState = remember { CanvasState() }
                    Box(modifier = Modifier.fillMaxSize()) {
                        DrawingCanvas(state = canvasState, modifier = Modifier.fillMaxSize())
                        Toolbar(
                            state = canvasState,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(WindowInsets.systemBars.asPaddingValues())
                                .padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
