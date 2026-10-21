# Security

## Reporting a problem

Please report security issues privately using GitHub's
[**Report a vulnerability**](../../security/advisories/new) form rather than a public issue.

## What the app can do

- **Permissions:** internet, vibration, and the microphone. Nothing else: no location,
  contacts, camera, storage, phone or account permissions.
  - The **microphone** is optional and only asked for when you turn on voice typing.
    Speech is recognised on the phone by default.
  - **Scan text** uses your camera app or the photo picker, so it needs no camera or storage
    permission. Text is read on the phone, and the photo is deleted when you leave the screen.
  - Libraries add two harmless normal permissions: network-state checks (for Google Play
    services) and a signature-only permission AndroidX uses to protect its internal broadcasts.
- **Internet:** used only for GIF search and AI writing, and only when you use them. Every
  request is HTTPS (plain HTTP is refused) and goes straight to the provider you chose
  (GIPHY or KLIPY; Gemini or Groq) with your own API key. AI writing never runs in password
  fields and sends nothing until you tap an action. On the free tiers, providers may use what
  you send to improve their models.
- **Google ML Kit** (Scan text, through Google Play services) may send Google anonymous
  usage statistics about the text-recognition feature. Your images and text are not sent.
- **Data:** learned words, your dictionary, clipboard history, settings and API keys are
  stored privately in the app. They are excluded from cloud backups and from device-to-device
  transfer to a new phone. The keyboard does not learn in password fields or in fields that ask
  for no personalisation (such as incognito tabs), and clipboard history skips anything marked
  sensitive and anything that looks like a one-time code.
- **Imports** (Rboard themes, fonts, dictionary files) are size-limited and never unpacked to
  storage.
- **Exposed components:** only the keyboard service (protected by Android's input-method
  permission) and the launcher screen. Every other screen and provider is private.

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
