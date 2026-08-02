package org.maplibre.kotlin.gfx

import org.maplibre.kotlin.gfx.platform.MetalContext

actual fun createPlatformContext(): Context = MetalContext()

actual val backendName: String get() = "metal"
