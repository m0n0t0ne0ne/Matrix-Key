# Matrix Key

A dot-matrix keyboard for Android, built for Nothing phones. It has Gboard-level features and offline learning, plus free AI writing and GIF search.

## Install

1. On your phone, open **[Releases](../../releases/latest)** and download `MatrixKey-*.apk`.
2. Open the file and allow installing from your browser or Files app if Android asks. Requires Android 10+ on a 64-bit phone.
3. Open **Matrix Key**, then follow the setup steps at the top: turn it on, switch to it, and allow the microphone.
4. Optionally check the download: each release lists the APK's SHA-256 and the signing certificate's fingerprint, which is the same for every release.

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
- **Proofreading:** tap a word in your text to get its suggestions back and swap it.
- **Number pad** (the 1234 key on the symbols page) and **quick math**: type `5+5(6/2)=` and the answer appears.

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
- **Scan text**: take a photo or pick an image and insert the text it contains (read on the phone).

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
- Run `./gradlew assembleRelease`. Without a key, the build is signed with your debug key.
- To sign with your own key, set `KEYSTORE` (path to a `.jks`) and `KEYSTORE_PASS` (its password; key alias `matrixkey`).
- The build downloads the Doto font (SIL OFL 1.1) and an English word-frequency list ([FrequencyWords](https://github.com/hermitdave/FrequencyWords), CC BY-SA 4.0) into `app/src/main/assets`.

### Releases

Releases are built and signed by GitHub Actions, never on a personal machine:

- **Build check** (`.github/workflows/ci.yml`) builds every push and pull request. Nothing is published.
- **Release** (`.github/workflows/release.yml`): running it from the Actions tab, or pushing a tag such as `v1.0`, builds that exact commit, signs it with the key stored in the repository's encrypted secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASS`), and publishes the release with the APK, its SHA-256 and the signing certificate's fingerprint. Release notes come from `docs/release-notes/<tag>.md`.

To release: bump `versionCode` and `versionName` in `app/build.gradle.kts` and add the notes file, then run **Actions › Release › Run workflow** (it tags the current `main` commit as `v<versionName>`), or push that tag.

## Security and licence

See [SECURITY.md](SECURITY.md) for how to report a problem and how releases are signed.

The code in this repository is released under the [MIT Licence](LICENSE) and comes with no warranty. Fonts, the word list and libraries keep their own licences; see [THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md). Unofficial app, not affiliated with Nothing.
