# maplibre-kotlin — Kotlin Multiplatform core

This directory is the Kotlin Multiplatform rewrite of the MapLibre Native
core. It replaces the C++ engine (`src/`, `include/`) with a pure-Kotlin
implementation that compiles to:

- **Android** — via `androidTarget()` (JVM bytecode, no JNI glue required)
- **iOS** — via Kotlin/Native (`iosArm64`, `iosSimulatorArm64`, `iosX64`)
  as a static framework `MapLibreKotlin`

## Module layout

```
kotlin/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── core/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/org/maplibre/kotlin/
│       │   ├── math/       — clamp, wrap, ceil_log2, angle conversions
│       │   ├── util/       — constants, LatLng, LatLngBounds, EdgeInsets,
│       │   │                 ScreenCoordinate, Projection (Web Mercator)
│       │   ├── tile/       — CanonicalTileID, decoded vector-tile data
│       │   ├── style/      — typed style model + StyleJsonParser
│       │   ├── renderer/   — shared CPU reference rasterizer
│       │   └── map/        — MapEngine contract + MapEngineImpl
│       ├── androidMain/    — GLES platform context
│       ├── iosMain/        — Metal platform context
│       └── commonTest/     — JVM host tests
└── androidApp/             — GLSurfaceView host for the shared engine
```

## Why no JSON across the platform boundary?

The original stack serialized style documents and layer properties to JSON
strings and passed them over JNI (`setStyleJson(String)`, Gson `JsonElement`s
in `platform/android/.../cpp/gson/`).

In this rewrite the boundary is the `MapEngine` interface:

```kotlin
interface MapEngine {
    fun setStyle(style: StyleSpec)          // typed object, not a JSON string
    fun addSource(source: SourceSpec)
    fun addLayer(layer: LayerSpec)
    fun setCamera(center: LatLng, zoom: Double, bearing: Double, pitch: Double)
    ...
}
```

`MapEngineImpl` lives in `commonMain`, so both platform hosts call the same
typed engine. Android's `MapView` invokes it directly and uploads the returned
RGBA frame to a GL texture. The Kotlin/Native framework exports the same
implementation for an iOS host to call directly. No JSON is constructed or
parsed at either platform/engine boundary.

## Porting status

| Module             | C++ source               | Kotlin port        |
|--------------------|--------------------------|--------------------|
| math (clamp/wrap/log2/angles) | `src/mbgl/math/*`, `include/mbgl/math/*` | ✅ done + tests |
| geo (LatLng, bounds, insets)  | `include/mbgl/util/geo.hpp`, `src/mbgl/util/geo.cpp` | ✅ done + tests |
| projection (Web Mercator)     | `include/mbgl/util/projection.hpp` | ✅ done + tests |
| tile ids                      | `include/mbgl/tile/tile_id.hpp`    | ✅ done + tests |
| style model + JSON parser     | `src/mbgl/style/`, `include/mbgl/style/` | ✅ done + tests |
| expressions (interpolate/step/match/coalesce/case/let/var/compound) | `src/mbgl/style/expression/`, `include/mbgl/style/expression/` | ✅ core engine done (50 tests): Value/Type/Expression, UnitBezier, exponential & cubic-bezier interpolators, step, interpolate (number/color/array), match, coalesce, case, let/var, arithmetic/comparison/boolean/coercion/string ops; `["zoom"]`, `["get"]`, `["has"]` |
| tile pipeline (loading/cover) | `src/mbgl/tile/`, `src/mbgl/util/tile_cover*` | ✅ core done (71 tests): scan-line tileCover (bounds), tileCount, coveringZoomLevel, Tileset/Scheme, replaceTokens, quadkey/bbox/prefix/ratio URL tokens, FileSource + TileLoader + TileObserver (coroutines) |
| renderer (GL/Metal backends)  | `src/mbgl/renderer/`, `src/mbgl/gl/`, `src/mbgl/mtl/` | 🚧 shared CPU rasterizer + `MapEngineImpl` done: typed style/source/layer lifecycle, geographic ↔ screen conversion, viewport tile coverage, decoded vector-tile rendering, GLES context (Android), Metal context (iOS), JVM host context. GPU drawables and native platform views remain to port. |

## Building & testing

```bash
# Host-side unit tests (JVM)
./gradlew :core:jvmTest

# Android application compilation
./gradlew :androidApp:compileDebugKotlinAndroid

# iOS framework (requires macOS/Xcode)
./gradlew :core:linkDebugFrameworkIosArm64
```

Requires JDK 17+ and Android SDK (set `sdk.dir` in `kotlin/local.properties`
or `ANDROID_HOME`).
