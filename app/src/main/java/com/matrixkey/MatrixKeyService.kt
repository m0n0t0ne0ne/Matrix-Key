package com.matrixkey

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.matrixkey.engine.EmojiData
import com.matrixkey.engine.WordEngine
import com.matrixkey.keyboard.Icon
import com.matrixkey.keyboard.KeyType
import com.matrixkey.keyboard.KeyboardView
import com.matrixkey.keyboard.Layouts
import com.matrixkey.keyboard.Mode
import com.matrixkey.keyboard.OverlayView
import com.matrixkey.keyboard.ShiftState
import com.matrixkey.keyboard.TopBarView
import com.matrixkey.net.AiClient
import com.matrixkey.net.GifClient
import com.matrixkey.net.Http
import com.matrixkey.panels.AiPanel
import com.matrixkey.panels.ClipboardPanel
import com.matrixkey.panels.ClipboardStore
import com.matrixkey.panels.EmojiPanel
import com.matrixkey.panels.GifPanel
import com.matrixkey.panels.PanelUi
import com.matrixkey.settings.PermissionActivity
import com.matrixkey.settings.SettingsActivity
import com.matrixkey.theme.FontManager
import com.matrixkey.theme.KeyboardTheme
import com.matrixkey.theme.ThemeManager
import java.io.File
import java.util.concurrent.Executors

