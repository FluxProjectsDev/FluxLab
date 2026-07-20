package com.febricahyaa.fluxlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.doOnAttach
import androidx.metrics.performance.JankStats

class MainActivity : ComponentActivity() {
    private var jankStats: JankStats? = null
    private var isResumed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FluxLabRoot() }
        window.decorView.doOnAttach {
            if (jankStats == null) {
                val frameTelemetry = (application as FluxLabApplication).container.frameTelemetry
                jankStats = JankStats.createAndTrack(window, frameTelemetry::record).apply {
                    isTrackingEnabled = isResumed
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        jankStats?.isTrackingEnabled = true
    }

    override fun onPause() {
        jankStats?.isTrackingEnabled = false
        isResumed = false
        super.onPause()
    }
}
