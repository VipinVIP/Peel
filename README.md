<div align="center">

<img src="docs/icon.png" width="112" alt="Peel icon">

# Peel: Sticker Widget

**Transparent, tap-to-play stickers for your Android home screen.**

[![License: MIT](https://img.shields.io/badge/license-MIT-7C4DFF.svg)](LICENSE)
[![Android 12+](https://img.shields.io/badge/Android-12%2B-3DDC84.svg?logo=android&logoColor=white)](#download)
[![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF.svg?logo=kotlin&logoColor=white)](https://developer.android.com/compose)

Peel turns any GIF or WebP into a sticker on your home screen. Transparent parts show your
wallpaper, so there's no box or background, just the sticker. Tap it to play.

<a href="https://github.com/VipinVIP/Peel/releases/latest"><img src="docs/get-it-on-github.png" alt="Get it on GitHub" height="80"></a>

<sub>Requires Android 12 or newer.</sub>

</div>

## Demo and screenshots

<div align="center">
<img src="docs/demo.gif" width="24%" alt="Demo: tapping stickers on the home screen, then setting a sticker to play three times">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="24%" alt="Sticker settings">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="24%" alt="Getting started">
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="24%" alt="Dark theme">
<br>
<sub>Left: tap stickers to play them together, then set how many times each one plays (<a href="docs/demo.mp4">MP4</a>).</sub>
</div>

## Features

- **Truly transparent:** only the image itself is drawn on your home screen
- **Tap to play:** choose 1–50 plays per tap, or loop until you tap again
- **Play together:** several stickers can animate at the same time
- **GIF and WebP:** animated or still, up to 40 MB
- **Material You:** dynamic color from your wallpaper, light and dark theme
- **Private:** no internet permission, ads or tracking; your images never leave your phone

## How it works

1. **Add a sticker:** tap *Add sticker* in the app, or find *Peel sticker* in your launcher's widget list.
2. **Pick an image:** choose a GIF or WebP from your phone.
3. **Tap to play:** the sticker plays as many times as you set, then rests on its best frame.
4. **Change it anytime:** use *Change* in the app, or long-press the sticker and choose *Widget settings*.

## Building

```bash
./gradlew assembleRelease
```

The APK is written to `app/build/outputs/apk/release/`.

The app ships a [baseline profile](https://developer.android.com/topic/performance/baselineprofiles/overview)
for faster startup. To regenerate it after changing startup code, run
`./gradlew :app:generateReleaseBaselineProfile` with a device connected. The profiling tool needs
Android 13 or newer on that device; the app itself runs on Android 12+.

## Contributing

Bug reports, ideas and pull requests are welcome. Please [open an issue](https://github.com/VipinVIP/Peel/issues) first for larger changes.

## License

Peel is released under the [MIT License](LICENSE). © 2026 VipinVIP
