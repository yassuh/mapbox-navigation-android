@file:JvmName("NavigationRouteEx")

package com.mapbox.navigation.base.internal.route

import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import com.mapbox.api.directions.v5.models.Closure
import com.mapbox.api.directions.v5.models.DirectionsResponse
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.Incident
import com.mapbox.api.directions.v5.models.LegAnnotation
import com.mapbox.api.directions.v5.models.LegStep
import com.mapbox.api.directions.v5.models.RouteLeg
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.navigation.base.internal.SDKRouteParser
import com.mapbox.navigation.base.route.NavigationRoute
import com.mapbox.navigation.base.route.toNavigationRoute
import com.mapbox.navigation.base.utils.DecodeUtils.stepGeometryToPoints
import com.mapbox.navigation.utils.internal.logE
import com.mapbox.navigator.Navigator
import com.mapbox.navigator.RouteInterface
import com.mapbox.navigator.RouterOrigin
import org.jetbrains.annotations.TestOnly

private const val ROUTE_REFRESH_LOG_CATEGORY = "RouteRefresh"

val NavigationRoute.routerOrigin: RouterOrigin get() = nativeRoute.routerOrigin

/**
 * Internal handle for the route's native peer.
 */
fun NavigationRoute.nativeRoute(): RouteInterface = this.nativeRoute

/**
 * Updates route's annotations, incidents, and closures in place while keeping the Native peer as is.
 * The peer should later be updated through [Navigator.refreshRoute].
 * Call from a worker thread as the function contains geometry parsing under the hood.
 */
@WorkerThread
fun NavigationRoute.refreshRoute(
    initialLegIndex: Int,
    currentLegGeometryIndex: Int?,
    legAnnotations: List<LegAnnotation?>?,
    incidents: List<List<Incident>?>?,
    closures: List<List<Closure>?>?,
): NavigationRoute {
    val updateLegs = directionsRoute.legs()?.mapIndexed { index, routeLeg ->
        if (index < initialLegIndex) {
            routeLeg
        } else {
            val newAnnotation = legAnnotations?.getOrNull(index)
            val mergedAnnotation =
                if (index == initialLegIndex && currentLegGeometryIndex != null) {
                    AnnotationsRefresher.getRefreshedAnnotations(
                        routeLeg.annotation(),
                        newAnnotation,
                        currentLegGeometryIndex
                    )
                } else {
                    AnnotationsRefresher.getRefreshedAnnotations(
                        routeLeg.annotation(),
                        newAnnotation,
                        startingLegGeometryIndex = 0
                    )
                }
            routeLeg.toBuilder()
                .duration(mergedAnnotation?.duration()?.sumOf { it } ?: routeLeg.duration())
                .annotation(mergedAnnotation)
                .incidents(incidents?.getOrNull(index))
                .closures(closures?.getOrNull(index))
                .steps(routeLeg.steps()?.updateSteps(directionsRoute, mergedAnnotation))
                .build()
        }
    }
    return updateDirectionsRouteOnly {
        toBuilder()
            .legs(updateLegs)
            .updateRouteDurationBasedOnLegsDuration(updateLegs)
            .build()
    }
}

/**
 * Updates only java representation of route.
 * The native route should later be updated through [Navigator.refreshRoute].
 */
fun NavigationRoute.updateDirectionsRouteOnly(
    block: DirectionsRoute.() -> DirectionsRoute
): NavigationRoute {
    val refreshedRoute = directionsRoute.block()
    val refreshedRoutes = directionsResponse.routes().toMutableList()
    refreshedRoutes[routeIndex] = refreshedRoute
    val refreshedResponse = directionsResponse.toBuilder()
        .routes(refreshedRoutes)
        .build()
    return copy(directionsResponse = refreshedResponse)
}

/**
 * Internal API used for testing purposes. Needed to avoid calling native parser from unit tests.
 */
@TestOnly
fun createNavigationRoute(
    directionsRoute: DirectionsRoute,
    sdkRouteParser: SDKRouteParser,
): NavigationRoute =
    directionsRoute
        .toNavigationRoute(sdkRouteParser, com.mapbox.navigation.base.route.RouterOrigin.Custom())

/**
 * Internal API used for testing purposes. Needed to avoid calling native parser from unit tests.
 */
@VisibleForTesting
fun createNavigationRoutes(
    directionsResponse: DirectionsResponse,
    routeOptions: RouteOptions,
    routeParser: SDKRouteParser,
    routerOrigin: com.mapbox.navigation.base.route.RouterOrigin,
): List<NavigationRoute> =
    NavigationRoute.create(directionsResponse, routeOptions, routeParser, routerOrigin)

/**
 * Internal API to create a new [NavigationRoute] from a native peer.
 */
fun RouteInterface.toNavigationRoute(): NavigationRoute {
    return this.toNavigationRoute()
}

private fun List<LegStep>.updateSteps(
    route: DirectionsRoute,
    mergedAnnotation: LegAnnotation?
): List<LegStep> {
    val mergedDurations = mergedAnnotation?.duration() ?: return this
    val result = mutableListOf<LegStep>()
    var previousStepsAnnotationsCount = 0
    forEachIndexed { index, step ->
        val stepPointsSize = route.stepGeometryToPoints(step).size
        if (stepPointsSize < 2) {
            logE(
                "step at $index has less than 2 points, unable to update duration",
                ROUTE_REFRESH_LOG_CATEGORY
            )
            return this
        }
        val stepAnnotationsCount = stepPointsSize - 1
        val updatedDuration = mergedDurations
            .drop(previousStepsAnnotationsCount)
            .take(stepAnnotationsCount)
            .sum()
        result.add(step.toBuilder().duration(updatedDuration).build())
        previousStepsAnnotationsCount += stepAnnotationsCount
    }
    return result
}

private fun DirectionsRoute.Builder.updateRouteDurationBasedOnLegsDuration(
    updateLegs: List<RouteLeg>?
): DirectionsRoute.Builder {
    updateLegs ?: return this
    var result = 0.0
    for (leg in updateLegs) {
        result += leg.duration() ?: return this
    }
    duration(result)
    return this
}
