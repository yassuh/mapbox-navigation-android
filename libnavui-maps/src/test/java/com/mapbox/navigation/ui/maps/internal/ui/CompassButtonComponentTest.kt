package com.mapbox.navigation.ui.maps.internal.ui

import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isVisible
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.MapboxMap
import com.mapbox.maps.plugin.animation.flyTo
import com.mapbox.maps.plugin.delegates.MapPluginExtensionsDelegate
import com.mapbox.maps.plugin.delegates.listeners.OnCameraChangeListener
import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.ui.base.view.MapboxExtendableButton
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalPreviewMapboxNavigationAPI::class)
class CompassButtonComponentTest {

    private val mapboxMap = mockk<MapboxMap>(relaxed = true)
    private val mapView = mockk<MapView>(relaxed = true) {
        every { getMapboxMap() } returns mapboxMap
    }
    private val iconImage = mockk<AppCompatImageView>(relaxed = true)
    private val compassButton = mockk<MapboxExtendableButton>(relaxed = true) {
        every { iconImage } returns this@CompassButtonComponentTest.iconImage
    }
    private val mapboxNavigation = mockk<MapboxNavigation>()
    private lateinit var component: CompassButtonComponent

    @Before
    fun setUp() {
        mockkStatic(MapPluginExtensionsDelegate::flyTo)
    }

    @After
    fun tearDown() {
        unmockkStatic(MapPluginExtensionsDelegate::flyTo)
    }

    @Test
    fun `onAttached when compass is disabled`() {
        component = CompassButtonComponent(compassButton, mapView, false)

        component.onAttached(mapboxNavigation)

        verify(exactly = 1) { compassButton.isVisible = false }
        verify(exactly = 0) { compassButton.setOnClickListener(any()) }
        verify(exactly = 0) { mapboxMap.addOnCameraChangeListener(any()) }
    }

    @Test
    fun `onDetached when compass is disabled`() {
        component = CompassButtonComponent(compassButton, mapView, false)

        component.onAttached(mapboxNavigation)
        component.onDetached(mapboxNavigation)

        verify(exactly = 1) { compassButton.setOnClickListener(null) }
    }

    @Test
    fun `onAttached when compass is enabled but mapView is null`() {
        component = CompassButtonComponent(compassButton, null, true)

        component.onAttached(mapboxNavigation)

        verify(exactly = 1) { compassButton.isVisible = true }
        verify(exactly = 0) { compassButton.setOnClickListener(any()) }
        verify(exactly = 0) { mapboxMap.addOnCameraChangeListener(any()) }
    }

    @Test
    fun `onDetached when compass is enabled but mapView is null`() {
        component = CompassButtonComponent(compassButton, null, true)

        component.onAttached(mapboxNavigation)
        component.onDetached(mapboxNavigation)

        verify(exactly = 1) { compassButton.setOnClickListener(null) }
    }

    @Test
    fun `onAttached when compass is enabled and has mapView`() {
        component = CompassButtonComponent(compassButton, mapView, true)

        component.onAttached(mapboxNavigation)

        verify(exactly = 1) { compassButton.isVisible = true }
        verify(exactly = 1) { compassButton.setOnClickListener(any()) }
        verify(exactly = 1) { mapboxMap.addOnCameraChangeListener(any()) }
    }

    @Test
    fun `onDetached when compass is enabled and has mapView`() {
        val cameraListeners = mutableListOf<OnCameraChangeListener>()
        component = CompassButtonComponent(compassButton, mapView, true)
        component.onAttached(mapboxNavigation)
        verify(exactly = 1) { mapboxMap.addOnCameraChangeListener(capture(cameraListeners)) }

        component.onDetached(mapboxNavigation)

        verify(exactly = 1) { compassButton.setOnClickListener(null) }
        verify(exactly = 1) { mapboxMap.removeOnCameraChangeListener(cameraListeners.first()) }
    }

    @Test
    fun `compass button click returns to north position`() {
        val cameraOptions = mutableListOf<CameraOptions>()
        val btnOnClickListeners = mutableListOf<View.OnClickListener>()
        component = CompassButtonComponent(compassButton, mapView, true)

        component.onAttached(mapboxNavigation)

        verify(exactly = 1) { compassButton.setOnClickListener(capture(btnOnClickListeners)) }
        btnOnClickListeners.first().onClick(mockk())
        verify { mapboxMap.flyTo(capture(cameraOptions)) }
        assertEquals(0.0, cameraOptions.first().bearing)
    }

    @Test
    fun `camera state change rotates the image accordingly`() {
        val cameraChangeListeners = mutableListOf<OnCameraChangeListener>()
        component = CompassButtonComponent(compassButton, mapView, true)

        component.onAttached(mapboxNavigation)

        verify(exactly = 1) { mapboxMap.addOnCameraChangeListener(capture(cameraChangeListeners)) }
        every { mapboxMap.cameraState } returns mockk {
            every { bearing } returns 78.12
        }
        cameraChangeListeners.first().onCameraChanged(mockk())
        verify { iconImage.rotation = -78.12f }
    }
}