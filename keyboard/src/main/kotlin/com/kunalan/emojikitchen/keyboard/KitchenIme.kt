package com.kunalan.emojikitchen.keyboard

import android.content.ClipDescription
import android.inputmethodservice.InputMethodService
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.AndroidUiDispatcher
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
import com.kunalan.emojikitchen.core.Combo
import com.kunalan.emojikitchen.core.ComboList
import com.kunalan.emojikitchen.core.Kitchen
import com.kunalan.emojikitchen.core.copyToClipboard
import com.kunalan.emojikitchen.core.pngUri
import kotlinx.coroutines.launch

class KitchenIme : InputMethodService(), LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    internal var kitchen: Kitchen? = null
        private set
    internal lateinit var emojiSections: Sections
        private set
    internal val acceptsImages = mutableStateOf(true)
    internal val canSwitch = mutableStateOf(false)
    internal var starTab by mutableStateOf(false)
        private set
    internal var star by mutableStateOf(StarItems(IntArray(0), IntArray(0)))
        private set
    internal var combo by mutableStateOf<ComboMode?>(null)
        private set
    internal val emojiGrid = LazyGridState()
    internal val comboGrid = LazyGridState()
    internal val starGrid = LazyGridState()
    internal val pulse = Animatable(1f)
    internal var pulseItem by mutableIntStateOf(-1)
        private set
    private var backDown = false
    private val inputView by lazy(LazyThreadSafetyMode.NONE) {
        ComposeView(this).apply { setContent { KeyboardRoot(this@KitchenIme) } }
    }

    internal val onStar: () -> Unit = {
        if (!starTab) {
            snapshotStar()
            starTab = true
        }
        combo = null
    }

    internal val onSection: (Int) -> Unit = { s ->
        starTab = false
        val c = combo
        if (c == null) emojiGrid.requestScrollToItem(emojiSections.jump(s))
        else comboGrid.requestScrollToItem(c.sections.jump(s))
    }

    internal val onEmoji: (Int) -> Unit = { e ->
        combo = kitchen!!.comboMode(e)
        comboGrid.requestScrollToItem(0)
    }

    internal val onBack: () -> Unit = { combo = null }

    internal val onSend: (Int) -> Unit = { item ->
        val k = kitchen!!
        pulseItem = item
        lifecycleScope.launch(AndroidUiDispatcher.Main) {
            pulse.snapTo(0.82f)
            pulse.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 900f))
        }
        lifecycleScope.launch {
            val uri = k.pngUri(item)
            val ic = currentInputConnection
            val sent = acceptsImages.value && ic != null && InputConnectionCompat.commitContent(
                ic,
                currentInputEditorInfo,
                InputContentInfoCompat(uri, PNG, null),
                InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
                null,
            )
            if (!sent) k.copyToClipboard(item)
            k.recents.push(k.combo(item))
        }
    }

    internal val onToggle: (Int) -> Unit = { item ->
        val k = kitchen!!
        k.favorites.toggle(k.combo(item))
    }

    internal val onGlobe: () -> Unit = { switchToNextInputMethod(false) }

    internal val onGlobeLong: () -> Unit = {
        getSystemService(InputMethodManager::class.java).showInputMethodPicker()
    }

    override fun onCreate() {
        super.onCreate()
        savedState.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        val k = Kitchen.get(this) ?: return
        kitchen = k
        emojiSections = Sections(k.categoryStart)
        starTab = k.favorites.items.isNotEmpty() || k.recents.items.isNotEmpty()
        prewarm(k, k.favorites)
        prewarm(k, k.recents)
    }

    private fun prewarm(k: Kitchen, list: ComboList) {
        val items = list.items
        for (i in 0 until minOf(PREWARM, items.size)) {
            val item = k.itemOf(items[i])
            if (item >= 0 && k.images.peek(item) == null) lifecycleScope.launch { k.images.load(item) }
        }
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
        acceptsImages.value = EditorInfoCompat.getContentMimeTypes(info).any {
            ClipDescription.compareMimeTypes(MIME_PNG, it)
        }
        canSwitch.value = shouldOfferSwitchingToNextInputMethod()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override fun onWindowShown() {
        super.onWindowShown()
        if (starTab) snapshotStar()
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
        if (keyCode != KeyEvent.KEYCODE_BACK || combo == null) return super.onKeyDown(keyCode, event)
        combo = null
        backDown = true
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_BACK || !backDown) return super.onKeyUp(keyCode, event)
        backDown = false
        return true
    }

    private fun snapshotStar() {
        val k = kitchen ?: return
        val favorites = k.favorites
        star = StarItems(
            k.itemsOf(favorites.items, Int.MAX_VALUE, null),
            k.itemsOf(k.recents.items, RECENTS, favorites),
        )
        starGrid.requestScrollToItem(0)
    }

    private companion object {
        const val MIME_PNG = "image/png"
        const val PREWARM = 15
        const val RECENTS = 20
        val PNG = ClipDescription("Emoji", arrayOf(MIME_PNG))
    }
}

private fun Kitchen.itemOf(c: Combo): Int = item(c.a, c.b)

private fun Kitchen.itemsOf(list: List<Combo>, limit: Int, skip: ComboList?): IntArray {
    val out = IntArray(minOf(limit, list.size))
    var n = 0
    for (c in list) {
        if (n == out.size) break
        if (skip != null && c in skip) continue
        val item = itemOf(c)
        if (item >= 0) out[n++] = item
    }
    return if (n == out.size) out else out.copyOf(n)
}
