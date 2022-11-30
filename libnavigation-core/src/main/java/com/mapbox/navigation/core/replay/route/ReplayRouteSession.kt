package com.mapbox.navigation.core.replay.route

import android.annotation.SuppressLint
import android.content.Context
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineProvider
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.MapboxNavigationObserver
import com.mapbox.navigation.core.replay.MapboxReplayer
import com.mapbox.navigation.core.replay.history.ReplayEventBase
import com.mapbox.navigation.core.replay.history.ReplayEventUpdateLocation
import com.mapbox.navigation.core.replay.history.ReplayEventsObserver
import com.mapbox.navigation.utils.internal.logW
import java.util.Collections

/**
 * Used to create a replay trip session. Continue to use [MapboxNavigation.setNavigationRoutes] to
 * decide the route that should be replayed.
 *
 * Do not use this class with the [ReplayProgressObserver]. They will create conflicts with and
 * the results are undefined.
 *
 * The simulated driver from [ReplayRouteSession] will slow down to a stop depending
 * [ReplayRouteSessionOptions.decodeMinDistance]. To remove this behavior you can set it to
 * [Double.MAX_VALUE], be aware that it will require more memory.
 *
 * Use [ReplayRouteSessionOptions] for customizations. For example, this is how can update the
 * location frequency.
 *
 * ```
 * replayRouteSession.setOptions(
 *     replayRouteSession.getOptions().toBuilder()
 *         .replayRouteOptions(
 *             replayRouteSession.getOptions().replayRouteOptions.toBuilder()
 *                 .frequency(25.0)
 *                 .build()
 *         )
 *         .build()
 * )
 * ```
 *
 * Enable and disable the [ReplayRouteSession] with [MapboxNavigation] or [MapboxNavigationApp].
 * The replay session will be enabled when [MapboxNavigation] is attached.
 */
@ExperimentalPreviewMapboxNavigationAPI
class ReplayRouteSession : MapboxNavigationObserver {

    private var options = ReplayRouteSessionOptions.Builder().build()

    private lateinit var polylineDecodeStream: ReplayPolylineDecodeStream
    private lateinit var replayRouteMapper: ReplayRouteMapper
    private var mapboxNavigation: MapboxNavigation? = null
    private var lastLocationEvent: ReplayEventUpdateLocation? = null
    private var routesObserver: RoutesObserver? = null
    private var currentRouteId: String? = null

    private val replayEventsObserver = ReplayEventsObserver { events ->
        if (isLastEventPlayed(events)) {
            pushMorePoints()
        }
    }

    /**
     * Get the options that are currently set. This can be used to change the options.
     * ```
     * setOptions(getOptions().toBuilder().locationResetEnabled(false).build())
     * ```
     */
    fun getOptions(): ReplayRouteSessionOptions = options

    /**
     * Set new options for the [ReplayRouteSession]. This will not effect previously simulated
     * events, the end behavior will depend on the values you have used. If you want to guarantee
     * the effect of the options, you need to set options before [MapboxNavigation] is attached.
     */
    fun setOptions(options: ReplayRouteSessionOptions) = apply {
        this.options = options
        if (::replayRouteMapper.isInitialized) {
            replayRouteMapper.options = this.options.replayRouteOptions
        }
    }

    override fun onAttached(mapboxNavigation: MapboxNavigation) {
        this.replayRouteMapper = ReplayRouteMapper(options.replayRouteOptions)
        this.mapboxNavigation = mapboxNavigation
        mapboxNavigation.stopTripSession()
        mapboxNavigation.startReplayTripSession()

        routesObserver = RoutesObserver { result ->
            if (result.navigationRoutes.isEmpty()) {
                currentRouteId = null
                mapboxNavigation.resetReplayLocation()
            } else if (result.navigationRoutes.first().id != currentRouteId) {
                onRouteChanged(result.navigationRoutes.first())
            }
        }.also { mapboxNavigation.registerRoutesObserver(it) }
        mapboxNavigation.mapboxReplayer.registerObserver(replayEventsObserver)
        mapboxNavigation.resetReplayLocation()
    }

