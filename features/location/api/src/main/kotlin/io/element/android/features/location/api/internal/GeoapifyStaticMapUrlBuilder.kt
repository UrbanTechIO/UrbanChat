/*
 * Copyright (c) 2026 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.api.internal

import io.element.android.features.location.api.BuildConfig

private const val MAX_DIMENSION_PX = 4096
private val ZOOM_RANGE = 1.0..20.0

/**
 * Builds an URL for Geoapify's Static Maps API (OpenStreetMap-based).
 *
 * https://apidocs.geoapify.com/docs/maps/static/
 *
 * Unlike MapTiler's static maps (paid-tier only), Geoapify's free tier includes static map
 * rendering. No marker is requested here: the pin is drawn by our own [io.element.android.libraries.designsystem.components.LocationPin]
 * composable on top of the plain map, matching the previous MapTiler-based rendering.
 */
internal class GeoapifyStaticMapUrlBuilder(
    private val apiKey: String,
    private val lightStyle: String,
    private val darkStyle: String,
) : StaticMapUrlBuilder {
    constructor() : this(
        apiKey = BuildConfig.GEOAPIFY_API_KEY,
        lightStyle = BuildConfig.GEOAPIFY_LIGHT_STYLE,
        darkStyle = BuildConfig.GEOAPIFY_DARK_STYLE,
    )

    override fun build(
        lat: Double,
        lon: Double,
        zoom: Double,
        darkMode: Boolean,
        width: Int,
        height: Int,
        density: Float,
    ): String {
        val style = if (darkMode) darkStyle else lightStyle
        val finalZoom = zoom.coerceIn(ZOOM_RANGE)
        val finalWidth = width.coerceIn(1, MAX_DIMENSION_PX)
        val finalHeight = height.coerceIn(1, MAX_DIMENSION_PX)
        return "https://maps.geoapify.com/v1/staticmap" +
            "?style=$style" +
            "&width=$finalWidth" +
            "&height=$finalHeight" +
            "&center=lonlat:$lon,$lat" +
            "&zoom=$finalZoom" +
            "&apiKey=$apiKey"
    }

    override fun isServiceAvailable() = apiKey.isNotEmpty()
}
