# Fixd Messaging

An open-source Android SMS/MMS app from the Fixd.fun team. Built so people who
use Samsung devices (or any Android device) have a real choice for texting
beyond the bundled defaults.

## Features (target v1)

- Default SMS/MMS handler  send and receive on your real phone number.
- Conversation list and thread UI built with Jetpack Compose + Material 3.
- Picture send / receive / manage with the modern Photo Picker.
- Built-in Fixd Keyboard (Android InputMethodService) with an emoji shortcut
  strip; a richer layout, suggestion strip, and full emoji panel land in
  follow-up commits.
- Emoji rendering everywhere via AndroidX `emoji2-bundled` so older devices
  display modern emoji correctly.
- Spell-check / autocorrect via the platform `SpellCheckerSession` API
  (wired into the keyboard service).
- Backup & transfer to a single human-readable JSON document the user owns.
- Fixd-themed Material 3 palette pulled from the public Fixd.fun branding.

## Project layout

```
app/
  src/main/AndroidManifest.xml         SMS/MMS default-handler intent filters
  src/main/java/app/fixd/messaging/
    FixdApp.kt                         Application: initializes EmojiCompat
    ui/MainActivity.kt                 Launcher; requests SMS role
    ui/ConversationListScreen.kt       Compose UI for the inbox
    ui/ComposeMessageActivity.kt       New-message screen with attachments
    sms/SmsSender.kt                   SmsManager wrapper + Sent provider
    sms/SmsDeliverReceiver.kt          Inbound SMS  Telephony.Sms.Inbox
    sms/HeadlessSmsSendService.kt      Quick-reply service required by the OS
    mms/MmsSender.kt                   Outbound MMS scaffold (PDU TODO)
    mms/MmsDeliverReceiver.kt          WAP-PUSH receiver scaffold
    keyboard/FixdKeyboardService.kt    InputMethodService for the Fixd IME
    backup/BackupManager.kt            JSON export / additive restore
    data/Conversation.kt               Repository: reads Telephony provider
    theme/Theme.kt                     Compose Material 3 theme (Fixd colors)
  src/main/res/                        Strings, colors, themes, IME meta, icons
```

## Build the APK

Requires JDK 17 and the Android SDK. From the repo root:

```
./gradlew :app:assembleDebug          # debug APK
./gradlew :app:assembleRelease        # unsigned release APK
```

The artifact lands in `app/build/outputs/apk/`.

## CI

`.github/workflows/android.yml` runs the debug build on every push and
uploads the APK as a workflow artifact, so a working APK is always one click
away from the GitHub Actions tab even without a local Android Studio install.

## License

MIT  see `LICENSE`.

## Status

This is an early scaffold. The wiring for the default-SMS-handler role and
the IME is real and runnable; the MMS PDU pipeline and the keyboard
autocorrect strip are documented stubs that subsequent commits fill in.

_A Fixd.fun project. Built in public so users can audit exactly what runs on
their phone._
