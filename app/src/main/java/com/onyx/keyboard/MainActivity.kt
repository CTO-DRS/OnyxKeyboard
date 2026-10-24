package com.onyx.keyboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.onyx.keyboard.ui.OnyxRoot
import com.onyx.keyboard.ui.theme.OnyxAppTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        AppGraph.init(this)
        setContent {
            OnyxAppTheme {
                OnyxRoot()
            }
        }
    }
}
