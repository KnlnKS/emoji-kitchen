package com.kunalan.emojikitchen.keyboard

import android.content.ClipDescription
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.kunalan.emojikitchen.core.Kitchen
import com.kunalan.emojikitchen.core.copyToClipboard
import com.kunalan.emojikitchen.core.pngUri
import kotlinx.coroutines.launch

class KitchenIme : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    internal val state: KeyboardState? by lazy(LazyThreadSafetyMode.NONE) {
        Kitchen.get(this)?.let { k -> KeyboardState(k, lifecycleScope) { send(k, it) } }
    }
    internal var acceptsImages by mutableStateOf(true)
        private set
    internal var canSwitch by mutableStateOf(false)
        private set
    private var backDown = false
    private val inputView by lazy(LazyThreadSafetyMode.NONE) {
        ComposeView(this).apply { setContent { KeyboardRoot(this@KitchenIme) } }
    }

    internal val onGlobe: () -> Unit = { switchToNextInputMethod(false) }

    internal val onGlobeLong: () -> Unit = {
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        state?.prewarm()
    }

    override fun onCreateInputView(): View {
        val decor = window.window!!.decorView
        decor.setViewTreeLifecycleOwner(this)
        decor.setViewTreeSavedStateRegistryOwner(this)
        (inputView.parent as? ViewGroup)?.removeView(inputView)
        return inputView
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        acceptsImages = EditorInfoCompat.getContentMimeTypes(info).any {
            ClipDescription.compareMimeTypes(MIME_PNG, it)
        }
        canSwitch = shouldOfferSwitchingToNextInputMethod()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        state?.refresh()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        registry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        super.onDestroy()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK || state?.back() != true) return super.onKeyDown(keyCode, event)
        backDown = true
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK || !backDown) return super.onKeyUp(keyCode, event)
        backDown = false
        return true
    }

    private fun send(kitchen: Kitchen, item: Int) {
        lifecycleScope.launch {
            val uri = kitchen.pngUri(item)
            val ic = currentInputConnection
            val sent = acceptsImages && ic != null && InputConnectionCompat.commitContent(
                ic,
                currentInputEditorInfo,
                InputContentInfoCompat(uri, PNG, null),
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null,
            )
            if (!sent) kitchen.copyToClipboard(item)
            kitchen.recents.push(kitchen.combo(item))
        }
    }

    private companion object {
        const val MIME_PNG = "image/png"
        val PNG = ClipDescription("Emoji", arrayOf(MIME_PNG))
    }
}
