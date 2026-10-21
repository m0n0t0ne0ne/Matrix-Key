# Security

## Reporting a problem

Please report security issues privately using GitHub's
[**Report a vulnerability**](../../security/advisories/new) form rather than a public issue.

## What the app can do

- **Internet:** used only for GIF search and AI writing, and only when you use them. Requests go
  to the provider you chose (GIPHY or KLIPY; Gemini or Groq) with the API key you entered.
  AI writing never runs in password fields.
- **Permissions:** internet, vibration, and the microphone for voice typing. No location,
  contacts or storage permissions. Scan text uses the camera app or the photo picker, so it
  needs no camera permission; text recognition runs on the phone.
- **Data:** learned words, your dictionary, clipboard history and settings are stored privately
  in the app and excluded from backups. The keyboard does not learn in password fields or in
  fields that ask for no personalisation (such as incognito tabs).
- **Exposed components:** only the keyboard service (protected by Android's input-method
  permission) and the launcher screen. Every other screen is private.

## How releases are signed

Official releases are built and signed by the **Release** GitHub Actions workflow from the
tagged commit. The signing key is held only in the repository's encrypted secrets and on its
owner's offline backup; it never appears in this repository.

Every release lists the signing certificate's SHA-256 fingerprint. It is the same for every
release; an APK signed with a different certificate is not an official build.

Test builds made before 1.0 were signed with a key that was published in this repository's
history. That key is retired and must not be trusted.

## Supply chain

GitHub Actions are pinned to commit hashes. Libraries come from Google's Maven repository and
Maven Central at fixed versions.
