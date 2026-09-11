package com.kunalan.emojikitchen

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.kunalan.emojikitchen.core.Kitchen
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.theme.KitchenTheme

class MainActivity : ComponentActivity() {
    private val setUpKeyboard: () -> Unit = { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val kitchen = Kitchen.get(this)
        setContent {
            KitchenTheme {
                if (kitchen == null) PackMissing()
                else CompositionLocalProvider(LocalKitchen provides kitchen) { KitchenScreen(setUpKeyboard) }
            }
        }
    }
}
