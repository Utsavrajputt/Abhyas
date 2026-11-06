package com.invictus.abhyas

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.invictus.abhyas.ui.AbhyasRoot
import com.invictus.abhyas.ui.theme.AbhyasTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as AbhyasApp
        setContent {
            AbhyasTheme {
                AbhyasRoot(database = app.database)
            }
        }
    }
}
