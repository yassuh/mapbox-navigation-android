package com.mapbox.navigation.base.internal.route

import com.mapbox.api.directions.v5.models.LegAnnotation
import com.mapbox.navigation.utils.internal.logE
import kotlin.math.min

internal object AnnotationsRefresher {

    private const val LOG_CATEGORY = "AnnotationsRefresher"

    fun getRefreshedAnnotations(
        oldAnnotation: LegAnnotation?,
        newAnnotation: LegAnnotation?,
        startingLegGeometryIndex: Int
    ): LegAnnotation? {
        if (oldAnnotation == null) {
            return null
        }
        val congestionNumeric = mergeAnnotationProperty(
            oldAnnotation,
            newAnnotation,
            startingLegGeometryIndex,
        ) { congestionNumeric() }
        val congestion = mergeAnnotationProperty(
            oldAnnotation,
            newAnnotation,
            startingLegGeometryIndex,
        ) { congestion() }
        val distance = mergeAnnotationProperty(
            oldAnnotation,
            newAnnotation,
            startingLegGeometryIndex,
        ) { distance() }
        val duration = mergeAnnotationProperty(
            oldAnnotation,
            newAnnotation,
            startingLegGeometryIndex,
        ) { duration() }
        val speed = mergeAnnotationProperty(
            oldAnnotation,
            newAnnotation,
            startingLegGeometryIndex,
        ) { speed() }
        val maxSpeed = mergeAnnotationProperty(
            oldAnnotation,
            newAnnotation,
            startingLegGeometryIndex,
        ) { maxspeed() }
        // unrecognized properties migrate from new annotation
        return (newAnnotation?.toBuilder() ?: LegAnnotation.builder())
            .congestion(congestion)
            .congestionNumeric(congestionNumeric)
            .maxspeed(maxSpeed)
            .distance(distance)
            .duration(duration)
            .speed(speed)
            .build()
    }

    private fun <T> mergeAnnotationProperty(
        oldAnnotation: LegAnnotation,
        newAnnotation: LegAnnotation?,
        startingLegGeometryIndex: Int,
        propertyExtractor: LegAnnotation.() -> List<T>?,
    ): List<T>? {
        val oldProperty = oldAnnotation.propertyExtractor() ?: return null
        val newProperty = newAnnotation?.propertyExtractor() ?: emptyList()
        val expectedSize = oldProperty.size
        if (expectedSize < startingLegGeometryIndex) {
            logE(
                "Annotations sizes mismatch: " +
                    "index=$startingLegGeometryIndex, expected_size=$expectedSize",
                LOG_CATEGORY
            )
            return null
        }
        val result = mutableListOf<T>()
        repeat(startingLegGeometryIndex) { result.add(oldProperty[it]) }
        repeat(min(expectedSize - startingLegGeometryIndex, newProperty.size)) {
            result.add(newProperty[it])
        }
        val filledSize = result.size
        repeat(expectedSize - filledSize) { result.add(oldProperty[it + filledSize]) }

        return result
    }
}
