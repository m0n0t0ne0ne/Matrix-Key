# Third-party notices

The MIT licence in [LICENSE](LICENSE) covers only the code written for this project.
These components keep their own licences.

## Downloaded at build time and included in the APK

| Component | Licence | Notes |
|---|---|---|
| **Doto** font, © The Doto Project Authors ([google/fonts](https://github.com/google/fonts)) | SIL Open Font License 1.1 | Fallback dot-matrix font, unmodified |
| **FrequencyWords** English word list, © Hermit Dave ([source](https://github.com/hermitdave/FrequencyWords)) | CC BY-SA 4.0 | `en_50k.txt`, unmodified; used for suggestions and glide typing |

## Libraries in the APK

| Component | Licence |
|---|---|
| AndroidX (Core, AppCompat, Preference, RecyclerView) | Apache 2.0 |
| Material Components for Android | Apache 2.0 |
| Kotlin standard library and kotlinx.coroutines | Apache 2.0 |
| Google ML Kit text recognition (via Google Play services) | [ML Kit terms](https://developers.google.com/ml-kit/terms) |

## Used from the phone, never included

**NDot**, Nothing's proprietary typeface. On Nothing phones the app loads the copy that
Nothing OS already has installed; the font is not part of this repository or the APK.
