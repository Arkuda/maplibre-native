package org.maplibre.kotlin.android

import android.app.Activity
import android.os.Bundle
import android.widget.FrameLayout
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

class MapActivity : Activity() {

    private lateinit var mapView: MapView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)

        mapView = MapView(this)
        setContentView(
            FrameLayout(this).apply {
                addView(
                    mapView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            },
        )
        mapView.onCreate(savedInstanceState)

        val apiKey = BuildConfig.MAPTILER_API_KEY
        require(apiKey.isNotBlank()) { "Build with MAPTILER_API_KEY to run the OpenMapTiles sample." }
        val styleJson = assets.open("basic.json").bufferedReader().use { it.readText() }
            .replace("get_your_own_OpIi9ZULNHzrESv6T2vL", apiKey)
        mapView.getMapAsync { map ->
            map.setStyle(Style.Builder().fromJson(styleJson))
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }

    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}