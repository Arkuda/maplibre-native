<p align="center">
  <img src="https://github.com/user-attachments/assets/7ff2cda8-f564-4e70-a971-d34152f969f0#gh-light-mode-only" alt="MapLibre Logo" width="200">
  <img src="https://github.com/user-attachments/assets/cee8376b-9812-40ff-91c6-2d53f9581b83#gh-dark-mode-only" alt="MapLibre Logo" width="200">
</p>

# MapLibre Native — Kotlin rewrite

This fork is a focused rewrite of MapLibre Native for **Android** and **iOS**. The C++ engine is being replaced by a Kotlin Multiplatform core; desktop, Node.js, Qt, Linux, Windows, and macOS platform products are out of scope.

The platform boundary is typed Kotlin objects, not JSON. Android and iOS call the shared `MapEngine` directly with `StyleSpec`, `SourceSpec`, and `LayerSpec`; JSON is only parsed at the input edge when loading a style document.

## Status

- **Shared core:** Kotlin Multiplatform (`commonMain`) port of projection, tile identifiers and loading, style model, expressions, MVT and GeoJSON decoding, geometry buckets, and a CPU reference renderer.
- **Android:** `GLSurfaceView` host and GLES 3.0 texture upload path in `kotlin/androidApp`. The view calls the shared `MapEngineImpl` directly.
- **iOS:** static Kotlin/Native framework targets for device and simulator, with a Metal platform context. A UIKit or SwiftUI host view is not yet implemented.
- **Rendering:** the shared CPU rasterizer currently renders typed decoded vector tiles. Native GPU drawables remain to be ported.

See [`kotlin/README.md`](kotlin/README.md) for the detailed porting status and architecture.

## Repository layout

```text
kotlin/
├── core/          Shared Kotlin Multiplatform library
│   ├── commonMain Typed map engine, style, tiles, renderer, geometry
│   ├── androidMain GLES platform context
│   └── iosMain     Metal platform context
└── androidApp/    Android GLSurfaceView demonstration host
```

The legacy C++ source remains in the repository as a porting reference. New engine work belongs in `kotlin/`.

## Build

Requirements:

- JDK 17
- Android SDK API 35 for Android builds (`ANDROID_HOME` or `kotlin/local.properties`)
- macOS and Xcode for iOS framework builds

```bash
cd kotlin

# JVM tests for the shared core
./gradlew :core:jvmTest

# Android AAR
./gradlew :core:assembleRelease

# Android demonstration application
./gradlew :androidApp:assembleDebug

# iOS simulator framework (macOS only)
./gradlew :core:linkDebugFrameworkIosSimulatorArm64
```

The Android artifact is written to:

```text
kotlin/core/build/outputs/aar/core-release.aar
```

## Continuous integration

[`.github/workflows/kotlin-library.yml`](.github/workflows/kotlin-library.yml) builds and uploads:

- `maplibre-kotlin-android-aar` after JVM tests and Android AAR assembly;
- `maplibre-kotlin-ios-framework` after building the arm64 iOS simulator framework.

The workflow runs for pull requests and pushes that modify `kotlin/**`.

## Contributing

Keep platform/engine APIs typed. Do not add JSON serialization, JNI bridges, or stringly typed protocols between a platform host and `MapEngine`.

Follow the existing Kotlin Multiplatform source-set split: portable code belongs in `commonMain`; Android and iOS interop belongs in `androidMain` and `iosMain` respectively. Run the relevant Gradle task before submitting a change.

- [`CONTRIBUTING.md`](CONTRIBUTING.md)
- [`ARCHITECTURE.md`](ARCHITECTURE.md) — legacy C++ architecture reference
- [`LICENSE.md`](LICENSE.md)
