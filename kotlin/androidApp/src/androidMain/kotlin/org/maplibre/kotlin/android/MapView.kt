package org.maplibre.kotlin.android

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * [GLSurfaceView] that renders the software-rasterized map frame into a GL
 * texture and draws it on a fullscreen quad.
 */
class MapView(context: Context) : GLSurfaceView(context) {

    private val engine = MapEngineImpl()

    init {
        setEGLContextClientVersion(3)
        setRenderer(MapRenderer())
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    val mapEngine: MapEngineImpl get() = engine

    private inner class MapRenderer : GLSurfaceView.Renderer {

        private var frameTex = -1
        private var program = -1
        private var vao = -1
        private var uTex = -1
        private var lastTexW = 0
        private var lastTexH = 0

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            frameTex = createTexture()
            program = createProgram()
            vao = createFullscreenQuad()
            uTex = GLES30.glGetUniformLocation(program, "u_tex")
            GLES30.glClearColor(0f, 0f, 0f, 1f)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES30.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            val density = resources.displayMetrics.density
            val w = width
            val h = height
            val buf = engine.renderFrame(w, h, density)

            val texW = w
            val texH = h
            if (texW != lastTexW || texH != lastTexH) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTex)
                GLES30.glTexImage2D(
                    GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA,
                    texW, texH, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null,
                )
                lastTexW = texW
                lastTexH = texH
            }
            val bb = ByteBuffer.wrap(buf)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTex)
            GLES30.glTexSubImage2D(
                GLES30.GL_TEXTURE_2D, 0, 0, 0, texW, texH,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, bb,
            )

            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
            GLES30.glUseProgram(program)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, frameTex)
            GLES30.glUniform1i(uTex, 0)
            GLES30.glBindVertexArray(vao)
            GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
            GLES30.glBindVertexArray(0)
        }

        private fun createTexture(): Int {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            val id = ids[0]
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, id)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
            return id
        }

        private fun createProgram(): Int {
            val vsh = """
                #version 300 es
                layout(location = 0) in vec2 a_pos;
                out vec2 v_uv;
                void main() {
                    v_uv = a_pos * 0.5 + 0.5;
                    gl_Position = vec4(a_pos, 0.0, 1.0);
                }
            """.trimIndent()
            val fsh = """
                #version 300 es
                precision mediump float;
                uniform sampler2D u_tex;
                in vec2 v_uv;
                out vec4 fragColor;
                void main() { fragColor = texture(u_tex, vec2(v_uv.x, 1.0 - v_uv.y)); }
            """.trimIndent()
            return compileProgram(vsh, fsh)
        }

        private fun createFullscreenQuad(): Int {
            val vertices = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f,
            )
            val bb = ByteBuffer.allocateDirect(vertices.size * 4)
                .order(ByteOrder.nativeOrder())
            bb.asFloatBuffer().put(vertices)
            bb.position(0)

            val vbo = IntArray(1)
            GLES30.glGenBuffers(1, vbo, 0)
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, bb.capacity(), bb, GLES30.GL_STATIC_DRAW)

            val vao = IntArray(1)
            GLES30.glGenVertexArrays(1, vao, 0)
            GLES30.glBindVertexArray(vao[0])
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
            GLES30.glEnableVertexAttribArray(0)
            GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
            GLES30.glBindVertexArray(0)
            return vao[0]
        }

        private fun compileProgram(vsh: String, fsh: String): Int {
            fun compile(type: Int, src: String): Int {
                val sh = GLES30.glCreateShader(type)
                GLES30.glShaderSource(sh, src)
                GLES30.glCompileShader(sh)
                val status = IntArray(1)
                GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, status, 0)
                if (status[0] == 0) {
                    val log = GLES30.glGetShaderInfoLog(sh)
                    throw RuntimeException("Shader compile failed: $log")
                }
                return sh
            }
            val vs = compile(GLES30.GL_VERTEX_SHADER, vsh)
            val fs = compile(GLES30.GL_FRAGMENT_SHADER, fsh)
            val p = GLES30.glCreateProgram()
            GLES30.glAttachShader(p, vs)
            GLES30.glAttachShader(p, fs)
            GLES30.glLinkProgram(p)
            val status = IntArray(1)
            GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, status, 0)
            if (status[0] == 0) {
                val log = GLES30.glGetProgramInfoLog(p)
                throw RuntimeException("Program link failed: $log")
            }
            return p
        }
    }
}