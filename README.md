# TempoShift for Android
1. Open this folder in Android Studio (Koala or newer); let Gradle sync.
2. Run on a device, or Build > Build APK(s).
First launch: menu > Folders > add folder, and pick your Music folder.
UI: app/src/main/assets/index.html (your original file plus an Android storage layer).

## Build without Android Studio (GitHub Actions)
1. Create a GitHub repo and upload everything in this folder (including the hidden .github folder).
2. Open the repo's Actions tab > "Build APK" > Run workflow (it also runs on every push).
3. When it finishes (about 3-5 minutes), open the run and download the "TempoShift-apk" artifact. Unzip it, install app-debug.apk.
