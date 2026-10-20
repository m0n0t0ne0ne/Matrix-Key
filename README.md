# Matrix Key

A dot-matrix keyboard for Android, built for Nothing phones. It has Gboard-level features and offline learning, plus free AI writing and GIF search.

## Install

1. On your phone, open **[Releases → latest](../../releases/tag/latest)** and download `MatrixKey.apk`.
2. Open the file and allow installing from your browser or Files app if Android asks.
3. Open **Matrix Key**, then follow the three setup steps at the top: turn it on, switch to it, and allow the microphone.

Every push to `main` rebuilds the APK automatically with GitHub Actions. You can also download it from the **Actions** tab under *Artifacts*.

## Features

**Look**
- Uses Nothing's own **NDot** system font for the keys when your phone has it. Otherwise it falls back to the open-source *Doto* dot-matrix font.
- Dotted icons, a red action key, and a suggestion strip that matches the dot style.
- Themes: Dynamic Color (Material You), System Auto, Light, Dark, Pure Black and Midnight.
- **Import Rboard / Gboard `.pack` themes** without root. The importer maps the theme's colors and background image onto Matrix Key.
- Key shape: Rounded, Rectangular, or **None** (floating letters).
- Key font: NDot, System, or a font file you import.
- Accent color: auto, or pick one.
- Live preview with Cancel and Apply.

**Typing**
- Number row, symbol hints, and long-press for symbols and accents.
- Key popups, one-handed mode, and adjustable height.
- Sound and vibration with adjustable strength.
- Suggestions, next-word prediction, emoji suggestions and autocorrect. Autocorrect knows which keys are next to each other, so near-miss taps are fixed.
- **Learns from you, on your phone only:**
  - your words and names
  - the misspellings you tend to make
  - your corrections and undos
  - where your thumbs actually land on each key
- Tap backspace right after an autocorrection to undo it.

**Glide typing**
- Swipe-to-type that matches the shape of your swipe, with alternatives in the suggestion strip.
- The glide trail matches your theme: dotted with NDot, solid otherwise, in the accent color. You can adjust its thickness.
- Slide on the spacebar to move the cursor. **Every one-letter move gives a haptic tick.**
- Swipe left from backspace to select whole words, then release to delete them.

**Toolbar**
- Emoji panel with recent emoji.
- Clipboard history with pinning and auto-clear. It skips passwords and one-time codes.
- **GIF search** with **GIPHY** or **KLIPY** (free key). If an app can't accept GIFs, it pastes the link instead.
- **AI writing**: Proofread, Formal, Casual, Shorter, Friendlier, Expand, Emojify, Translate, or a custom instruction.
  - Uses the **Gemini free tier**, and switches to the **Groq free tier** when Gemini's limit is hit.
  - Never runs in password fields, and sends nothing until you tap.
- Voice typing with Android's on-device speech recognizer.

## Free API keys

| Feature | Where to get a key | Paste it in |
|---|---|---|
| AI writing | [Google AI Studio](https://aistudio.google.com/apikey) (Gemini) | Settings › AI writing |
| AI backup | [Groq Console](https://console.groq.com/keys) | Settings › AI writing |
| GIFs | [GIPHY Developers](https://developers.giphy.com/dashboard/) (choose **API**) or [KLIPY Partner](https://partner.klipy.com) | Settings › GIFs |

On free tiers, providers may use the text you send to improve their models. Only text you choose to send is sent.

## Privacy

- Typing, learning, the dictionary and clipboard history stay on the device.
- The internet permission is used only for GIF search and AI writing.
- The keyboard doesn't learn in fields that ask for no personalization, such as incognito tabs.

## Building it yourself

- Requires JDK 17 and the Android SDK (API 35).
- Run `./gradlew assembleRelease`.
- The build downloads the Doto font (SIL OFL 1.1) and an English word-frequency list ([FrequencyWords](https://github.com/hermitdave/FrequencyWords), CC BY-SA 4.0) into `app/src/main/assets`.

## Not included yet

- Scan text (OCR) from the toolbar.
- Extra languages beyond English.
- Floating and split keyboard.