    private fun MapboxNavigation.resetReplayLocation() {
        mapboxReplayer.clearEvents()
        resetTripSession()
        if (options.locationResetEnabled) {
            val context = navigationOptions.applicationContext
            if (PermissionsManager.areLocationPermissionsGranted(context)) {
                pushRealLocation(context)
            } else {
                logW(LOG_CATEGORY) {
                    "Location permission have not been accepted. If this is intentional, disable" +
                        " this warning with ReplayRouteSessionOptions.locationResetEnabled."
                }
            }
        }
        mapboxReplayer.play()
    }

    override fun onDetached(mapboxNavigation: MapboxNavigation) {
        this.mapboxNavigation = null
        mapboxNavigation.mapboxReplayer.unregisterObserver(replayEventsObserver)
        mapboxNavigation.mapboxReplayer.stop()
        mapboxNavigation.mapboxReplayer.clearEvents()
        mapboxNavigation.stopTripSession()
    }

    private fun onRouteChanged(navigationRoute: NavigationRoute) {
        val mapboxReplayer = mapboxNavigation?.mapboxReplayer ?: return
        mapboxReplayer.clearEvents()
        mapboxReplayer.play()
        val geometries = navigationRoute.directionsRoute.routeOptions()!!.geometries()
        val usesPolyline6 = geometries.contains(DirectionsCriteria.GEOMETRY_POLYLINE6)
        val geometry = navigationRoute.directionsRoute.geometry()
        if (!usesPolyline6 || geometry.isNullOrEmpty()) {
            logW(LOG_CATEGORY) {
                "The NavigationRouteReplay must have geometry encoded with polyline6 " +
                    "$geometries $geometry"
            }
            return
        }
        currentRouteId = navigationRoute.id
        polylineDecodeStream = ReplayPolylineDecodeStream(geometry, 6)
        mapboxNavigation?.resetTripSession()
        pushMorePoints()
    }

    private fun isLastEventPlayed(events: List<ReplayEventBase>): Boolean {
        val currentLocationEvent = events.lastOrNull { it is ReplayEventUpdateLocation }
            ?: return false
        val lastEventTimestamp = this.lastLocationEvent?.eventTimestamp ?: 0.0
        return currentLocationEvent.eventTimestamp >= lastEventTimestamp
    }

    private fun pushMorePoints() {
        val nextPoints = polylineDecodeStream.decode(options.decodeMinDistance)
        val nextReplayLocations = replayRouteMapper.mapPointList(nextPoints)
        lastLocationEvent = nextReplayLocations.lastOrNull { it is ReplayEventUpdateLocation }
            as? ReplayEventUpdateLocation
        mapboxNavigation?.mapboxReplayer?.clearPlayedEvents()
        mapboxNavigation?.mapboxReplayer?.pushEvents(nextReplayLocations)
    }

    /**
     * This function is similar to [MapboxReplayer.pushRealLocation] except that it checks if there
     * is an active route before it tries to push a gps location. This is needed to avoid a race
     * condition between setting routes and requesting a location.
     */
    @SuppressLint("MissingPermission")
    private fun pushRealLocation(context: Context) {
        LocationEngineProvider.getBestLocationEngine(context.applicationContext)
            .getLastLocation(
                object : LocationEngineCallback<LocationEngineResult> {
                    override fun onSuccess(result: LocationEngineResult?) {
                        if (mapboxNavigation?.getNavigationRoutes()?.isNotEmpty() == true) {
                            return
                        }
                        result?.lastLocation?.let {
                            val event = ReplayRouteMapper.mapToUpdateLocation(0.0, it)
                            mapboxNavigation?.mapboxReplayer?.pushEvents(
                                Collections.singletonList(event)
                            )
                        }
                    }

                    override fun onFailure(exception: Exception) {
                        // Intentionally empty
                    }
                }
            )
    }

    private companion object {
        private const val LOG_CATEGORY = "MapboxReplayRouteTripSession"
    }
}