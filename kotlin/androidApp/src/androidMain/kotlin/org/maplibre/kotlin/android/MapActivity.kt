package org.maplibre.kotlin.android

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import org.maplibre.kotlin.gfx.Color
import org.maplibre.kotlin.style.LayerSpec
import org.maplibre.kotlin.style.LayerType
import org.maplibre.kotlin.style.PropertyValue
import org.maplibre.kotlin.style.SourceSpec
import org.maplibre.kotlin.style.StyleSpec
import org.maplibre.kotlin.tile.CanonicalTileID
import org.maplibre.kotlin.tile.FeatureType
import org.maplibre.kotlin.tile.TileFeature
import org.maplibre.kotlin.tile.TileLayer
import org.maplibre.kotlin.tile.TilePoint
import org.maplibre.kotlin.tile.VectorTileData
import org.maplibre.kotlin.util.LatLng

class MapActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mapView = MapView(this)
        setContentView(
            FrameLayout(this).apply {
                addView(mapView, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ))
            },
        )

        val engine = mapView.mapEngine
        engine.setStyle(
            StyleSpec(
                version = 8,
                name = "demo",
                sources = mapOf(
                    "demo" to SourceSpec.Vector(id = "demo", tiles = emptyList()),
                ),
                layers = listOf(
                    LayerSpec(
                        id = "background",
                        type = LayerType.Background,
                        paint = mapOf(
                            "background-color" to PropertyValue.Constant(Color(0.93f, 0.93f, 0.93f, 1.0f)),
                            "background-opacity" to PropertyValue.Constant(1.0),
                        ),
                    ),
                    LayerSpec(
                        id = "water",
                        type = LayerType.Fill,
                        source = "demo",
                        sourceLayer = "water",
                        paint = mapOf(
                            "fill-color" to PropertyValue.Constant(Color(0.4f, 0.65f, 0.85f, 1.0f)),
                            "fill-opacity" to PropertyValue.Constant(1.0),
                        ),
                    ),
                ),
            ),
        )

        // Demo tile: a polygon in tile coordinates [0..EXTENT].
        val extent = 8192
        val tile = VectorTileData(
            layers = mapOf(
                "water" to TileLayer(
                    name = "water",
                    version = 2,
                    extent = extent,
                    features = listOf(
                        TileFeature(
                            id = 1,
                            type = FeatureType.POLYGON,
                            properties = emptyMap(),
                            geometry = listOf(
                                listOf(
                                    TilePoint(1024.0, 1024.0),
                                    TilePoint(7168.0, 1024.0),
                                    TilePoint(7168.0, 7168.0),
                                    TilePoint(1024.0, 7168.0),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        engine.putTile(CanonicalTileID(2u, 1u, 1u), tile)
        engine.setCamera(LatLng(0.0, 0.0), 2.0)
    }
}