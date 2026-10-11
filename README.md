# Sound Sleep

## Overview

**Sound Sleep** (`com.eggwall.SoundSleep`) is a minimalist night-clock and sleep-aid application for Android. It is designed to sit on a nightstand all night with minimal visual distraction while optionally playing soothing audio:

- **All-Night Clock Display**: Displays the current time in a large `h:mm` format (`120dip`) on a pure black background while disabling the device's screen timeout (`FLAG_KEEP_SCREEN_ON` and `android:keepScreenOn="true"`).
- **Immersive Fullscreen**: Hides the title/action bar, system status bar, and on-screen navigation buttons (Home, Recents, Back) so the clock uses the entire screen without bright system UI chrome.
- **Screen Burn-In Protection & Progressive Dimming**: Every 10 minutes, the clock and control icons smoothly animate to new random positions on screen, and the icons progressively dim so the display becomes as dark as possible overnight. Touching a control restores icon brightness.
- **White Noise & Sleep Music**:
  - **Top half of screen (Music Note)**: Plays random tracks from `[ExternalStorage]/Music/sleeping` if present, or falls back to a bundled looping jingle (`res/raw/jingle.mp3`).
  - **Bottom half of screen (Rain Cloud)**: Loops bundled white noise (`res/raw/noise.ogg`).
  - Tapping the active half a second time stops playback and returns to silence.

---

## Device & Android Version Compatibility

- **Supported Device Architectures**: All Android CPU architectures (`armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`) across phones and tablets, as the app compiles to pure Dalvik/ART bytecode (`classes.dex`) with no native NDK binary dependencies.
- **Minimum Android Version**: **Android 4.4 (KitKat, API level 19)** (`minSdkVersion="19"`).
- **Target Android Version**: **Android 15 (API level 35)** (`targetSdkVersion="35"`).
- **Maximum Android Version**: **No upper limit** (`maxSdkVersion` is not set). The app is forward-compatible with Android 15+ and automatically adapts to modern platform APIs:
  - Uses `WindowInsetsController` on Android 11+ (API 30+) and `SYSTEM_UI_FLAG_IMMERSIVE_STICKY` on Android 4.4–10 (API 19–29).
  - Uses `NotificationChannel` on Android 8.0+ (API 26+).
  - Uses `PendingIntent.FLAG_IMMUTABLE` on Android 6.0+ (API 23+).
  - Uses `Context.RECEIVER_NOT_EXPORTED` on Android 13+ (API 33+).
  - Stores `resources.arsc` uncompressed and 4-byte aligned as required on Android 11+ (API 30+).

---

## Architecture

The project contains two primary components in `src/com/eggwall/SoundSleep/` and a single main layout in `res/layout/main.xml`:

1. **`SleepActivity` (`src/com/eggwall/SoundSleep/SleepActivity.java`)**:
   - Hosts the fullscreen UI defined in `res/layout/main.xml`:
     - A root `FrameLayout` (`@+id/toplevel`) with a black background and `android:keepScreenOn="true"`.
     - Two invisible full-width `Button` regions stacked vertically (`@+id/startMusic` on the top half, `@+id/startWhiteNoise` on the bottom half) that capture taps across the top and bottom halves of the screen.
     - Two status `ImageView` icons (`@+id/note` for music and `@+id/cloud` for white noise) that swap to a pause icon (`@drawable/pause`) when their respective audio mode is active.
     - A framework `TextClock` (`@+id/clock`) configured to display `h:mm` in both 12-hour and 24-hour modes.
   - Manages immersive fullscreen mode and `FLAG_KEEP_SCREEN_ON` in `onCreate()`, `onResume()`, and `onWindowFocusChanged()`.
   - Schedules a periodic `Runnable` (`mChangeClockLocation`) on the main-thread `Handler` every 10 minutes (`DELAY = 10 * 60 * 1000` ms) to reposition the clock and icons and gradually reduce icon alpha.
   - Sends command `Intent`s (`MUSIC`, `WHITE_NOISE`, `GET_STATUS`) to `AudioService` and listens for package-scoped status broadcasts (`MESSAGE_SILENCE`, `MESSAGE_MUSIC`, `MESSAGE_WHITE_NOISE`) to keep the icons synchronized with playback state.

2. **`AudioService` (`src/com/eggwall/SoundSleep/AudioService.java`)**:
   - A background Android `Service` managing a `MediaPlayer` instance with a `PARTIAL_WAKE_LOCK` so audio continues playing even if the user switches away from the activity.
   - Handles state transitions between `SILENCE`, `MUSIC`, and `WHITE_NOISE`. Receiving the currently playing command a second time stops playback and stops the service.
   - Posts an ongoing notification (`NOTIFICATION_ID = 1`) while audio is playing that returns the user to `SleepActivity` when tapped, and broadcasts state updates back to `SleepActivity`.

---

## Build and Install Steps

### Prerequisites
- Java JDK (`javac`, `jar`, `keytool`)
- Apache Ant (`ant`)
- Android SDK with **Platform 35** (`platforms/android-35/android.jar`) and **Build-Tools 35.0.0** (`build-tools/35.0.0/`)

### 1. Configure SDK Location
Ensure `sdk.dir` in `local.properties` points to your Android SDK installation (or set the `ANDROID_HOME` environment variable):

```properties
sdk.dir=/usr/local/google/home/viki/Android/Sdk
```

*(If you use a different platform or build-tools version, update `target` and `build.tools.version` in `project.properties`.)*

### 2. Build the APK
From the project root directory:

- **Build for release**:
  ```bash
  ant release
  ```
  Produces a signed, zipaligned APK at `bin/BabyMusic-release.apk` (when signing keys are configured in `ant.properties`) or an unsigned APK at `bin/BabyMusic-release-unsigned.apk`.

- **Build for debug**:
  ```bash
  ant debug
  ```
  Produces a debug-signed APK at `bin/BabyMusic-debug.apk` using `debug.keystore`.

- **Clean build artifacts**:
  ```bash
  ant clean
  ```

### 3. Install on a Device
Connect an Android device with USB debugging enabled (or start an emulator) and run:

```bash
adb install -r bin/BabyMusic-release.apk
```

---

## Production Signing Key Configuration

Release signing is configured in **`ant.properties`**.

For convenience during local testing, `ant.properties` is currently configured to use the local `debug.keystore`:

```properties
key.store=debug.keystore
key.alias=androiddebugkey
key.store.password=android
key.alias.password=android
```

To sign release builds with your **production keys**:

1. Edit **`ant.properties`** to point `key.store` and `key.alias` to your production keystore and alias, and remove the plaintext password properties so they are not committed to version control:
   ```properties
   key.store=/path/to/your/production-release.keystore
   key.alias=your_production_key_alias
   ```
2. Run `ant release`. The `-sign-release` target in `build.xml` will interactively prompt you for your keystore password and key alias password, then sign and output `bin/BabyMusic-release.apk`.
   - For non-interactive/CI builds, you can pass the passwords on the command line (`ant release -Dkey.store.password=... -Dkey.alias.password=...`) or store them in `local.properties` (which is excluded from version control).