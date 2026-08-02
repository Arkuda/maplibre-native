package org.maplibre.kotlin.gfx

import org.maplibre.kotlin.gfx.platform.GlesContext

actual fun createPlatformContext(): Context = GlesContext()

actual val backendName: String get() = "opengl-es-3.0"
