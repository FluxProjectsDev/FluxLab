package com.febricahyaa.fluxlab.integration

import com.febricahyaa.fluxlab.model.RootCommand
import com.febricahyaa.fluxlab.model.RootCommandResult
import com.febricahyaa.fluxlab.model.RootGateway
import com.febricahyaa.fluxlab.model.RootState
import com.febricahyaa.fluxlab.model.SnapshotFreshness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeParsingTest {
    @Test
    fun `Flux detection combines module runtime daemon and metadata signals`() = runTest {
        val gateway = FakeRootGateway(
            mapOf(
                RootCommand.FLUX_MODULE_EXISTS to success(),
                RootCommand.FLUX_CONFIG_EXISTS to success(),
                RootCommand.READ_MODULE_PROP to success("id=flux\nversion=2.4.1\nversionCode=241\n"),
                RootCommand.FIND_FLUX_DAEMON to success("418\n"),
                RootCommand.READ_CURRENT_PROFILE to success("balanced\n"),
                RootCommand.STAT_SYNTHESIS_STATUS to success("1700000000\n"),
                RootCommand.READ_RUNTIME_STATUS to success("kernel_is_gki=1\n"),
            ),
        )

        val result = FluxRuntimeAdapter(gateway).readInstallation()

        assertTrue(result.installed)
        assertEquals(true, result.enabled)
        assertTrue(result.runtimeAvailable)
        assertEquals(true, result.daemonAlive)
        assertEquals("2.4.1", result.versionName)
        assertEquals(241L, result.versionCode)
        assertEquals("balanced", result.activeProfile)
        assertEquals("GKI", result.kernelType)
        assertTrue(result.synthesisCoreAvailable)
        assertEquals(1_700_000_000_000L, result.lastStatusUpdateEpochMs)
    }

    @Test
    fun `Flux metadata parser tolerates comments malformed lines and unknown fields`() {
        val values = FluxRuntimeAdapter(FakeRootGateway()).parseProperties(
            "id=flux\nmalformed\nfuture_field=preserved\nversion = 1.0\n",
        )

        assertEquals("flux", values["id"])
        assertEquals("preserved", values["future_field"])
        assertEquals("1.0", values["version"])
        assertFalse(values.containsKey("malformed"))
    }

    @Test
    fun `SynthesisCore JSON preserves unknown fields and validates known values`() {
        val reader = SynthesisCoreAdapter(FakeRootGateway(), nowEpochMs = { 10_000L })
        val snapshot = reader.parse(
            """{"schema_version":2,"sequence":9,"focused_package":"com.example.app","focused_pid":321,"focused_uid":10321,"screen_awake":true,"battery_saver":false,"charging_state":1,"thermal_headroom":0.42,"audio_active":"yes","future_signal":"ready"}""",
            modifiedEpochMs = 8_500L,
        )

        assertEquals(2, snapshot.schemaVersion)
        assertEquals(9L, snapshot.sequence)
        assertEquals("com.example.app", snapshot.focusedPackage)
        assertEquals(321, snapshot.foregroundPid)
        assertEquals(10321, snapshot.foregroundUid)
        assertEquals(true, snapshot.screenAwake)
        assertEquals(false, snapshot.batterySaver)
        assertEquals(true, snapshot.charging)
        assertEquals(0.42, snapshot.thermalHeadroom!!, 0.0001)
        assertEquals("ready", snapshot.extensions["future_signal"])
        assertEquals(SnapshotFreshness.FRESH, snapshot.freshness)
    }

    @Test
    fun `SynthesisCore key value and legacy focused app formats are supported`() {
        val snapshot = SynthesisCoreAdapter(FakeRootGateway()).parse(
            "schema_version 2\nfocused_app com.example.legacy 77 10077\nscreen_on=on\nzen_mode 2\nkernel_is_gki yes\n",
        )

        assertEquals("com.example.legacy", snapshot.focusedPackage)
        assertEquals(77, snapshot.foregroundPid)
        assertEquals(10077, snapshot.foregroundUid)
        assertEquals(true, snapshot.screenAwake)
        assertEquals(2, snapshot.zenMode)
        assertEquals(true, snapshot.kernelIsGki)
    }

    @Test
    fun `malformed partial payload yields nullable fields and parser warnings`() {
        val snapshot = SynthesisCoreAdapter(FakeRootGateway()).parse(
            "{\"schema_version\":2,\"focused_pid\":-7,\"screen_awake\":\"perhaps\"",
        )

        assertEquals(2, snapshot.schemaVersion)
        assertNull(snapshot.foregroundPid)
        assertNull(snapshot.screenAwake)
        assertTrue(snapshot.warnings.any { it.contains("not closed") })
        assertTrue(snapshot.warnings.any { it.contains("invalid focused_pid") })
        assertTrue(snapshot.warnings.any { it.contains("invalid screen_awake") })
    }

    private class FakeRootGateway(
        private val results: Map<RootCommand, RootCommandResult> = emptyMap(),
        rootState: RootState = RootState.Available,
    ) : RootGateway {
        private val mutableState = MutableStateFlow(rootState)
        override val state: StateFlow<RootState> = mutableState
        override suspend fun checkAvailability(force: Boolean): RootState = state.value
        override suspend fun execute(command: RootCommand): RootCommandResult =
            results[command] ?: RootCommandResult(1, "", "unavailable")
    }

    private companion object {
        fun success(stdout: String = ""): RootCommandResult = RootCommandResult(0, stdout, "")
    }
}
