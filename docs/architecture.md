# Architecture

Fixd Messaging is a single-module Android app written in Kotlin and Jetpack
Compose. It ships:

- An `Application` class that initializes `EmojiCompat` so emoji render
  consistently across Android versions.
- A launcher `Activity` that requests the SMS handler role and shows the
  conversation list. Until the role is granted the UI is read-only.
- Three platform integration points required by Android to be a default SMS
  app: a `SMS_DELIVER` receiver, a `WAP_PUSH_DELIVER` receiver, and a
  `RESPOND_VIA_MESSAGE` service.
- An `InputMethodService` so the user can opt in to the bundled Fixd Keyboard.
- A `BackupManager` that exports the platform Telephony provider to JSON the
  user owns and can move between devices.

No network calls leave the device by default. The only network side-effect is
the carrier SMS/MMS path itself, which is the user's own messaging plan.
