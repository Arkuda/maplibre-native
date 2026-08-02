package org.maplibre.kotlin.gfx

/**
 * Creates the platform graphics context.
 *
 * On Android this returns an OpenGL ES 3.0 context backed by EGL;
 * on iOS a Metal context. The actual backends live in androidMain/iosMain.
 */
expect fun createPlatformContext(): Context

/** Backend identifier, for diagnostics. */
expect val backendName: String
