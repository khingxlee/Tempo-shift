# TempoShift (native Android)
Kotlin + Jetpack Compose + Media3. Builds on GitHub Actions (.github/workflows/build.yml) or Android Studio.

## Features
Library (songs / albums / artists / playlists), independent tempo + pitch, A-B loop, waveform, synced lyrics with
on-device translation to English and left/centre alignment, equalizer + bass/loudness boost (Now Playing menu),
tag editing (MP3 files are rewritten; other formats are kept inside the app), save album cover in original quality,
per-song tempo/pitch memory, backup/restore, Android Auto + Wear OS media browsing, six languages.

## Package layout (app/src/main/java/app/temposhift)
- MainActivity.kt          entry point, "Open with", battery prompt
- service/PlayerService    ExoPlayer + media session + Android Auto browse tree;  service/EqEngine  audio effects
- player/PlayerState       view-model: playback, tempo/pitch, loop, playlists, lyrics, waveform
- data/                    Library (MediaStore), AppDb (SQLite), Prefs, Lyrics, LyricsTranslator, Waveform, TagEditor, CoverSaver
- ui/, ui/screens/         theme, motion helpers, library, now playing, lyrics, settings, equalizer, tag editor

## App icon
See app/src/main/res/ICON_README.txt

## Signing
* Debug/test builds use app/debug.keystore (committed on purpose) so every build installs over the previous one.
* Real release key (do this once, keep the file + passwords safe - you cannot update the app without them):
    keytool -genkeypair -v -keystore temposhift-release.jks -alias temposhift -keyalg RSA -keysize 2048 -validity 10000
  Turn the file into text:   Linux/Mac:  base64 -w0 temposhift-release.jks
                             Windows PowerShell:  [Convert]::ToBase64String([IO.File]::ReadAllBytes("temposhift-release.jks"))
  In GitHub: repo Settings > Secrets and variables > Actions > New repository secret, add:
    KEYSTORE_BASE64 (the text above), KEYSTORE_PASSWORD, KEY_ALIAS (temposhift), KEY_PASSWORD
  The workflow then signs app-release.apk with it automatically.

Round 1: release build is minified (R8) and arm64-only for a small APK - install app-release.apk. Native library, albums, side menu, lyrics page, centred sliders.

Round 2: full Settings (editor controls, pitch and tempo, colors with light/dark/system themes and accent palettes, library scan/refresh), compact rounded dialogs.
