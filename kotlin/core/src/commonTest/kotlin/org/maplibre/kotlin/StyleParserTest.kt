package org.maplibre.kotlin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.style.SourceSpec
import org.maplibre.kotlin.style.StyleJsonParser

class StyleParserTest {

    private val styleJson = """
    {
      "version": 8,
      "name": "Test style",
      "sources": {
        "osm": {
          "type": "vector",
          "tiles": ["https://tile.example.com/{z}/{x}/{y}.pbf"],
          "maxzoom": 16
        },
        "hillshade": {
          "type": "raster",
          "tiles": ["https://hill.example.com/{z}/{x}/{y}.png"]
        }
      },
      "layers": [
        {
          "id": "background",
          "type": "background",
          "paint": { "background-color": "#f2efe9" }
        },
        {
          "id": "roads",
          "type": "line",
          "source": "osm",
          "source-layer": "transportation",
          "minzoom": 5,
          "layout": { "line-cap": "round" },
          "paint": { "line-width": ["interpolate", ["linear"], ["zoom"], 14, 1, 20, 5] }
        }
      ]
    }
    """.trimIndent()

    @Test
    fun parsesBasicStyle() {
        val style = StyleJsonParser.parse(styleJson)
        assertEquals(8, style.version)
        assertEquals("Test style", style.name)
        assertEquals(2, style.sources.size)
        assertEquals(2, style.layers.size)
    }

    @Test
    fun parsesVectorSource() {
        val style = StyleJsonParser.parse(styleJson)
        val source = style.sources["osm"]
        assertIs<SourceSpec.Vector>(source)
        assertEquals(listOf("https://tile.example.com/{z}/{x}/{y}.pbf"), source.tiles)
        assertEquals(16, source.maxzoom)
    }

    @Test
    fun parsesRasterSource() {
        val style = StyleJsonParser.parse(styleJson)
        assertIs<SourceSpec.Raster>(style.sources["hillshade"])
    }

    @Test
    fun parsesLayerProperties() {
        val style = StyleJsonParser.parse(styleJson)
        val background = style.layers[0]
        assertEquals("background", background.id)
        assertEquals(LayerType.Background, background.type)
        val color = background.paint["background-color"]
        assertIs<PropertyValue.Constant>(color)
        assertEquals("#f2efe9", color.value)

        val roads = style.layers[1]
        assertEquals(LayerType.Line, roads.type)
        assertEquals("osm", roads.source)
        assertEquals("transportation", roads.sourceLayer)
        assertEquals(5f, roads.minzoom)
        val width = roads.paint["line-width"]
        assertIs<PropertyValue.Expression>(width)
        assertEquals(7, width.steps.size)
    }
}
