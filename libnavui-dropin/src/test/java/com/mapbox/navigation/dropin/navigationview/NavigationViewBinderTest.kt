package com.mapbox.navigation.dropin.navigationview

import com.mapbox.navigation.base.ExperimentalPreviewMapboxNavigationAPI
import com.mapbox.navigation.dropin.EmptyBinder
import com.mapbox.navigation.dropin.ViewBinderCustomization
import com.mapbox.navigation.dropin.actionbutton.ActionButtonDescription
import com.mapbox.navigation.dropin.actionbutton.ActionButtonDescription.Position.END
import com.mapbox.navigation.dropin.actionbutton.ActionButtonDescription.Position.START
import com.mapbox.navigation.dropin.infopanel.InfoPanelBinder
import com.mapbox.navigation.dropin.infopanel.MapboxInfoPanelBinder
import com.mapbox.navigation.ui.base.lifecycle.UIBinder
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@ExperimentalPreviewMapboxNavigationAPI
internal class NavigationViewBinderTest {

    lateinit var sut: NavigationViewBinder

    @Before
    fun setUp() {
        sut = NavigationViewBinder()
    }

    @Test
    fun `applyCustomization should update NON NULL binders`() {
        val c = customization()

        sut.applyCustomization(c)

        assertEquals(c.speedLimitBinder, sut.speedLimit.value)
        assertEquals(c.maneuverBinder, sut.maneuver.value)
        assertEquals(c.roadNameBinder, sut.roadName.value)
        assertEquals(c.infoPanelTripProgressBinder, sut.infoPanelTripProgressBinder.value)
        assertEquals(c.infoPanelHeaderBinder, sut.infoPanelHeaderBinder.value)
        assertEquals(c.infoPanelContentBinder, sut.infoPanelContentBinder.value)
        assertEquals(c.actionButtonsBinder, sut.actionButtonsBinder.value)
        assertEquals(c.customActionButtons, sut.customActionButtons.value)
    }

    @Test
    fun `applyCustomization should reset to default binders`() {
        sut.applyCustomization(customization())

        sut.applyCustomization(
            ViewBinderCustomization().apply {
                speedLimitBinder = UIBinder.USE_DEFAULT
                maneuverBinder = UIBinder.USE_DEFAULT
                roadNameBinder = UIBinder.USE_DEFAULT
                infoPanelTripProgressBinder = UIBinder.USE_DEFAULT
                infoPanelHeaderBinder = UIBinder.USE_DEFAULT
                infoPanelContentBinder = UIBinder.USE_DEFAULT
                actionButtonsBinder = UIBinder.USE_DEFAULT
                customActionButtons = emptyList()
                infoPanelBinder = InfoPanelBinder.defaultBinder()
            }
        )

        assertTrue(sut.speedLimit.value == null)
        assertTrue(sut.maneuver.value == null)
        assertTrue(sut.roadName.value == null)
        assertTrue(sut.infoPanelTripProgressBinder.value == null)
        assertTrue(sut.infoPanelHeaderBinder.value == null)
        assertTrue(sut.infoPanelContentBinder.value == null)
        assertTrue(sut.actionButtonsBinder.value == null)
        assertTrue(sut.customActionButtons.value.isEmpty())
        assertTrue(sut.infoPanelBinder.value is MapboxInfoPanelBinder)
    }

    private fun customization() = ViewBinderCustomization().apply {
        speedLimitBinder = EmptyBinder()
        maneuverBinder = EmptyBinder()
        roadNameBinder = EmptyBinder()
        infoPanelTripProgressBinder = EmptyBinder()
        infoPanelHeaderBinder = EmptyBinder()
        infoPanelContentBinder = EmptyBinder()
        actionButtonsBinder = EmptyBinder()
        customActionButtons = listOf(
            ActionButtonDescription(mockk(), START),
            ActionButtonDescription(mockk(), START),
            ActionButtonDescription(mockk(), END)
        )
        infoPanelBinder = mockk()
    }
}