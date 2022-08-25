@file:Suppress("unused")

package com.mapbox.navigation.dropin

import com.mapbox.api.directions.v5.models.DirectionsWaypoint
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.base.route.NavigationRoute

/**
 * Api that gives you the ability to change the state for navigation apps.
 */
@ExperimentalPreviewMapboxNavigationAPI
abstract class NavigationViewApi {

    /**
     * Clear Route data and request [NavigationView] to enter Free Drive state.
     *
     * [NavigationViewListener.onFreeDrive] will be called once [NavigationView] enters
     * Free Drive state.
     */
    abstract fun startFreeDrive()

    /**
     * Sets a [point] as destination and request [NavigationView] to enter Destination Preview state.
     *
     * [NavigationViewListener.onDestinationChanged] will be called once new destination is set
     * and [NavigationViewListener.onDestinationPreview] once [NavigationView] enters
     * Destination Preview state.
     */
    abstract fun startDestinationPreview(point: Point)

    /**
     * Request [NavigationView] to enter Route Preview state.
     *
     * [NavigationViewListener.onRoutePreview] will be called once [NavigationView] enters
     * Route Preview state.
     *
     * Fails with an error when either Destination or Preview Routes has not been set.
     */
    abstract fun startRoutePreview(): Expected<NavigationViewApiError, Unit>

    /**
     * Sets a preview [routes] and request Request [NavigationView] to enter Route Preview state.
     * Last [DirectionsWaypoint] location will be used as the destination.
     *
     * [NavigationViewListener.onDestinationChanged] will be called once new destination is set
     * and [NavigationViewListener.onRoutePreview] will be called once [NavigationView] enters
     * Route Preview state.
     *
     * Fails with an error when [routes] is an empty list.
     */
    abstract fun startRoutePreview(
        routes: List<NavigationRoute>
    ): Expected<NavigationViewApiError, Unit>

    /**
     * Request [NavigationView] to enter Active Navigation state.
     *
     * [NavigationViewListener.onActiveNavigation] will be called once [NavigationView] enters
     * Route Preview state.
     *
     * Fails with an error when either Destination or Preview Routes has not been set.
     */
    abstract fun startActiveGuidance(): Expected<NavigationViewApiError, Unit>

    /**
     * Sets [routes] and request [NavigationView] to enter Active Navigation state.
     * Last [DirectionsWaypoint] location will be used as the destination.
     *
     * [NavigationViewListener.onDestinationChanged] will be called once new destination is set
     * and [NavigationViewListener.onActiveNavigation] will be called once [NavigationView] enters
     * Active Navigation state.
     *
     * Fails with an error when [routes] is an empty list.
     */
    abstract fun startActiveGuidance(
        routes: List<NavigationRoute>
    ): Expected<NavigationViewApiError, Unit>

    /**
     * Request [NavigationView] to enter Arrival state.
     *
     * [NavigationViewListener.onArrival] will be called once [NavigationView] enters
     * Arrival state.
     *
     * Fails with an error when either Destination or Routes has not been set.
     */
    abstract fun startArrival(): Expected<NavigationViewApiError, Unit>

    /**
     * Sets [routes] and request [NavigationView] to enter Arrival state.
     * Last [DirectionsWaypoint] location will be used as the destination.
     *
     * [NavigationViewListener.onDestinationChanged] will be called once new destination is set
     * and [NavigationViewListener.onArrival] will be called once [NavigationView] enters
     * Arrival state.
     *
     * Fails with an error when [routes] is an empty list.
     */
    abstract fun startArrival(routes: List<NavigationRoute>): Expected<NavigationViewApiError, Unit>

    /**
     * Checks if the current trip is being simulated.
     */
    abstract fun isReplayEnabled(): Boolean

    /**
     * Enable/Disable replay trip session based on simulated locations.
     */
    abstract fun routeReplayEnabled(enabled: Boolean)
}

/**
 * Errors returned by the NavigationApi.
 */
sealed class NavigationViewApiError(message: String) : Throwable(message) {
    /**
     * Error returned when the Destination hasn't been set yet.
     */
    object MissingDestinationInfo : NavigationViewApiError("Destination cannot be empty.")

    /**
     * Error returned when the Preview Routes list hasn't been set yet.
     */
    object MissingPreviewRoutesInfo : NavigationViewApiError("Preview Routes cannot be empty.")

    /**
     * Error returned when the Routes list hasn't been set yet.
     */
    object MissingRoutesInfo : NavigationViewApiError("Routes cannot be empty.")

    /**
     * Error returned when given PreviewRoute or Route list is empty.
     */
    object InvalidRoutesInfo : NavigationViewApiError("Routes cannot be empty.")

    /**
     * Error returned when given PreviewRoute or Route list is missing [DirectionsWaypoint]
     * information that is needed to determine Destination coordinates.
     */
    object IncompleteRoutesInfo :
        NavigationViewApiError("Missing destination info in a given route.")
}
