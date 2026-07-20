package com.febricahyaa.fluxlab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.metrics.performance.JankStats

class MainActivity : ComponentActivity() {
    private lateinit var jankStats: JankStats

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        jankStats = JankStats.createAndTrack(window) { /* Local frame observations only. */ }
        setContent { FluxLabRoot() }
    }

    override fun onDestroy() {
        jankStats.isTrackingEnabled = false
        super.onDestroy()
    }
}
