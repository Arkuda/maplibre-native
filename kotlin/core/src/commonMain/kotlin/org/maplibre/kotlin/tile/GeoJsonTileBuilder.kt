package org.maplibre.kotlin.tile

import org.maplibre.kotlin.style.GeoJsonGeometry
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.PI

/**
 * Converts GeoJSON features (lon/lat) into VectorTileData for a specific tile.
 * Used by GeoJsonSource to synthesize tiles on the fly.
 */
object GeoJsonTileBuilder {

    /**
     * Builds a tile from GeoJSON features.
     *
     * @param features Parsed GeoJSON features (lon/lat coordinates).
     * @param z Tile zoom level.
     * @param x Tile column.
     * @param y Tile row.
     * @param layerName Layer name in the output tile (default "geojson").
     * @return VectorTileData with geometries in tile-local coordinates (0..4096).
     */
    fun buildTile(
        features: List<GeoJsonFeature>,
        z: Int,
        x: Int,
        y: Int,
        layerName: String = "geojson",
    ): VectorTileData {
        val scale = (1 shl z).toDouble()
        val tileX = x.toDouble()
        val tileY = y.toDouble()
        
        // tile extent is 4096 units (MVT standard)
        val extent = 4096
        
        val convertedFeatures = features.mapNotNull { feature ->
            val (geomType, geom) = convertGeometry(feature.geometry, scale, tileX, tileY, extent)
                ?: return@mapNotNull null
            TileFeature(
                id = feature.id,
                type = geomType,
                properties = feature.properties,
                geometry = geom,
            )
        }
        
        return VectorTileData(
            layers = mapOf(layerName to TileLayer(
                name = layerName,
                version = 2,
                extent = extent,
                features = convertedFeatures,
            )),
        )
    }

    /**
     * Projects lon/lat to tile-local coordinates (0..extent).
     * Mercator projection: x = lon, y = atanh(sin(lat)).
     */
    private fun lonLatToTileXY(lon: Double, lat: Double, scale: Double, tileX: Double, tileY: Double, extent: Int): TilePoint? {
        // Clamp latitude to avoid singularity at poles
        val clampedLat = lat.coerceIn(-85.0511, 85.0511)
        
        // Mercator projection (0..1 world coordinates)
        val worldX = lon / 360.0 + 0.5
        val worldY = 0.5 - (ln((1.0 + sin(PI * clampedLat / 180.0)) /
                     (1.0 - sin(PI * clampedLat / 180.0))) / (4.0 * PI))
        
        // Tile-local coordinates
        val localX = (worldX * scale - tileX) * extent
        val localY = (worldY * scale - tileY) * extent
        
        // Skip if too far outside tile (allow small buffer for clipping)
        if (localX < -extent || localX > 2 * extent || localY < -extent || localY > 2 * extent) {
            return null
        }
        
        return TilePoint(localX, localY)
    }

    private fun convertGeometry(
        geometry: GeoJsonGeometry,
        scale: Double,
        tileX: Double,
        tileY: Double,
        extent: Int,
    ): Pair<FeatureType, GeometryCollection>? {
        return when (geometry) {
            is GeoJsonGeometry.Point -> {
                val pt = lonLatToTileXY(geometry.x, geometry.y, scale, tileX, tileY, extent) ?: return null
                Pair(FeatureType.POINT, listOf(listOf(pt)))
            }
            is GeoJsonGeometry.LineString -> {
                val pts = geometry.points.mapNotNull { lonLatToTileXY(it.x, it.y, scale, tileX, tileY, extent) }
                if (pts.size < 2) return null
                Pair(FeatureType.LINESTRING, listOf(pts))
            }
            is GeoJsonGeometry.Polygon -> {
                val rings = geometry.rings.mapNotNull { ring ->
                    ring.points.mapNotNull { lonLatToTileXY(it.x, it.y, scale, tileX, tileY, extent) }
                }.filter { it.size >= 3 }
                if (rings.isEmpty()) return null
                Pair(FeatureType.POLYGON, rings)
            }
        }
    }
}
