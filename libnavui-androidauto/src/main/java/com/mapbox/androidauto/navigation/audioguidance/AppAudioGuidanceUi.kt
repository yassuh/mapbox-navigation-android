package com.mapbox.androidauto.navigation.audioguidance

import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.mapbox.navigation.ui.voice.api.MapboxAudioGuidance
import com.mapbox.navigation.ui.voice.api.MapboxAudioGuidanceState
import com.mapbox.navigation.ui.voice.view.MapboxSoundButton
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

fun Fragment.attachAudioGuidance(
    mapboxSoundButton: MapboxSoundButton
) {
    val lifecycleOwner = viewLifecycleOwner
    val flow = MapboxAudioGuidance.getInstance().stateFlow()
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { state ->
                if (state.isMuted) {
                    mapboxSoundButton.mute()
                } else {
                    mapboxSoundButton.unmute()
                }
                mapboxSoundButton.visibility = if (state.isPlayable) View.VISIBLE else View.GONE
            }
        }
    }
    mapboxSoundButton.setOnClickListener {
        MapboxAudioGuidance.getInstance().toggle()
    }
}

/**
 * Use this function to mute the audio guidance for a lifecycle.
 */
fun Lifecycle.muteAudioGuidance() {
    addObserver(object : DefaultLifecycleObserver {
        lateinit var initialState: MapboxAudioGuidanceState
        override fun onResume(owner: LifecycleOwner) {
            with(MapboxAudioGuidance.getInstance()) {
                initialState = stateFlow().value
                mute()
            }
        }

        override fun onPause(owner: LifecycleOwner) {
            if (!initialState.isMuted) {
                MapboxAudioGuidance.getInstance().unMute()
            }
        }
    })
}
