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
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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

private val PUNCTUATION = listOf(",", ".", "?", "!", "'", ":", ";")

class MatrixKeyService : InputMethodService(), KeyboardView.Listener, TopBarView.Listener,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var prefs: Prefs
    private lateinit var engine: WordEngine
    private lateinit var clipStore: ClipboardStore
    private val main = Handler(Looper.getMainLooper())
    private val engineHandler get() = WordEngine.handler
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
    /** Live emoji results above the keys while searching emoji. */
    private lateinit var emojiStrip: android.widget.HorizontalScrollView
    private lateinit var emojiStripRow: LinearLayout
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

    /**
     * Proofreading: the suggestions each recent word was offered with
     * (lower-case word → alternatives), so tapping a word in your message brings
     * them back. Kept in memory only.
     */
    private val wordHistory = object : LinkedHashMap<String, List<String>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>?) = size > 300
    }
    /** The word in the text you tapped on, while its suggestions are showing. */
    private data class EditWord(val start: Int, val end: Int, val word: String)
    private var editWord: EditWord? = null
    /** Last time the keyboard itself was touched or changed the text. */
    private var lastOwnActionAt = 0L
    private var suggestionGen = 0
    private var currentSuggestions: List<String> = emptyList()
    private var autoCorrection: String? = null
    private var deleteAnchor = -1
    private var showToolbar = false
    private var needsReload = true

    // Field flags
    private var noLearning = false
    private var noSuggestions = false
    /** Fields that ask for no suggestions (many apps set this just to hide spell-check underlines):
     *  suggestions and next-word predictions still show, but nothing is changed automatically. */
    private var noAutocorrect = false
    /** The strip is showing personal-dictionary matches for the text before the cursor. */
    private var tokenSuggestions = false

    /** Text before the cursor back to the last space (e.g. "kevin.mi" while typing an email). */
    private fun currentToken(): String {
        val before = currentInputConnection?.getTextBeforeCursor(80, 0)?.toString().orEmpty()
        return before.takeLastWhile { !it.isWhitespace() }
    }
    private var isPassword = false

    // Internal text field (GIF search, AI prompt, emoji search)
    private enum class Target { NONE, GIF_SEARCH, AI_PROMPT, EMOJI_SEARCH }
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

    companion object {
        private const val SCAN_CHIP = "Insert scanned text"
        /** The running keyboard, so Scan text can hand its result straight over. */
        @Volatile var instance: MatrixKeyService? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        engine = WordEngine.get(this)
        engineHandler.post { engine.ensureLoaded() }
        clipStore = ClipboardStore(this)
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).addPrimaryClipChangedListener(clipListener)
        voice = VoiceInput(this, prefs,
            onPartial = { s -> main.post { voicePartial = s; showMessage("Listening… $s") } },
            onFinal = { s -> main.post { voiceDone(s) } },
            onError = { s -> main.post { voiceError(s) } })
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        runCatching { (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).removePrimaryClipChangedListener(clipListener) }
        voice.stop()
        engineHandler.post { engine.save() }
        net.shutdownNow()
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        needsReload = true
        if (key == "reset_learned_at") {
            if (::keyboard.isInitialized) keyboard.clearAim()
            prefs.sp.edit().remove("aim").apply()
        }
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
            emojiStripRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
            emojiStrip = android.widget.HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = false
                visibility = View.GONE
                addView(emojiStripRow)
            }
            addView(emojiStrip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (48 * resources.displayMetrics.density).toInt()))
            addView(content, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        val r = KeyboardRoot(ctx, column)
        r.onTouch = { lastOwnActionAt = SystemClock.uptimeMillis() }
        r.addView(bgImage, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        r.addView(overlay, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Android positions the keyboard window above the navigation bar itself;
        // the keyboard view must not add any inset padding of its own.
        r.setOnApplyWindowInsetsListener { _, insets -> insets }
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
        // Not into Matrix Key's own screens (the Scan text box itself): wait for the app you came from.
        if (info.packageName != packageName) insertPendingScan()
        selStart = info.initialSelStart; selEnd = info.initialSelEnd
        updateEnterIcon(info)
        setMode(modeFor(info))
        updateShiftFromField()
        refreshSuggestions()
    }

    /**
     * Scan text's "Insert text" was tapped. As soon as the app you came from has its
     * text box back (not Matrix Key's own screen), type the text in. If the keyboard
     * isn't open there yet, it's inserted the next time it opens in that app.
     */
    fun scanReady() {
        main.post {
            var tries = 0
            val check = object : Runnable {
                override fun run() {
                    if (prefs.sp.getString(ScanActivity.PENDING, null) == null && scanText == null) return
                    val ei = currentInputEditorInfo
                    if (ei != null && ei.packageName != packageName && currentInputConnection != null && isInputViewShown) {
                        insertPendingScan()
                        return
                    }
                    if (++tries < 40) main.postDelayed(this, 150)
                }
            }
            main.postDelayed(check, 200)
        }
    }

    /** Scanned text waiting to go in; offered as a suggestion until it's in the message. */
    private var scanText: String? = null
    private var scanInFlight = false

    /**
     * Types the scanned text in, then checks it actually arrived (some apps ignore text
     * sent while their box is still being set up). If it didn't, it's offered as a
     * suggestion to tap instead.
     */
    private fun insertPendingScan() {
        prefs.sp.getString(ScanActivity.PENDING, null)?.let { t ->
            val at = prefs.sp.getLong(ScanActivity.PENDING_AT, 0)
            prefs.sp.edit().remove(ScanActivity.PENDING).remove(ScanActivity.PENDING_AT).apply()
            if (System.currentTimeMillis() - at <= 10 * 60_000L) scanText = t
        }
        if (scanText == null) return
        if (isPassword) { scanText = null; return }
        if (scanInFlight) return
        scanInFlight = true
        // Apps like Messages and WhatsApp restore their saved draft just after you come
        // back, which wipes text typed in straight away. So wait for the box to settle,
        // type it in, check it stuck, and try once more before offering the button.
        scanAttempt(0)
    }

    private val scanDelays = longArrayOf(700, 1300)

    private fun scanAttempt(n: Int) {
        main.postDelayed({
            val text = scanText
            val ic = currentInputConnection
            val ei = currentInputEditorInfo
            if (text == null || ic == null || ei == null || ei.packageName == packageName) {
                scanInFlight = false
                if (text != null) showScanChip()
                return@postDelayed
            }
            val tail = text.takeLast(12)
            if (!ic.getTextBeforeCursor(tail.length + 2, 0)?.toString().orEmpty().contains(tail)) {
                val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                ic.commitText((if (before.isNotEmpty() && !before[0].isWhitespace()) " " else "") + text, 1)
            }
            main.postDelayed({
                val now = currentInputConnection?.getTextBeforeCursor(tail.length + 2, 0)?.toString().orEmpty()
                when {
                    now.contains(tail) -> { scanInFlight = false; scanText = null; refreshSuggestions() }
                    n + 1 < scanDelays.size -> scanAttempt(n + 1)
                    else -> { scanInFlight = false; showScanChip() }
                }
            }, 400)
        }, scanDelays[n])
    }

    private fun showScanChip() {
        if (scanText == null || !::topBar.isInitialized) return
        currentSuggestions = listOf("", SCAN_CHIP, "")
        topBar.mode = TopBarView.BarMode.SUGGESTIONS
        topBar.suggestions = currentSuggestions
        topBar.emphasized = 1
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
        keyboard.textScale = prefs.keyTextScale
        keyboard.dottedIcons = prefs.dottedIcons
        topBar.onTapFeedback = { keyboard.feedback.key(topBar) }

        overlay.trailColor = when (prefs.trailColor) {
            "text" -> theme.text
            "white" -> android.graphics.Color.WHITE
            else -> theme.trail ?: theme.accent
        }
        overlay.trailDotted = when (prefs.trailStyle) { "dot" -> true; "solid" -> false; else -> dot }
        overlay.trailWidthDp = when (prefs.trailWidth) { "thin" -> 3f; "thick" -> 11f; else -> 6.5f }

        topBar.theme = theme
        topBar.typeface = tf
        topBar.dotStyle = dot
        topBar.tools = buildList {
            // The emoji key on the keyboard already opens emoji; only add it here without one.
            if (!prefs.emojiKey) add(TopBarView.Tool.EMOJI)
            if (prefs.gif) add(TopBarView.Tool.GIF)
            add(TopBarView.Tool.CLIPBOARD)
            if (prefs.ai) add(TopBarView.Tool.AI)
            add(TopBarView.Tool.SCAN)
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
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        noAutocorrect = noSuggestions || (info.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS) != 0
        val incognitoField = (info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0
        noLearning = isPassword || !prefs.learn || (prefs.incognito && incognitoField)
    }

    /**
     * Multi-line fields and message boxes (texts, chats, emails) get a new line from
     * the enter key; the app's own send button sends.
     */
    private fun enterMakesNewLine(info: EditorInfo?): Boolean {
        if (info == null) return false
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        return (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0 ||
            (cls == InputType.TYPE_CLASS_TEXT && (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE ||
                variation == InputType.TYPE_TEXT_VARIATION_LONG_MESSAGE))
    }

    private fun updateEnterIcon(info: EditorInfo) {
        val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
        val noAction = (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 || enterMakesNewLine(info)
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
            targetInsert(text)
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
            if (text == "=" && !isPassword) solveMath(ic)
            if (mode != Mode.ALPHA && text == "'" ) setMode(Mode.ALPHA)
            updateShiftFromField()
            refreshSuggestions()
        }
    }

    /** "5+5(6/2)=" → types the answer right after the "=". */
    private fun solveMath(ic: android.view.inputmethod.InputConnection) {
        val before = ic.getTextBeforeCursor(200, 0)?.toString() ?: return
        if (!before.endsWith("=")) return
        val answer = com.matrixkey.engine.Calc.solveBefore(before.dropLast(1)) ?: return
        ic.commitText(answer, 1)
        prevWord = null
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
            KeyType.NUMPAD -> setMode(Mode.NUMPAD)
            KeyType.ALPHA -> { setMode(Mode.ALPHA); updateShiftFromField() }
            KeyType.EMOJI -> showEmoji()
            else -> {}
        }
    }

    private fun onSpace() {
        if (target != Target.NONE) { targetInsert(" "); return }
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
            if (targetCursor > 0) { targetText.deleteCharAt(targetCursor - 1); targetCursor-- }
            targetChanged()
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
        val newLine = enterMakesNewLine(ei)
        val noAction = ei == null || (ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 || newLine
        if (newLine) {
            // Type the line break as text: some messaging apps treat an Enter key
            // press as "send", but a typed new line is always just a new line.
            ic.commitText("\n", 1)
        } else if (!noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
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
        val final0 = if (autocorrect && prefs.autocorrect && !noAutocorrect && correction != null &&
            !correction.equals(typed, ignoreCase = false) && engine.ready
        ) correction else typed
        val final = capitalI(final0)
        ic.commitText(final, 1)
        val corrected = final0 != typed
        remember(final, (if (corrected) listOf(typed) else emptyList()) + currentSuggestions)
        lastCorrection = if (corrected) typed to final else null
        keyboard.commitAim(accepted = !corrected && engine.isKnown(typed))
        learn(final, if (corrected) typed else null)
        composing.clear()
        autoCorrection = null
    }

    /** The word "i" on its own is always written "I". */
    private fun capitalI(w: String): String = if (w == "i") "I" else w

    private fun learn(word: String, typo: String?) {
        if (noLearning) { prevWord = word; return }
        val prev = prevWord
        engineHandler.post {
            engine.learnWord(word, prev)
            if (typo != null) engine.learnCorrection(typo, word)
        }
        prevWord = word
    }

    override fun onGlide(path: List<PointF>, times: List<Long>) {
        if (target != Target.NONE) {
            // Swiping inside the GIF search / AI prompt box types into that box.
            engineHandler.post {
                val words = engine.decodeGlide(path, times, null, prefs.blockOffensive)
                main.post {
                    val w = words.firstOrNull() ?: return@post
                    if (target == Target.NONE) return@post
                    val word = if (shift != ShiftState.OFF) w.replaceFirstChar { it.uppercase() } else w
                    val before = targetText.getOrNull(targetCursor - 1)
                    val after = targetText.getOrNull(targetCursor)
                    targetInsert((if (before != null && before != ' ') " " else "") + word + (if (after != null && after != ' ') " " else ""))
                    if (shift == ShiftState.ON) setShift(ShiftState.OFF)
                }
            }
            return
        }
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) finishWord(autocorrect = false)
        val prev = prevWord
        val caps = shift
        engineHandler.post {
            val words = engine.decodeGlide(path, times, prev, prefs.blockOffensive)
            // Personal-dictionary entries that start with the glided word (e.g. "kevin" → your email).
            val mine = words.firstOrNull()?.let { first -> engine.personalMatches(first).filter { !it.equals(first, true) } }.orEmpty()
            main.post {
                if (words.isEmpty()) return@post
                var w = capitalI(words.first())
                if (caps == ShiftState.LOCKED) w = w.uppercase() else if (caps == ShiftState.ON) w = w.replaceFirstChar { it.uppercase() }
                val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
                if (phantomSpace || (before.isNotEmpty() && !before[0].isWhitespace() && before[0] !in "([{\"'")) ic.commitText(" ", 1)
                ic.commitText(w, 1)
                phantomSpace = true
                phantomSpaceWasAuto = true
                lastGlide = w
                lastCorrection = null
                glideAlternatives = (mine + words.drop(1).map { alt ->
                    if (caps == ShiftState.LOCKED) alt.uppercase() else if (caps == ShiftState.ON) alt.replaceFirstChar { it.uppercase() } else alt
                }).distinct()
                if (shift == ShiftState.ON) setShift(ShiftState.OFF)
                lastOwnActionAt = SystemClock.uptimeMillis()
                remember(w, glideAlternatives)
                learn(w, null)
                showGlideAlternatives(w)
            }
        }
    }

    private fun showGlideAlternatives(chosen: String) {
        val alts = glideAlternatives
        currentSuggestions = listOf(alts.getOrElse(0) { "" }, chosen, alts.getOrElse(1) { "" }) + alts.drop(2)
        topBar.suggestions = currentSuggestions
        topBar.emphasized = 1
        topBar.mode = TopBarView.BarMode.SUGGESTIONS
    }

    override fun onCursorStep(forward: Boolean): Boolean {
        // In the search / prompt box, slide the cursor through its text instead of the app's.
        if (target != Target.NONE) {
            val next = (targetCursor + if (forward) 1 else -1)
            if (next < 0 || next > targetText.length) return false
            targetCursor = next
            targetChanged()
            return true
        }
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
        if (target != Target.NONE) {
            // Preview: show the box without the last N words; applied on release.
            if (words == targetDeleteWords) return false
            targetDeleteWords = words
            val head = targetText.substring(0, targetCursor)
            topBar.searchText = dropLastWords(head, words) + targetText.substring(targetCursor)
            topBar.searchCursor = dropLastWords(head, words).length
            return true
        }
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
        if (target != Target.NONE) {
            val head = dropLastWords(targetText.substring(0, targetCursor), targetDeleteWords)
            val tail = targetText.substring(targetCursor)
            targetText.clear(); targetText.append(head).append(tail)
            targetCursor = head.length
            targetChanged()
            targetDeleteWords = 0
            return
        }
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
            // You tapped somewhere in your text (not the keyboard typing): offer
            // suggestions for the word under the cursor, for easy proofreading.
            val byTap = SystemClock.uptimeMillis() - lastOwnActionAt > 400
            if (moved && byTap && candidatesStart < 0 && newSelStart == newSelEnd) suggestForWordAtCursor(newSelEnd)
            else if (moved && editWord != null && newSelEnd !in editWord!!.start..editWord!!.end) {
                editWord = null
                refreshSuggestions()
            }
        }
    }

    /** Remembers what [word] was offered alongside, for proofreading later. */
    private fun remember(word: String, offered: List<String>) {
        if (noLearning || word.isEmpty()) return
        val alts = offered.map { it.trim('\u201C', '\u201D') }
            .filter { a -> a.isNotEmpty() && !a.equals(word, true) && a.any { it.isLetter() } }
            .distinctBy { it.lowercase() }
            .take(8)
        if (alts.isNotEmpty()) wordHistory[word.lowercase()] = alts
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\''

    private fun suggestForWordAtCursor(cursor: Int) {
        val ic = currentInputConnection ?: return
        if (isPassword || panel != null || !prefs.showSuggestions || !::topBar.isInitialized) return
        val left = ic.getTextBeforeCursor(48, 0)?.toString().orEmpty().takeLastWhile { isWordChar(it) }
        val right = ic.getTextAfterCursor(48, 0)?.toString().orEmpty().takeWhile { isWordChar(it) }
        val word = (left + right).trim('\'')
        if (word.isEmpty() || !word.any { it.isLetter() }) {
            if (editWord != null) { editWord = null; refreshSuggestions() }
            return
        }
        val start = cursor - left.length + (left.length - left.trimStart('\'').length)
        val e = EditWord(start, start + word.length, word)
        phantomSpace = false; lastGlide = null; lastCorrection = null; tokenSuggestions = false
        val gen = ++suggestionGen
        val saved = wordHistory[word.lowercase()]
        val block = prefs.blockOffensive
        engineHandler.post {
            // Words you typed this session come back with what they were offered;
            // older words get fresh suggestions.
            val alts = saved ?: engine.suggest(word, null, block, true).suggestions
                .filter { !it.equals(word, true) }
            main.post {
                if (gen != suggestionGen || composing.isNotEmpty()) return@post
                val cased = alts.map { matchCase(word, it) }.distinctBy { it.lowercase() }
                editWord = e
                autoCorrection = null
                currentSuggestions = listOf(cased.getOrElse(0) { "" }, word, cased.getOrElse(1) { "" }) + cased.drop(2)
                topBar.mode = TopBarView.BarMode.SUGGESTIONS
                topBar.suggestions = currentSuggestions
                topBar.emphasized = 1
            }
        }
    }

    private fun matchCase(model: String, w: String) = when {
        model.length > 1 && model.all { !it.isLetter() || it.isUpperCase() } -> w.uppercase()
        model.first().isUpperCase() -> w.replaceFirstChar { it.uppercase() }
        else -> w
    }

    /** Swaps the tapped word for the chosen suggestion; the cursor lands after it. */
    private fun replaceTappedWord(e: EditWord, chosen: String) {
        val ic = currentInputConnection ?: return
        editWord = null
        lastOwnActionAt = SystemClock.uptimeMillis()
        if (chosen != e.word) {
            ic.beginBatchEdit()
            ic.setComposingRegion(e.start, e.end)
            ic.commitText(chosen, 1)
            ic.endBatchEdit()
            remember(chosen, listOf(e.word) + currentSuggestions)
            val old = e.word
            if (!noLearning) engineHandler.post {
                // A misspelling you fixed is remembered as a habit; real words aren't.
                if (!engine.isKnown(old)) engine.learnCorrection(old.lowercase(), chosen.lowercase())
                engine.learnWord(chosen, null)
            }
        } else {
            ic.setSelection(e.end, e.end)
        }
        refreshSuggestions()
    }

    // ------------------------------------------------------------------
    // Suggestions
    // ------------------------------------------------------------------

    private fun refreshSuggestions() {
        editWord = null
        if (!::topBar.isInitialized || target != Target.NONE) return
        if (scanText != null && !scanInFlight && currentInputEditorInfo?.packageName != packageName) { showScanChip(); return }
        if (topBar.mode == TopBarView.BarMode.MESSAGE) return
        val typed = composing.toString()
        val gen = ++suggestionGen
        if (!prefs.showSuggestions || noSuggestions) {
            // No word suggestions here (the app asked for none, e.g. email fields or
            // Google Keep notes, or they're turned off). Still offer your own dictionary
            // entries (like your email address) as you type, and punctuation after a word.
            autoCorrection = null
            val token = if (isPassword) "" else currentToken()
            val mine = if (token.isNotEmpty()) engine.personalMatches(token) else emptyList()
            tokenSuggestions = mine.isNotEmpty()
            currentSuggestions = when {
                mine.isNotEmpty() -> centered(mine)
                !isPassword && typed.isEmpty() && afterWord() -> PUNCTUATION
                else -> emptyList()
            }
            showToolbarOrSuggestions()
            return
        }
        val prev = prevWord
        val allowAuto = prefs.autocorrect && !noAutocorrect
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
                // Typing something like an email ("kevin.mi…"): offer matching
                // personal-dictionary entries for the whole thing.
                tokenSuggestions = false
                val tok = currentToken()
                if (tok.any { it == '@' || it == '.' || it == '_' || it == '-' }) {
                    val mine = engine.personalMatches(tok)
                    if (mine.isNotEmpty()) {
                        currentSuggestions = centered(mine)
                        tokenSuggestions = true
                        autoCorrection = null
                        showToolbarOrSuggestions()
                        return@post
                    }
                }
                // Right after "word" + space: punctuation is always on offer.
                // With next-word prediction on, the two best predictions come first.
                if (typed.isEmpty() && afterWord()) {
                    val preds = list.filter { it.isNotEmpty() && it !in PUNCTUATION }.take(2)
                    currentSuggestions = if (preds.isEmpty()) PUNCTUATION else preds + listOf(",", ".", "?", "!")
                }
                showToolbarOrSuggestions()
            }
        }
    }

    /** True when the cursor sits right after a word (or a word and one space). */
    private fun afterWord(): Boolean {
        val before = currentInputConnection?.getTextBeforeCursor(2, 0)?.toString().orEmpty()
        if (before.isEmpty()) return false
        val last = before.last()
        return last.isLetterOrDigit() || (last == ' ' && before.length == 2 && before[0].isLetterOrDigit())
    }

    /** Best item in the middle, second on the left, third on the right, the rest after (scroll to see). */
    private fun centered(list: List<String>): List<String> =
        if (list.isEmpty()) emptyList()
        else listOf(list.getOrElse(1) { "" }, list[0], list.getOrElse(2) { "" }) + list.drop(3)

    /**
     * Middle slot = what space will insert; left = what you typed; right = next best (or an emoji).
     * Remaining candidates follow, reached by scrolling the bar.
     */
    private fun arrange(typed: String, list: List<String>, auto: String?): List<String> {
        if (typed.isEmpty()) return centered(list)
        val three = arrangeThree(typed, list, auto)
        val extras = list.filter { c -> three.none { it.equals(c, true) || it.equals("\u201C$c\u201D", true) } && !c.equals(typed, true) }
        return three + extras
    }

    private fun arrangeThree(typed: String, list: List<String>, auto: String?): List<String> {
        val emoji = if (prefs.emojiSuggestions) EmojiData.forWord(auto ?: typed) else null
        return if (auto != null) {
            val others = list.filter { !it.equals(typed, true) && it != auto }
            listOf("\u201C$typed\u201D", auto, emoji ?: others.getOrElse(0) { "" })
        } else {
            // Middle = the engine's best guess (often a completion of what you're typing).
            val best = list.firstOrNull() ?: typed
            val bestIsTyped = best.equals(typed, ignoreCase = true)
            val rest = list.filter { !it.equals(best, true) && !it.equals(typed, true) }
            val left = if (bestIsTyped) rest.getOrElse(0) { "" } else "\u201C$typed\u201D"
            val right = emoji ?: rest.firstOrNull { it != left } ?: ""
            listOf(left, if (bestIsTyped) typed else best, right)
        }
    }

    private fun showToolbarOrSuggestions() {
        if (panel != null) { topBar.mode = TopBarView.BarMode.TOOLBAR; return }
        val empty = currentSuggestions.all { it.isEmpty() }
        val wantToolbar = showToolbar || (empty && prefs.toolbar && composing.isEmpty())
        if (wantToolbar && !isPassword) {
            topBar.mode = TopBarView.BarMode.TOOLBAR
        } else {
            topBar.mode = TopBarView.BarMode.SUGGESTIONS
            topBar.suggestions = currentSuggestions
            // Bold middle = what the space bar will insert (an autocorrection or the typed word).
            val mid = currentSuggestions.getOrElse(1) { "" }
            topBar.emphasized = if (mid.isNotEmpty() && (autoCorrection != null || mid == composing.toString())) 1 else -1
        }
    }

    // ------------------------------------------------------------------
    // TopBarView.Listener
    // ------------------------------------------------------------------

    override fun onSuggestion(index: Int) {
        val ic = currentInputConnection ?: return
        var s = currentSuggestions.getOrElse(index) { "" }
        if (s.isEmpty()) return
        if (s == SCAN_CHIP) {
            val text = scanText ?: return
            scanText = null
            val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
            ic.commitText((if (before.isNotEmpty() && !before[0].isWhitespace()) " " else "") + text, 1)
            refreshSuggestions()
            return
        }
        editWord?.let { replaceTappedWord(it, s); return }
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
                    if (!noLearning && s.all { it.isLetter() || it == '\'' }) engineHandler.post { engine.learnGlide(s); engine.rejectGlide(glided); engine.learnWord(s, p) }
                    glideAlternatives = (listOf(glided) + glideAlternatives).filter { it != s }
                    remember(s, glideAlternatives)
                    showGlideAlternatives(s)
                }
            }
            return
        }
        if (s in PUNCTUATION && !tokenSuggestions) { insertPunctuation(s); return }
        if (tokenSuggestions) {
            // Replace what you've typed of this entry with the whole entry.
            ic.finishComposingText(); composing.clear()
            val token = currentToken()
            if (token.isNotEmpty()) ic.deleteSurroundingText(token.length, 0)
            ic.commitText(s, 1)
            tokenSuggestions = false
            refreshSuggestions()
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
            s = capitalI(s)
            // A predicted word picked at the start of a sentence gets its capital.
            if (typed.isEmpty() && shift == ShiftState.ON && s.isNotEmpty() && s[0].isLowerCase()) s = s.replaceFirstChar { it.uppercase() }
            remember(s, listOf(typed) + currentSuggestions)
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

    /** "word |" + "," → "word, " (the space moves after the mark). */
    private fun insertPunctuation(p: String) {
        val ic = currentInputConnection ?: return
        if (composing.isNotEmpty()) finishWord(autocorrect = true)
        if (p == "'") {
            // "word |" + ' → "word'|": the apostrophe attaches to the word with no
            // space, and the word stays open so "s", "t", "ll" … complete it.
            val before = ic.getTextBeforeCursor(64, 0)?.toString().orEmpty()
            val trimmed = before.trimEnd(' ')
            val spaces = before.length - trimmed.length
            val word = trimmed.takeLastWhile { it.isLetterOrDigit() || it == '\'' }
            if (word.isNotEmpty() && spaces <= 1 && !noSuggestions) {
                ic.deleteSurroundingText(word.length + spaces, 0)
                composing.clear(); composing.append(word).append('\'')
                ic.setComposingText(composing, 1)
                prevWord = null
            } else {
                if (spaces == 1) ic.deleteSurroundingText(1, 0)
                ic.commitText("'", 1)
            }
            phantomSpace = false; lastCorrection = null; lastGlide = null
            refreshSuggestions()
            return
        }
        val before = ic.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        if (before == " ") ic.deleteSurroundingText(1, 0)
        ic.commitText("$p ", 1)
        phantomSpace = false
        lastCorrection = null
        lastGlide = null
        if (p in listOf(".", "?", "!")) prevWord = null
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
            TopBarView.Tool.SCAN -> {
                if (composing.isNotEmpty()) finishWord(autocorrect = true)
                startActivity(Intent(this, ScanActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** The search / prompt line changed: redraw it, and update emoji results live. */
    /** Where you're typing in the search / prompt box. */
    private var targetCursor = 0

    private fun targetInsert(text: String) {
        targetCursor = targetCursor.coerceIn(0, targetText.length)
        targetText.insert(targetCursor, text)
        targetCursor += text.length
        targetChanged()
    }

    /** You tapped inside the search / prompt box: move the cursor there. */
    override fun onSearchCursor(index: Int) {
        if (target == Target.NONE) return
        targetCursor = index.coerceIn(0, targetText.length)
        targetChanged()
    }

    private fun targetChanged() {
        targetCursor = targetCursor.coerceIn(0, targetText.length)
        topBar.searchText = targetText.toString()
        topBar.searchCursor = targetCursor
        if (target == Target.EMOJI_SEARCH) showEmojiResults(targetText.toString())
    }

    private fun showEmojiResults(q: String) {
        if (!::emojiStrip.isInitialized) return
        emojiStripRow.removeAllViews()
        val found = com.matrixkey.engine.EmojiData.search(q)
        val d = resources.displayMetrics.density
        if (q.isBlank() || found.isEmpty()) {
            emojiStripRow.addView(TextView(this).apply {
                text = if (q.isBlank()) "Type to find an emoji" else "No emoji for “${q.trim()}”"
                setTextColor(theme.sub); textSize = 14f
                setPadding((16 * d).toInt(), 0, 0, 0)
            })
        } else found.take(40).forEach { e -> emojiStripRow.addView(stripEmoji(e, q)) }
        emojiStrip.setBackgroundColor(theme.bg)
        emojiStrip.visibility = View.VISIBLE
        emojiStrip.scrollTo(0, 0)
    }

    /** One emoji in the search strip: tap inserts it; long-press shows its skin tones. */
    private fun stripEmoji(e: String, q: String, toneChoice: Boolean = false): View {
        val d = resources.displayMetrics.density
        val shown = if (toneChoice) e else com.matrixkey.panels.EmojiPanel.withTone(prefs.sp, e)
        return TextView(this).apply {
            text = shown; textSize = 26f
            gravity = android.view.Gravity.CENTER
            setPadding((10 * d).toInt(), 0, (10 * d).toInt(), 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setOnClickListener {
                keyboard.feedback.key(it)
                rememberEmoji(shown)
                currentInputConnection?.commitText(shown, 1)
                if (toneChoice) showEmojiResults(q)
            }
            if (!toneChoice && com.matrixkey.panels.EmojiPanel.toneBase(e) != null) setOnLongClickListener {
                keyboard.feedback.key(it)
                showToneChoices(e, q)
                true
            }
        }
    }

    /** Swaps the search strip for the emoji's skin tones until you pick one or go back. */
    private fun showToneChoices(e: String, q: String) {
        val d = resources.displayMetrics.density
        emojiStripRow.removeAllViews()
        emojiStripRow.addView(TextView(this).apply {
            text = "‹"; textSize = 26f; setTextColor(theme.sub)
            gravity = android.view.Gravity.CENTER
            setPadding((14 * d).toInt(), 0, (10 * d).toInt(), 0)
            setOnClickListener { keyboard.feedback.key(it); showEmojiResults(q) }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        for (option in com.matrixkey.panels.EmojiPanel.toneOptions(e)) {
            val v = stripEmoji(option, q, toneChoice = true)
            v.setOnClickListener {
                keyboard.feedback.key(it)
                com.matrixkey.panels.EmojiPanel.saveTone(prefs.sp, e, option)
                rememberEmoji(option)
                currentInputConnection?.commitText(option, 1)
                showEmojiResults(q)
            }
            emojiStripRow.addView(v)
        }
        emojiStrip.scrollTo(0, 0)
    }

    private fun rememberEmoji(e: String) {
        val sp = prefs.sp
        val recents = sp.getString("recent_emoji", "")!!.split(" ").filter { it.isNotEmpty() && it != e }.toMutableList()
        recents.add(0, e)
        sp.edit().putString("recent_emoji", recents.take(32).joinToString(" ")).apply()
    }

    override fun onSearchSubmit() {
        val q = targetText.toString().trim()
        when (target) {
            Target.EMOJI_SEARCH -> {
                closeTarget()
                showEmoji(q)
            }
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

    /** Swipe down on the suggestion bar / toolbar: close the keyboard. */
    override fun onSwipeDown() {
        keyboard.feedback.tick(keyboard)
        if (composing.isNotEmpty()) finishWord(autocorrect = false)
        closeTarget(redraw = false)
        if (voice.listening) voice.stop()
        closePanels()
        requestHideSelf(0)
    }

    /** The keyboard button in the search line goes straight back to typing. */
    override fun onSearchClose() = onBackToKeyboard()

    override fun onBackToKeyboard() {
        closeTarget(redraw = false)
        if (voice.listening) voice.stop()
        closePanels()
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

    private var targetDeleteWords = 0

    /** "find funny cats" minus 1 word → "find funny " (keeps the space so typing continues). */
    private fun dropLastWords(text: String, words: Int): String {
        if (words <= 0) return text
        var i = text.length
        var count = 0
        while (count < words && i > 0) {
            while (i > 0 && text[i - 1].isWhitespace()) i--
            while (i > 0 && !text[i - 1].isWhitespace()) i--
            count++
        }
        return text.substring(0, i)
    }

    private fun openTarget(t: Target, hint: String, icon: Icon, initial: String = "") {
        targetDeleteWords = 0
        target = t
        targetText.clear(); targetText.append(initial)
        targetCursor = initial.length
        topBar.searchCursor = targetCursor
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
        if (::emojiStrip.isInitialized) emojiStrip.visibility = View.GONE
        targetText.clear()
        if (redraw && ::topBar.isInitialized) {
            topBar.mode = TopBarView.BarMode.SUGGESTIONS
            refreshSuggestions()
        }
    }

    // ------------------------------------------------------------------
    // Panels
    // ------------------------------------------------------------------

    private fun ui() = PanelUi(this, theme, FontManager.keyTypeface(this, prefs.keyFont)) { v -> keyboard.feedback.key(v) }

    private fun showPanelView(v: View) {
        val h = keyboard.height.takeIf { it > 0 } ?: keyboard.measuredHeight
        (v.parent as? ViewGroup)?.removeView(v)
        panel?.let { if (it !== v) content.removeView(it) }
        content.addView(v, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h))
        keyboard.visibility = View.INVISIBLE
        panel = v
        // Keep the toolbar up, with a keyboard button to get back to the keys.
        topBar.activeTool = when (v) {
            is EmojiPanel -> TopBarView.Tool.EMOJI
            is GifPanel -> TopBarView.Tool.GIF
            is ClipboardPanel -> TopBarView.Tool.CLIPBOARD
            is AiPanel -> TopBarView.Tool.AI
            else -> null
        }
        topBar.panelOpen = true
        topBar.mode = TopBarView.BarMode.TOOLBAR
    }

    private fun hidePanel() {
        panel?.let { content.removeView(it) }
        panel = null
        if (::topBar.isInitialized) { topBar.panelOpen = false; topBar.activeTool = null }
        if (::keyboard.isInitialized) keyboard.visibility = View.VISIBLE
    }

    private fun closePanels() {
        hidePanel()
        gifPanel = null
        aiPanel = null
        refreshSuggestions()
    }

    private fun showEmoji(query: String = "") {
        if (composing.isNotEmpty()) finishWord(autocorrect = true)
        showPanelView(EmojiPanel(this, ui(),
            onEmoji = { e -> currentInputConnection?.commitText(e, 1); phantomSpace = false },
            onBackspace = { onBackspace() },
            onClose = { closePanels() },
            onSearchTap = {
                openTarget(Target.EMOJI_SEARCH, "Search emoji", Icon.SEARCH)
                showEmojiResults("")
            },
            results = if (query.isNotBlank()) com.matrixkey.engine.EmojiData.search(query) else null,
            query = query))
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