class MatrixKeyService : InputMethodService(), KeyboardView.Listener, TopBarView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var engine: WordEngine
    private lateinit var clipStore: ClipboardStore
    private val main = Handler(Looper.getMainLooper())
    private val engineThread = HandlerThread("words").apply { start() }
    private val engineHandler by lazy { Handler(engineThread.looper) }
    private val net = Executors.newFixedThreadPool(4)

    // Views
    private var root: FrameLayout? = null
    private lateinit var bgImage: ImageView
    private lateinit var topBar: TopBarView
    private lateinit var content: FrameLayout
    private lateinit var keyboard: KeyboardView
    private lateinit var overlay: OverlayView
    private var panel: View? = null
    private var gifPanel: GifPanel? = null
    private var aiPanel: AiPanel? = null
    private lateinit var theme: KeyboardTheme

    // Editing state
    private val composing = StringBuilder()
    private var prevWord: String? = null
    private var selStart = 0
    private var selEnd = 0
    private var mode = Mode.ALPHA
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L
    private var lastSpaceTime = 0L
    /** Insert a space before the next word (after glide typing). */
    private var phantomSpace = false
    /** The last word that was replaced by autocorrect, so backspace can undo it. */
    private var lastCorrection: Pair<String, String>? = null
    /** Last glided word and its alternatives. */
    private var lastGlide: String? = null
    private var glideAlternatives: List<String> = emptyList()
    private var suggestionGen = 0
    private var currentSuggestions: List<String> = emptyList()
    private var autoCorrection: String? = null
    private var deleteAnchor = -1
    private var showToolbar = false
    private var needsReload = true

    // Field flags
    private var noLearning = false
    private var noSuggestions = false
    private var isPassword = false

    // Internal text field (GIF search, AI prompt, emoji search)
    private enum class Target { NONE, GIF_SEARCH, AI_PROMPT }
    private var target = Target.NONE
    private val targetText = StringBuilder()
    private var aiPromptAction: AiClient.Action = AiClient.Action.CUSTOM

    private lateinit var voice: VoiceInput
    private var voicePartial = ""

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (prefs.clipboard) {
            (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).let { clipStore.capture(it, prefs.clipHideSensitive) }
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        engine = WordEngine(this)
        engineHandler.post { engine.load() }
        clipStore = ClipboardStore(this)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
        voice = VoiceInput(this, prefs,
            onPartial = { s -> main.post { voicePartial = s; showMessage("Listening… $s") } },
            onFinal = { s -> main.post { voiceDone(s) } },
            onError = { s -> main.post { voiceError(s) } })
    }

    override fun onDestroy() {
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        runCatching { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener) }
        voice.stop()
        engineHandler.post { engine.save() }
        engineThread.quitSafely()
        net.shutdownNow()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        needsReload = true
        if (key == "reset_learned_at") {
            engineHandler.post { engine.load() }
            if (::keyboard.isInitialized) keyboard.clearAim()
            prefs.sp.edit().remove("aim").apply()
        }
        if (key == "dict_changed_at") engineHandler.post { engine.load() }
    }

    override fun onCreateInputView(): View {
        val ctx = this
        overlay = OverlayView(ctx)
        keyboard = KeyboardView(ctx, overlay).also { it.listener = this }
        keyboard.loadAim(prefs.sp.getString("aim", null))
        topBar = TopBarView(ctx).also { it.listener = this }
        content = FrameLayout(ctx)
        content.addView(keyboard, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        bgImage = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP; visibility = View.GONE }

        val column = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            addView(topBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val r = KeyboardRoot(ctx, column)
        r.addView(bgImage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        r.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.setOnApplyWindowInsetsListener { v, insets ->
            val bottom = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                insets.getInsets(WindowInsets.Type.navigationBars()).bottom
            } else {
                @Suppress("DEPRECATION") insets.systemWindowInsetBottom
            }
            v.setPadding(0, 0, 0, bottom)
            insets
        }
        root = r
        needsReload = true
        applySettings()
        return r
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (needsReload) applySettings()
        clipStore.prune(prefs.clipClearMs)
        composing.clear(); prevWord = null; phantomSpace = false; lastCorrection = null; lastGlide = null
        closeTarget(redraw = false)
        hidePanel()
        readField(info)
        selStart = info.initialSelStart; selEnd = info.initialSelEnd
        updateEnterIcon(info)
        setMode(modeFor(info))
        updateShiftFromField()
        refreshSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        voice.stop()
        composing.clear()
        persist()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        persist()
    }

    private fun persist() {
        if (::keyboard.isInitialized) prefs.sp.edit().putString("aim", keyboard.aimJson()).apply()
        engineHandler.post { engine.save() }
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    // ------------------------------------------------------------------
    // Settings → views
    // ------------------------------------------------------------------

    private fun applySettings() {
        if (root == null) return
        needsReload = false
        FontManager.clearCache()
        theme = ThemeManager.resolve(this, prefs)
        val tf = FontManager.keyTypeface(this, prefs.keyFont)
        val dot = FontManager.isDotFont(prefs.keyFont)

        root?.setBackgroundColor(theme.bg)
        val bgPath = theme.backgroundImage
        if (bgPath != null && File(bgPath).exists()) {
            bgImage.setImageBitmap(BitmapFactory.decodeFile(bgPath))
            bgImage.visibility = View.VISIBLE
        } else {
            bgImage.setImageDrawable(null); bgImage.visibility = View.GONE
        }
        @Suppress("DEPRECATION")
        window?.window?.navigationBarColor = theme.bg

        keyboard.theme = theme
        keyboard.typeface = tf
        keyboard.dotStyle = dot
        keyboard.keyShape = prefs.keyShape
        keyboard.showHints = prefs.symbolHints
        keyboard.popupEnabled = prefs.popup
        keyboard.heightScale = prefs.heightScale
        keyboard.oneHanded = prefs.oneHanded
        keyboard.glideEnabled = prefs.glide
        keyboard.trailEnabled = prefs.glideTrail
        keyboard.gestureCursor = prefs.gestureCursor
        keyboard.gestureDelete = prefs.gestureDelete
        keyboard.cursorHaptic = prefs.cursorHaptic
        keyboard.longPressDelay = prefs.longPressDelay
        keyboard.learnAim = prefs.learnAim
        keyboard.feedback.hapticEnabled = prefs.haptic
        keyboard.feedback.strength = prefs.vibrationStrength
        keyboard.feedback.soundEnabled = prefs.sound
        keyboard.feedback.volume = prefs.soundVolume

        overlay.trailColor = when (prefs.trailColor) {
            "text" -> theme.text
            "white" -> android.graphics.Color.WHITE
            else -> theme.trail ?: theme.accent
        }
        overlay.trailDotted = when (prefs.trailStyle) { "dot" -> true; "solid" -> false; else -> dot }
        overlay.trailWidthDp = when (prefs.trailWidth) { "thin" -> 4f; "thick" -> 9f; else -> 6f }

        topBar.theme = theme
        topBar.typeface = tf
        topBar.dotStyle = dot
        topBar.tools = buildList {
            add(TopBarView.Tool.EMOJI)
            if (prefs.gif) add(TopBarView.Tool.GIF)
            add(TopBarView.Tool.CLIPBOARD)
            if (prefs.ai) add(TopBarView.Tool.AI)
            add(TopBarView.Tool.SETTINGS)
            if (prefs.voice) add(TopBarView.Tool.MIC)
        }
        rebuildKeys()
        topBar.invalidate()
    }

    private fun rebuildKeys() {
        if (!::keyboard.isInitialized) return
        keyboard.mode = mode
        keyboard.rows = Layouts.build(mode, Layouts.Options(prefs.layout, prefs.numberRow && mode == Mode.ALPHA, prefs.emojiKey))
        keyboard.shift = shift
        keyboard.relayout()
    }

    private fun setMode(m: Mode) {
        mode = m
        rebuildKeys()
    }

    private fun modeFor(info: EditorInfo): Mode = when (info.inputType and InputType.TYPE_MASK_CLASS) {
        InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE -> Mode.NUMBER
        InputType.TYPE_CLASS_DATETIME -> Mode.SYMBOLS
        else -> Mode.ALPHA
    }

    private fun readField(info: EditorInfo) {
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        isPassword = cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD) ||
            (cls == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD)
        noSuggestions = isPassword || cls != InputType.TYPE_CLASS_TEXT ||
            variation == InputType.TYPE_TEXT_VARIATION_URI || variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val incognitoField = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        noLearning = isPassword || !prefs.learn || (prefs.incognito && incognitoField)
    }

    private fun updateEnterIcon(info: EditorInfo) {
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        keyboard.enterIcon = if (noAction) Icon.ENTER else when (action) {
            EditorInfo.IME_ACTION_SEARCH -> Icon.SEARCH
            EditorInfo.IME_ACTION_SEND -> Icon.SEND
            EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> Icon.NEXT
            EditorInfo.IME_ACTION_DONE -> Icon.DONE
            else -> Icon.ENTER
        }
    }

    // ------------------------------------------------------------------
    // Shift / caps
    // ------------------------------------------------------------------

    private fun setShift(s: ShiftState) {
        shift = s
        if (::keyboard.isInitialized) keyboard.shift = s
    }

    private fun updateShiftFromField() {
        if (shift == ShiftState.LOCKED || mode != Mode.ALPHA) return
        val ic = currentInputConnection ?: return
        val ei = currentInputEditorInfo ?: return
        val caps = prefs.autoCaps && ei.inputType != InputType.TYPE_NULL && composing.isEmpty() &&
            ic.getCursorCapsMode(ei.inputType) != 0
        setShift(if (caps) ShiftState.ON else ShiftState.OFF)
    }

    // ------------------------------------------------------------------
    // KeyboardView.Listener
    // ------------------------------------------------------------------

    override fun onKeysLaidOut(centers: Map<Char, PointF>, keyWidth: Float) {
        engineHandler.post { engine.keyCenters = centers; engine.keyWidth = keyWidth }
    }

    private fun isWordChar(s: String) = s.length == 1 && (s[0].isLetterOrDigit() || s[0] == '\'') ||
        (s.length > 1 && s.all { it.isLetter() })

    override fun onChar(text: String, fromLongPress: Boolean) {
        if (target != Target.NONE) {
            targetText.append(text)
            topBar.searchText = targetText.toString()
            if (shift == ShiftState.ON) setShift(ShiftState.OFF)
            return
        }
        val ic = currentInputConnection ?: return
        lastCorrection = null
        lastGlide = null
        if (isWordChar(text) && !(text[0].isDigit() && composing.isEmpty() && mode != Mode.ALPHA)) {
            if (phantomSpace) { ic.commitText(" ", 1); phantomSpace = false }
            if (noSuggestions) {
                ic.commitText(text, 1)
            } else {
                composing.append(text)
                ic.setComposingText(composing, 1)
            }
            if (shift == ShiftState.ON) setShift(ShiftState.OFF)
            refreshSuggestions()
        } else {
            // Punctuation / symbols end the word.
            val punct = text.length == 1 && text[0] in ".,!?;:"
            if (composing.isNotEmpty()) finishWord(autocorrect = punct)
            if (punct) {
                // "word ." → "word."
                val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
                if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit() && (phantomSpaceWasAuto || justPickedSuggestion)) {
                    ic.deleteSurroundingText(1, 0)
                }
            }
            phantomSpace = false
            justPickedSuggestion = false
            phantomSpaceWasAuto = false
            ic.commitText(text, 1)
            if (mode != Mode.ALPHA && text == "'" ) setMode(Mode.ALPHA)
            updateShiftFromField()
            refreshSuggestions()
        }
    }

    private var justPickedSuggestion = false
    private var phantomSpaceWasAuto = false

    override fun onKey(type: KeyType) {
        when (type) {
            KeyType.SPACE -> onSpace()
            KeyType.BACKSPACE -> onBackspace()
            KeyType.ENTER -> onEnter()
            KeyType.SHIFT -> {
                val now = System.currentTimeMillis()
                val next = when {
                    shift == ShiftState.ON && now - lastShiftTap < 320 -> ShiftState.LOCKED
                    shift == ShiftState.OFF -> ShiftState.ON
                    else -> ShiftState.OFF
                }
                lastShiftTap = now
                setShift(next)
            }
            KeyType.SYMBOLS -> setMode(Mode.SYMBOLS)
            KeyType.MORE_SYMBOLS -> setMode(Mode.MORE_SYMBOLS)
            KeyType.ALPHA -> { setMode(Mode.ALPHA); updateShiftFromField() }
            KeyType.EMOJI -> showEmoji()
            else -> {}
        }
    }

    private fun onSpace() {
        if (target != Target.NONE) { targetText.append(' '); topBar.searchText = targetText.toString(); return }
        val ic = currentInputConnection ?: return
        val now = System.currentTimeMillis()
        if (composing.isNotEmpty()) {
            finishWord(autocorrect = true)
            ic.commitText(" ", 1)
        } else {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (prefs.doubleSpacePeriod && now - lastSpaceTime < 700 && before.length == 2 && before[1] == ' ' &&
                (before[0].isLetterOrDigit() || before[0] in ")\"'")
            ) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
            } else {
                ic.commitText(" ", 1)
            }
            lastCorrection = null
        }
        phantomSpace = false
        lastGlide = null
        lastSpaceTime = now
        if (mode == Mode.SYMBOLS || mode == Mode.MORE_SYMBOLS) setMode(Mode.ALPHA)
        updateShiftFromField()
        refreshSuggestions()
    }

    private fun onBackspace() {
        if (target != Target.NONE) {
            if (targetText.isNotEmpty()) targetText.deleteCharAt(targetText.length - 1)
            topBar.searchText = targetText.toString()
            return
        }
        val ic = currentInputConnection ?: return
        phantomSpace = false
        // Undo the last autocorrection: "teh " + ⌫ → "teh"
        val corr = lastCorrection
        if (corr != null && composing.isEmpty()) {
            val (typed, corrected) = corr
            val before = ic.getTextBeforeCursor(corrected.length + 1, 0)?.toString()
            if (before == "$corrected ") {
                ic.deleteSurroundingText(corrected.length + 1, 0)
                ic.commitText(typed, 1)
                lastCorrection = null
                if (!noLearning) engineHandler.post { engine.learnRejectedCorrection(typed) }
                refreshSuggestions()
                return
            }
            lastCorrection = null
        }
        // Remove a whole glided word in one tap.
        val glided = lastGlide
        if (glided != null && composing.isEmpty()) {
            val before = ic.getTextBeforeCursor(glided.length, 0)?.toString()
            lastGlide = null
            if (before == glided) {
                ic.deleteSurroundingText(glided.length, 0)
                updateShiftFromField(); refreshSuggestions()
                return
            }
        }
        if (composing.isNotEmpty()) {
            composing.deleteCharAt(composing.length - 1)
            if (composing.isEmpty()) ic.commitText("", 1) else ic.setComposingText(composing, 1)
            keyboard.clearWordTaps()
        } else if (selEnd > selStart) {
            ic.commitText("", 1)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DEL))
        }
        updateShiftFromField()
        refreshSuggestions()
    }

    private fun onEnter() {
        if (target != Target.NONE) { onSearchSubmit(); return }
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) finishWord(autocorrect = true)
        phantomSpace = false
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            (ei.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
        prevWord = null
        updateShiftFromField()
        refreshSuggestions()
    }

    /** Commits the word being typed, applying autocorrect when appropriate. */
    private fun finishWord(autocorrect: Boolean) {
        val ic = currentInputConnection ?: return
        val typed = composing.toString()
        if (typed.isEmpty()) return
        val correction = autoCorrection
        val final = if (autocorrect && prefs.autocorrect && !noSuggestions && correction != null &&
            !correction.equals(typed, ignoreCase = false) && engine.ready
        ) correction else typed
        ic.commitText(final, 1)
        val corrected = final != typed
        lastCorrection = if (corrected) typed to final else null
        keyboard.commitAim(accepted = !corrected && engine.isKnown(typed))
        learn(final, if (corrected) typed else null)
        composing.clear()
        autoCorrection = null
    }

    private fun learn(word: String, typo: String?) {
        if (noLearning) { prevWord = word; return }
        val prev = prevWord
        engineHandler.post {
            engine.learnWord(word, prev)
            if (typo != null) engine.learnCorrection(typo, word)
        }
        prevWord = word
    }

    override fun onGlide(path: List<PointF>) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) finishWord(autocorrect = false)
        val prev = prevWord
        val caps = shift
        engineHandler.post {
            val words = engine.decodeGlide(path, prev, prefs.blockOffensive)
            main.post {
                if (words.isEmpty()) return@post
                var w = words.first()
                if (caps == ShiftState.LOCKED) w = w.uppercase() else if (caps == ShiftState.ON) w = w.replaceFirstChar { it.uppercase() }
                val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                if (phantomSpace || (before.isNotEmpty() && !before[0].isWhitespace() && before[0] !in "([{\"'")) ic.commitText(" ", 1)
                ic.commitText(w, 1)
                phantomSpace = true
                phantomSpaceWasAuto = true
                lastGlide = w
                lastCorrection = null
                glideAlternatives = words.drop(1).map { alt ->
                    if (caps == ShiftState.LOCKED) alt.uppercase() else if (caps == ShiftState.ON) alt.replaceFirstChar { it.uppercase() } else alt
                }
                if (shift == ShiftState.ON) setShift(ShiftState.OFF)
                learn(w, null)
                showGlideAlternatives(w)
            }
        }
    }

    private fun showGlideAlternatives(chosen: String) {
        val alts = glideAlternatives
        currentSuggestions = listOf(alts.getOrElse(0) { "" }, chosen, alts.getOrElse(1) { "" })
        topBar.suggestions = currentSuggestions
        topBar.emphasized = 1
        topBar.mode = TopBarView.BarMode.SUGGESTIONS
    }

    override fun onCursorStep(forward: Boolean): Boolean {
        val ic = currentInputConnection ?: return false
        if (composing.isNotEmpty()) {
            ic.finishComposingText()
            learn(composing.toString(), null)
            composing.clear()
            refreshSuggestions()
        }
        phantomSpace = false; lastGlide = null; lastCorrection = null
        val pos = selEnd
        val n = if (forward) {
            val after = ic.getTextAfterCursor(2, 0)?.toString().orEmpty()
            if (after.isEmpty()) return false
            if (after.length == 2 && Character.isSurrogatePair(after[0], after[1])) 2 else 1
        } else {
            val before = ic.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.isEmpty() || pos <= 0) return false
            if (before.length == 2 && Character.isSurrogatePair(before[0], before[1])) 2 else 1
        }
        val newPos = if (forward) pos + n else pos - n
        if (newPos < 0) return false
        ic.setSelection(newPos, newPos)
        selStart = newPos; selEnd = newPos
        return true
    }

    override fun onDeleteSelect(words: Int): Boolean {
        val ic = currentInputConnection ?: return false
        if (composing.isNotEmpty()) {
            ic.finishComposingText(); composing.clear()
        }
        if (deleteAnchor < 0) deleteAnchor = selEnd
        val anchor = deleteAnchor
        if (words <= 0) {
            ic.setSelection(anchor, anchor)
            selStart = anchor; selEnd = anchor
            return true
        }
        // Text before the anchor = text before the selection + the selection itself.
        val before = ic.getTextBeforeCursor(2000, 0)?.toString().orEmpty()
        val text = if (selStart != selEnd) before + ic.getSelectedText(0)?.toString().orEmpty() else before
        var i = text.length
        var count = 0
        while (count < words && i > 0) {
            while (i > 0 && text[i - 1].isWhitespace()) i--
            while (i > 0 && !text[i - 1].isWhitespace()) i--
            count++
        }
        val start = (anchor - (text.length - i)).coerceAtLeast(0)
        if (start == selStart && selEnd == anchor) return false
        ic.setSelection(start, anchor)
        selStart = start; selEnd = anchor
        return true
    }

    override fun onDeleteCommit() {
        val ic = currentInputConnection ?: return
        if (selEnd > selStart) ic.commitText("", 1)
        deleteAnchor = -1
        updateShiftFromField()
        refreshSuggestions()
    }

    override fun onSpaceLongPress() {
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        selStart = newSelStart; selEnd = newSelEnd
        if (deleteAnchor >= 0 && newSelStart == newSelEnd && newSelEnd != deleteAnchor) deleteAnchor = -1
        // The cursor moved away from the word being typed (tapped elsewhere).
        if (composing.isNotEmpty() && (candidatesStart < 0 || newSelEnd != candidatesEnd || newSelStart != newSelEnd)) {
            currentInputConnection?.finishComposingText()
            composing.clear()
            refreshSuggestions()
        }
        if (composing.isEmpty() && target == Target.NONE && deleteAnchor < 0) {
            val moved = newSelStart != oldSelStart || newSelEnd != oldSelEnd
            if (moved && candidatesStart < 0) updateShiftFromField()
        }
    }

    // ------------------------------------------------------------------
    // Suggestions
    // ------------------------------------------------------------------

    private fun refreshSuggestions() {
        if (!::topBar.isInitialized || target != Target.NONE) return
        if (topBar.mode == TopBarView.BarMode.MESSAGE) return
        val typed = composing.toString()
        val gen = ++suggestionGen
        if (!prefs.showSuggestions || noSuggestions) {
            autoCorrection = null
            currentSuggestions = emptyList()
            showToolbarOrSuggestions()
            return
        }
        val prev = prevWord
        val allowAuto = prefs.autocorrect
        val block = prefs.blockOffensive
        val predict = prefs.nextWord
        engineHandler.post {
            val (list, auto) = if (typed.isNotEmpty()) {
                val r = engine.suggest(typed, prev, block, allowAuto)
                r.suggestions to r.autoCorrection
            } else if (predict && prev != null) {
                engine.predict(prev, block) to null
            } else emptyList<String>() to null
            main.post {
                if (gen != suggestionGen) return@post
                autoCorrection = auto
                currentSuggestions = arrange(typed, list, auto)
                showToolbarOrSuggestions()
            }
        }
    }

    /** Middle slot = what space will insert; left = what you typed; right = next best (or an emoji). */
    private fun arrange(typed: String, list: List<String>, auto: String?): List<String> {
        if (typed.isEmpty()) {
            return when (list.size) {
                0 -> emptyList()
                1 -> listOf("", list[0], "")
                2 -> listOf(list[1], list[0], "")
                else -> listOf(list[1], list[0], list[2])
            }
        }
        val others = list.filter { !it.equals(typed, true) && it != auto }
        val emoji = if (prefs.emojiSuggestions) EmojiData.forWord(auto ?: typed) else null
        val third = emoji ?: others.getOrElse(if (auto == null) 1 else 0) { "" }
        return if (auto != null) {
            listOf("“$typed”", auto, third)
        } else {
            val best = if (engine.isKnown(typed) || others.isEmpty()) typed else others[0]
            val left = if (best == typed) others.getOrElse(0) { "" } else "“$typed”"
            val right = if (emoji != null) emoji else others.firstOrNull { it != best && it != left } ?: ""
            listOf(left, best, right)
        }
    }

    private fun showToolbarOrSuggestions() {
        val empty = currentSuggestions.all { it.isEmpty() }
        val wantToolbar = showToolbar || (empty && prefs.toolbar && composing.isEmpty())
        if (wantToolbar && !isPassword) {
            topBar.mode = TopBarView.BarMode.TOOLBAR
        } else {
            topBar.mode = TopBarView.BarMode.SUGGESTIONS
            topBar.suggestions = currentSuggestions
            topBar.emphasized = if (currentSuggestions.getOrElse(1) { "" }.isNotEmpty() && (autoCorrection != null || composing.isNotEmpty())) 1 else -1
        }
    }

    // ------------------------------------------------------------------
    // TopBarView.Listener
    // ------------------------------------------------------------------

    override fun onSuggestion(index: Int) {
        val ic = currentInputConnection ?: return
        var s = currentSuggestions.getOrElse(index) { "" }
        if (s.isEmpty()) return
        // A glided word: swap it for the chosen alternative.
        val glided = lastGlide
        if (glided != null && composing.isEmpty()) {
            if (s != glided) {
                val before = ic.getTextBeforeCursor(glided.length, 0)?.toString()
                if (before == glided) {
                    ic.deleteSurroundingText(glided.length, 0)
                    ic.commitText(s, 1)
                    lastGlide = s
                    val p = prevWord
                    if (!noLearning) engineHandler.post { engine.learnGlide(s); engine.learnWord(s, p) }
                    glideAlternatives = (listOf(glided) + glideAlternatives).filter { it != s }
                    showGlideAlternatives(s)
                }
            }
            return
        }
        val isEmoji = s.isNotEmpty() && !s[0].isLetterOrDigit() && s[0] != '“' && !s.any { it.isLetter() }
        val typedSlot = s.startsWith("“") && s.endsWith("”")
        if (typedSlot) s = s.substring(1, s.length - 1)
        if (isEmoji) {
            if (composing.isNotEmpty()) finishWord(autocorrect = true)
            val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            if (before.isNotEmpty() && !before[0].isWhitespace()) ic.commitText(" ", 1)
            ic.commitText(s, 1)
        } else {
            val typed = composing.toString()
            if (phantomSpace) { ic.commitText(" ", 1); phantomSpace = false }
            ic.commitText(s, 1)
            ic.commitText(" ", 1)
            keyboard.commitAim(accepted = typed == s)
            if (typed.isNotEmpty()) {
                if (typedSlot) {
                    if (!noLearning) engineHandler.post { engine.learnRejectedCorrection(typed) }
                    learn(s, null)
                } else learn(s, if (!typed.equals(s, true)) typed else null)
            } else learn(s, null)
            composing.clear()
            justPickedSuggestion = true
        }
        lastCorrection = null
        autoCorrection = null
        updateShiftFromField()
        refreshSuggestions()
    }

    override fun onSuggestionLongPress(index: Int) {
        var s = currentSuggestions.getOrElse(index) { "" }
        if (s.startsWith("“")) s = s.trim('“', '”')
        if (s.isEmpty() || !s.any { it.isLetter() }) return
        engineHandler.post { engine.removeWord(s) }
        showMessage("Removed “$s” from suggestions")
        main.postDelayed({ if (topBar.mode == TopBarView.BarMode.MESSAGE && target == Target.NONE && !voice.listening) onMessageClose() }, 1600)
    }

    override fun onToggleToolbar() {
        showToolbar = topBar.mode != TopBarView.BarMode.TOOLBAR
        if (!showToolbar && currentSuggestions.all { it.isEmpty() }) {
            topBar.mode = TopBarView.BarMode.SUGGESTIONS
            topBar.suggestions = listOf("", "", "")
        } else showToolbarOrSuggestions()
    }

    override fun onTool(tool: TopBarView.Tool) {
        showToolbar = false
        when (tool) {
            TopBarView.Tool.EMOJI -> showEmoji()
            TopBarView.Tool.GIF -> showGif()
            TopBarView.Tool.CLIPBOARD -> showClipboard()
            TopBarView.Tool.AI -> showAi()
            TopBarView.Tool.SETTINGS -> startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            TopBarView.Tool.MIC -> startVoice()
        }
    }

    override fun onSearchSubmit() {
        val q = targetText.toString().trim()
        when (target) {
            Target.GIF_SEARCH -> {
                closeTarget()
                showGif(q)
            }
            Target.AI_PROMPT -> {
                val action = aiPromptAction
                closeTarget()
                showPanelView(aiPanel ?: return)
                aiPanel?.start(action, q)
            }
            Target.NONE -> {}
        }
    }

    override fun onSearchClose() {
        val t = target
        closeTarget()
        when (t) {
            Target.GIF_SEARCH -> gifPanel?.let { showPanelView(it) }
            Target.AI_PROMPT -> aiPanel?.let { showPanelView(it) }
            Target.NONE -> {}
        }
    }

    override fun onMessageClose() {
        if (voice.listening) voice.stop()
        topBar.mode = TopBarView.BarMode.SUGGESTIONS
        refreshSuggestions()
    }

    private fun showMessage(msg: String) {
        topBar.message = msg
        topBar.mode = TopBarView.BarMode.MESSAGE
    }

    private fun openTarget(t: Target, hint: String, icon: Icon, initial: String = "") {
        target = t
        targetText.clear(); targetText.append(initial)
        topBar.searchHint = hint
        topBar.searchIcon = icon
        topBar.searchText = initial
        topBar.mode = TopBarView.BarMode.SEARCH
        hidePanel()
        if (mode != Mode.ALPHA) setMode(Mode.ALPHA)
        setShift(ShiftState.OFF)
    }

    private fun closeTarget(redraw: Boolean = true) {
        if (target == Target.NONE) return
        target = Target.NONE
        targetText.clear()
        if (redraw && ::topBar.isInitialized) {
            topBar.mode = TopBarView.BarMode.SUGGESTIONS
            refreshSuggestions()
        }
    }

    // ------------------------------------------------------------------
    // Panels
    // ------------------------------------------------------------------

    private fun ui() = PanelUi(this, theme, FontManager.keyTypeface(this, prefs.keyFont))

    private fun showPanelView(v: View) {
        val h = keyboard.height.takeIf { it > 0 } ?: keyboard.measuredHeight
        (v.parent as? ViewGroup)?.removeView(v)
        panel?.let { if (it !== v) content.removeView(it) }
        content.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
        keyboard.visibility = View.INVISIBLE
        panel = v
    }

    private fun hidePanel() {
        panel?.let { content.removeView(it) }
        panel = null
        if (::keyboard.isInitialized) keyboard.visibility = View.VISIBLE
    }

    private fun closePanels() {
        hidePanel()
        gifPanel = null
        aiPanel = null
        refreshSuggestions()
    }

    private fun showEmoji() {
        if (composing.isNotEmpty()) finishWord(autocorrect = true)
        showPanelView(EmojiPanel(this, ui(),
            onEmoji = { e -> currentInputConnection?.commitText(e, 1); phantomSpace = false },
            onBackspace = { onBackspace() },
            onClose = { closePanels() }))
    }

    private fun showClipboard() {
        if (composing.isNotEmpty()) finishWord(autocorrect = true)
        clipStore.prune(prefs.clipClearMs)
        showPanelView(ClipboardPanel(this, ui(), clipStore, prefs.clipboard,
            onPaste = { t -> currentInputConnection?.commitText(t, 1); closePanels() },
            onClose = { closePanels() }))
    }

    private fun showGif(query: String = "") {
        if (composing.isNotEmpty()) finishWord(autocorrect = true)
        val client = GifClient(this, prefs)
        val p = gifPanel ?: GifPanel(this, ui(), client, net,
            onSend = { g -> sendGif(g) },
            onSearchTap = { openTarget(Target.GIF_SEARCH, "Search ${client.providerName}", Icon.SEARCH) },
            onClose = { closePanels() }).also { gifPanel = it }
        showPanelView(p)
        p.search(query)
    }

    private fun sendGif(g: GifClient.Gif) {
        val ei = currentInputEditorInfo ?: return
        val mimes = EditorInfoCompat.getContentMimeTypes(ei)
        val accepts = mimes.any { ClipDescription.compareMimeTypes("image/gif", it) }
        if (!accepts) {
            if (prefs.gifLinkFallback) {
                currentInputConnection?.commitText(g.sendUrl, 1)
                closePanels()
            } else showMessage("This app doesn't accept GIFs")
            return
        }
        showMessage("Sending GIF…")
        net.execute {
            val result = runCatching {
                val dir = File(cacheDir, "gifs").apply { mkdirs() }
                dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 3_600_000 }?.forEach { it.delete() }
                val ext = if (g.sendUrl.substringBefore('?').endsWith(".webp")) "webp" else "gif"
                val f = File(dir, "${g.id.ifEmpty { System.currentTimeMillis().toString() }}.$ext")
                if (!f.exists()) f.writeBytes(Http.bytes(g.sendUrl, 15 * 1024 * 1024))
                f to ext
            }
            main.post {
                result.onSuccess { (f, ext) ->
                    val uri = FileProvider.getUriForFile(this, "com.matrixkey.files", f)
                    val info = InputContentInfoCompat(uri, ClipDescription("GIF", arrayOf("image/$ext")), Uri.parse(g.pageUrl))
                    val ok = currentInputConnection?.let { ic ->
                        InputConnectionCompat.commitContent(ic, ei, info, InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION, null)
                    } == true
                    if (!ok && prefs.gifLinkFallback) currentInputConnection?.commitText(g.sendUrl, 1)
                    topBar.mode = TopBarView.BarMode.SUGGESTIONS
                    closePanels()
                }.onFailure {
                    showMessage("Couldn't download that GIF")
                }
            }
        }
    }

    private fun showAi() {
        if (isPassword) return
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) { ic.finishComposingText(); learn(composing.toString(), null); composing.clear() }
        val selected = ic.getSelectedText(0)?.toString().orEmpty()
        val hasSel = selected.isNotEmpty()
        val source = if (hasSel) selected else {
            val before = ic.getTextBeforeCursor(4000, 0)?.toString().orEmpty()
            val after = ic.getTextAfterCursor(4000, 0)?.toString().orEmpty()
            before + after
        }
        val ai = AiClient(prefs)
        lateinit var panelRef: AiPanel
        panelRef = AiPanel(this, ui(), source, hasSel,
            onRun = { action, extra ->
                net.execute {
                    val r = runCatching { ai.run(action, source, extra) }
                    main.post {
                        r.onSuccess { panelRef.showResult(it) }.onFailure { panelRef.showError(AiClient.friendlyError(it)) }
                    }
                }
            },
            onAskPrompt = { action ->
                aiPromptAction = action
                openTarget(Target.AI_PROMPT,
                    if (action == AiClient.Action.TRANSLATE) "Translate to which language?" else "Tell AI what to do…", Icon.AI)
            },
            onReplace = { text -> replaceWithAi(text, hasSel, source) },
            onClose = { closePanels() })
        aiPanel = panelRef
        showPanelView(panelRef)
    }

    private fun replaceWithAi(text: String, hadSelection: Boolean, source: String) {
        val ic = currentInputConnection ?: return
        ic.beginBatchEdit()
        if (hadSelection && (ic.getSelectedText(0)?.toString() == source)) {
            ic.commitText(text, 1)
        } else {
            val before = ic.getTextBeforeCursor(4000, 0)?.length ?: 0
            val after = ic.getTextAfterCursor(4000, 0)?.length ?: 0
            ic.deleteSurroundingText(before, after)
            ic.commitText(text, 1)
        }
        ic.endBatchEdit()
        closePanels()
    }

    // ------------------------------------------------------------------
    // Voice
    // ------------------------------------------------------------------

    private fun startVoice() {
        if (!voice.hasPermission()) {
            startActivity(Intent(this, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        if (composing.isNotEmpty()) finishWord(autocorrect = true)
        hidePanel()
        voicePartial = ""
        if (voice.start()) showMessage("Listening…")
    }

    private fun voiceDone(text: String) {
        val ic = currentInputConnection
        if (text.isNotBlank() && ic != null) {
            val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            val needsSpace = before.isNotEmpty() && !before[0].isWhitespace()
            var t = text.trim()
            if (ic.getCursorCapsMode(currentInputEditorInfo?.inputType ?: 0) != 0) t = t.replaceFirstChar { it.uppercase() }
            ic.commitText((if (needsSpace) " " else "") + t, 1)
            prevWord = t.split(' ').lastOrNull()
        }
        topBar.mode = TopBarView.BarMode.SUGGESTIONS
        updateShiftFromField()
        refreshSuggestions()
    }

    private fun voiceError(msg: String) {
        showMessage(msg)
        main.postDelayed({ if (topBar.mode == TopBarView.BarMode.MESSAGE && !voice.listening) onMessageClose() }, 2500)
    }
}
