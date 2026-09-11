package com.kunalan.emojikitchen

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kunalan.emojikitchen.core.Kitchen
import com.kunalan.emojikitchen.keyboard.KitchenIme
import com.kunalan.emojikitchen.ui.LocalKitchen
import com.kunalan.emojikitchen.ui.theme.KitchenTheme

class MainActivity : ComponentActivity() {
    private var keyboardSetup by mutableStateOf(false)
    private val ime by lazy { ComponentName(this, KitchenIme::class.java) }
    private val inputMethods by lazy { getSystemService(InputMethodManager::class.java) }

    private val setUpKeyboard: () -> Unit = {
        if (imeEnabled()) inputMethods.showInputMethodPicker()
        else startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val kitchen = Kitchen.get(this)
        setContent {
            KitchenTheme {
                if (kitchen == null) PackMissing()
                else CompositionLocalProvider(LocalKitchen provides kitchen) { KitchenScreen(keyboardSetup, setUpKeyboard) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshKeyboardSetup()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshKeyboardSetup()
    }

    private fun refreshKeyboardSetup() {
        val current = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        keyboardSetup = !imeEnabled() || ComponentName.unflattenFromString(current.orEmpty()) != ime
    }

    private fun imeEnabled(): Boolean {
        val id = ime.flattenToShortString()
        return inputMethods.enabledInputMethodList.any { it.id == id }
    }
}
