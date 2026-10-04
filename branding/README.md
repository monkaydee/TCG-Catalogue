# CardNavo brand assets

Original card-and-navigation identity in navy, mint and cyan. The two trading
cards identify the category; the directional cut in the N identifies navigation.

## Start here

| Need | File |
|---|---|
| Transparent logo for dark backgrounds | `logos/cardnavo-logo-dark.png` or `.svg` |
| Transparent logo for light backgrounds | `logos/cardnavo-logo-light.png` or `.svg` |
| Logo on the brand background | `logos/cardnavo-logo-on-navy.png` |
| Isolated symbol | `logos/cardnavo-mark.svg` or `cardnavo-mark-1024.png` |
| Google Play icon | `icons/cardnavo-play-icon-512.png` |
| Large icon | `icons/cardnavo-icon-1024.png` |
| Watch the startup reveal | `animation/cardnavo-startup-1080x1920.mp4` |
| Looping small preview | `animation/cardnavo-startup-preview.gif` |
| Full startup animation, editable vector format | `animation/cardnavo-startup-lottie.json` |
| Compact icon-only animation | `animation/cardnavo-icon-lottie.json` |
| Reduced-motion / final frame | `animation/cardnavo-startup-static.png` |
| Native Android assets | `android/res/` |

SVG assets have outlined lettering: no font installation or remote font is needed.
The PNG exports have real alpha; transparency is not painted checkerboard artwork.
ImageGen originals and the normalized vector geometry are included under `sources/`.
The original generated artwork was converted to flat paths and one consistent
palette for production exports. The rejected first render is not included.

## Brand use

| Color | Hex | Use |
|---|---|---|
| Deep navy | `#0B1426` | App and icon background |
| Mint teal | `#29DBC6` | Navigation N and Navo lettering |
| Pale cyan | `#78CBFF` | Rear card |
| Near-white | `#F4F8FF` | Card outlines and Card lettering |

Use the symbol alone in the launcher icon; use the full name in headers and
marketing. Keep the proportions fixed. Leave clear space at least as wide as
the N's upright stroke. Keep body copy and buttons in the app's normal UI font.
The light variant changes white elements to navy for light backgrounds.

## Animation

The full reveal is 1.2 seconds, silent, 60 fps, with no external images or fonts:

- 0.00–0.37 s: card outlines and rear card fade in and settle upward.
- 0.20–0.57 s: the navigation N appears.
- 0.38–0.78 s: CardNavo fades in beneath the symbol.
- 0.78–1.20 s: the complete identity holds.

The GIF preview adds a short final-frame hold and repeats. The MP4 and Lottie
composition contain the actual 1.2-second reveal. Play the app animation once,
then show the app. Use the static artwork when reduced motion is requested.
Do not add a fixed launch delay just to finish playing the animation.

## Android icon integration

1. Copy the contents of `android/res/` into the app module's `src/main/res/`.
2. Update the existing application element in `AndroidManifest.xml`:

```xml
android:icon="@mipmap/ic_cardnavo"
android:roundIcon="@mipmap/ic_cardnavo_round"
android:label="@string/cardnavo_app_name"
```

The app's package can remain `com.monkaydee.tcgcatalogue`; the public name can
be CardNavo. The uploaded APK contains `Theme.TcgCatalogue`, which the supplied
starting theme uses as its post-splash theme. If the source has changed that
theme name, adjust the two `postSplashScreenTheme` references accordingly.

The adaptive foreground uses 108×108 dp with the artwork inside the central
60×60 dp area, within Android's 66×66 dp safe area. Color foreground, solid
background, Android 13 themed monochrome layer, and legacy 48–192 px icons
are included. The Google Play PNG is 512×512, RGBA, sRGB, below 1 MB, square,
without pre-rounded outside corners or an external shadow.

## Native Android splash: recommended route

For the launcher activity, use:

```xml
android:theme="@style/Theme.CardNavo.Starting"
```

Use the existing AndroidX SplashScreen integration, calling
`installSplashScreen()` before `super.onCreate(savedInstanceState)` in
`MainActivity`. If that call already exists, keep it rather than adding a second
call. The source needs the AndroidX `core-splashscreen` library.

The compact native icon animation lasts 540 ms. Android 12+ uses the supplied
AnimatedVectorDrawable; older versions use a complete static icon. The vector's
default state is also complete for devices with animations disabled.

The native splash shows the symbol, since Android masks its animated icon.
The full wordmark reveal is a separate optional app-content animation.
Let real app initialization determine when the splash exits. The system may
finish startup before the entire icon animation is visible.

## Optional full-wordmark reveal in Compose

`android/CardNavoStartup.kt` uses the existing Jetpack Compose stack and requires
no Lottie dependency. Copy it into the app's source under the matching package.

Show it only while the app is loading:

```kotlin
if (showStartup) {
    CardNavoStartup(
        isAppReady = appReady,
        onAppReady = { showStartup = false },
    )
} else {
    AppContent()
}
```

`showStartup` and `appReady` above are placeholders for your app state. Readiness
exits immediately; the example does not make a ready app wait for 1.2 seconds.
If initialization lasts longer, the final logo holds. Animator-disabled devices
show the final logo without moving it. Choose this route only when you want
the full wordmark screen; use a static system splash to avoid two consecutive
logo animations.

For a Lottie-based implementation, use either raw JSON file in `android/res/raw/`.
Set its background to navy, turn looping off, and use the static final frame when
animations are disabled. Lottie adds a dependency; the Compose and native routes
do not need it.

## Validation and limits

- Logo, square icon, circular mask, rounded mask and 32–96 px sizes inspected.
- XML, SVG and JSON assets parsed; resource references and vector bounds checked.
- MP4 dimensions, frame rate, duration and decoding checked; animation frames inspected.
- Assets are provided for integration. The uploaded APK has not been changed.
- Android resources and the optional Kotlin example have not been compiled or
  run inside your app; that final check requires its Android project source.

## Official references — checked 4 October 2026

- Google Play icon specifications: https://developer.android.com/distribute/google-play/resources/icon-design-specifications
- Adaptive icons: https://developer.android.com/develop/ui/compose/system/icon_design_adaptive
- Splash screens: https://developer.android.com/develop/ui/views/launch/splash-screen
- AndroidX SplashScreen: https://developer.android.com/reference/kotlin/androidx/core/splashscreen/SplashScreen
