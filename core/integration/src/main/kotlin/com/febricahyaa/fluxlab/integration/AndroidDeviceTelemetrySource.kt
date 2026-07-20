package com.febricahyaa.fluxlab.integration

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.view.WindowManager
import androidx.core.content.getSystemService
import com.febricahyaa.fluxlab.model.BatteryTelemetry
import com.febricahyaa.fluxlab.model.BatteryDiagnostics
import com.febricahyaa.fluxlab.model.BatteryCapacityNormalizer
import com.febricahyaa.fluxlab.model.BatteryCapacityUnit
import com.febricahyaa.fluxlab.model.BatteryHealthEstimator
import com.febricahyaa.fluxlab.model.BatteryHealthParser
import com.febricahyaa.fluxlab.model.StorageIdentityProvider
import com.febricahyaa.fluxlab.model.BenchmarkPreset
import com.febricahyaa.fluxlab.model.ThermalEligibilityEvaluator
import com.febricahyaa.fluxlab.model.CpuCoreTelemetry
import com.febricahyaa.fluxlab.model.CpuIdentity
import com.febricahyaa.fluxlab.model.CpuIdentityProvider
import com.febricahyaa.fluxlab.model.CpuTelemetry
import com.febricahyaa.fluxlab.model.DeviceTelemetrySnapshot
import com.febricahyaa.fluxlab.model.DeviceTelemetrySource
import com.febricahyaa.fluxlab.model.GpuCapabilityProvider
import com.febricahyaa.fluxlab.model.MemoryTelemetry
import com.febricahyaa.fluxlab.model.RootGateway
import com.febricahyaa.fluxlab.model.SystemTelemetry
import com.febricahyaa.fluxlab.model.ThermalTelemetry
import com.febricahyaa.fluxlab.model.ThermalZone
import com.febricahyaa.fluxlab.model.ChargingState
import com.febricahyaa.fluxlab.model.ThermalEligibility
import com.febricahyaa.fluxlab.integration.BatteryCurrentUnit
import com.febricahyaa.fluxlab.integration.BatteryVoltageUnit
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AndroidDeviceTelemetrySource(
    context: Context,
    rootGateway: RootGateway? = null,
    private val cpuIdentityProvider: CpuIdentityProvider = AndroidCpuIdentityProvider(),
    private val gpuCapabilityProvider: GpuCapabilityProvider = AndroidGpuCapabilityProvider(rootGateway = rootGateway),
    private val storageIdentityProvider: StorageIdentityProvider = AndroidStorageIdentityProvider(context),
) : DeviceTelemetrySource {
    private val appContext = context.applicationContext
    private var previousCpu: Map<String, CpuTimes> = emptyMap()

    override suspend fun sample(): DeviceTelemetrySnapshot = withContext(Dispatchers.IO) {
        val battery = readBattery()
        val storage = storageIdentityProvider.read()
        val cpuIdentity = cpuIdentityProvider.identify()
        DeviceTelemetrySnapshot(
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            cpu = readCpu(cpuIdentity),
            memory = readMemory(),
            thermal = readThermal(battery),
            battery = battery,
            gpu = gpuCapabilityProvider.sample(),
            system = readSystem(),
            storage = storage,
        )
    }

    override fun stream(intervalMs: Long): Flow<DeviceTelemetrySnapshot> = flow {
        val boundedInterval = intervalMs.coerceIn(250L, 10_000L)
        while (currentCoroutineContext().isActive) {
            emit(sample())
            delay(boundedInterval)
        }
    }

    private fun readCpu(identity: CpuIdentity): CpuTelemetry {
        val current = readText("/proc/stat")?.let(ProcStatParser::parse).orEmpty()
        val previous = previousCpu
        previousCpu = current
        val count = identity.coreCount.coerceAtLeast(1)
        val cores = (0 until count).map { index ->
            val directory = "/sys/devices/system/cpu/cpu$index/cpufreq"
            CpuCoreTelemetry(
                index = index,
                usagePercent = ProcStatParser.usage(previous["cpu$index"], current["cpu$index"]),
                currentFrequencyKhz = readLong("$directory/scaling_cur_freq"),
                minimumFrequencyKhz = readLong("$directory/cpuinfo_min_freq") ?: readLong("$directory/scaling_min_freq"),
                maximumFrequencyKhz = readLong("$directory/cpuinfo_max_freq") ?: readLong("$directory/scaling_max_freq"),
                online = index == 0 || readLong("/sys/devices/system/cpu/cpu$index/online") != 0L,
                governor = readText("$directory/scaling_governor")?.trim()?.takeIf(String::isNotEmpty),
            )
        }
        return CpuTelemetry(
            totalUsagePercent = ProcStatParser.usage(previous["cpu"], current["cpu"]),
            cores = cores,
            architecture = identity.supportedAbis.firstOrNull() ?: System.getProperty("os.arch").orEmpty(),
            coreCount = count,
            identity = identity,
        )
    }

    private fun readMemory(): MemoryTelemetry {
        val info = readText("/proc/meminfo")?.let(MemInfoParser::parse).orEmpty()
        val total = info["MemTotal"]
        val available = info["MemAvailable"]
        val cached = listOf("Cached", "SReclaimable").sumOf { info[it] ?: 0L } - (info["Shmem"] ?: 0L)
        val swapTotal = info["SwapTotal"]
        val swapFree = info["SwapFree"]
        val psi = readText("/proc/pressure/memory")?.let(PsiParser::parse) ?: PsiValues(null, null)
        val major = readText("/proc/vmstat")?.lineSequence()?.firstOrNull { it.startsWith("pgmajfault ") }
            ?.substringAfter(' ')?.trim()?.toLongOrNull()
        val zram = File("/sys/block").listFiles()?.filter { it.name.startsWith("zram") }
            ?.mapNotNull { readLong(File(it, "disksize").path) }?.sum()?.takeIf { it > 0 }
        return MemoryTelemetry(
            totalKb = total,
            availableKb = available,
            usedKb = if (total != null && available != null) (total - available).coerceAtLeast(0L) else null,
            cachedKb = cached.takeIf { it > 0 },
            swapTotalKb = swapTotal,
            swapUsedKb = if (swapTotal != null && swapFree != null) (swapTotal - swapFree).coerceAtLeast(0L) else null,
            zramBytes = zram,
            psiSomeAvg10 = psi.someAvg10,
            psiFullAvg10 = psi.fullAvg10,
            majorPageFaults = major,
        )
    }

    private fun readBattery(): BatteryTelemetry {
        val manager = appContext.getSystemService<BatteryManager>()
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)?.takeIf { it >= 0 }
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val percent = level?.let { it * 100 / scale }
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status?.let { it == BatteryManager.BATTERY_STATUS_CHARGING || it == BatteryManager.BATTERY_STATUS_FULL }
        val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        val plugType = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            BatteryManager.BATTERY_PLUGGED_DOCK -> "dock"
            else -> null
        }
        val temperature = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
            ?.takeUnless { it == Int.MIN_VALUE }?.div(10.0)
        val current = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeUnless { it == Long.MIN_VALUE }
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)?.toLong()?.takeIf { it > 0 }
        val counter = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        val averageCurrent = manager?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
            ?.takeUnless { it == Long.MIN_VALUE }
        val state = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING -> ChargingState.CHARGING
            BatteryManager.BATTERY_STATUS_FULL -> ChargingState.FULL
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> ChargingState.NOT_CHARGING
            BatteryManager.BATTERY_STATUS_DISCHARGING -> ChargingState.DISCHARGING
            else -> ChargingState.UNKNOWN
        }
        val reading = BatteryPowerNormalizer.read(current, BatteryCurrentUnit.MICROAMPERES, voltage, BatteryVoltageUnit.MILLIVOLTS, state)
        return BatteryTelemetry(percent, charging, plugType, temperature, current, voltage, counter, reading.calculatedPowerWatts, reading.calculatedPowerWatts != null, currentRaw = current, currentUnitSource = reading.currentUnitSource, voltageRaw = voltage, voltageUnitSource = reading.voltageUnitSource, calculatedPowerWatts = reading.calculatedPowerWatts, chargingState = state, powerConfidence = reading.powerConfidence, powerWarnings = reading.powerWarnings, normalizedCurrentAmps = reading.normalizedCurrentAmps, normalizedVoltageVolts = reading.normalizedVoltageVolts, powerSource = reading.powerSource, averageCurrentMicroamps = averageCurrent, diagnostics = readBatteryDiagnostics(intent, counter))
    }

    private fun readBatteryDiagnostics(intent: Intent?, chargeCounterMicroAh: Long?): BatteryDiagnostics {
        val health = BatteryHealthParser.parse(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1))
        val batteryDir = File("/sys/class/power_supply").listFiles()?.firstOrNull { it.name.equals("battery", true) || it.name.startsWith("battery", true) }
        fun node(vararg names: String): Pair<Long, String>? = names.firstNotNullOfOrNull { name ->
            val file = batteryDir?.let { File(it, name) }
            file?.let { readLong(it.path)?.let { value -> value to it.path } }
        }
        val currentCharge = BatteryCapacityNormalizer.read(chargeCounterMicroAh, BatteryCapacityUnit.MICROAMP_HOURS, "BatteryManager charge counter")
        val fullRaw = node("charge_full")
        val designRaw = node("charge_full_design")
        val full = BatteryCapacityNormalizer.read(fullRaw?.first, BatteryCapacityUnit.MICROAMP_HOURS, fullRaw?.second)
        val design = BatteryCapacityNormalizer.read(designRaw?.first, BatteryCapacityUnit.MICROAMP_HOURS, designRaw?.second)
        val soh = BatteryHealthEstimator.estimate(full.normalizedMilliAmpHours, design.normalizedMilliAmpHours)
        val cycle = node("cycle_count", "battery_cycle")
        val cycleCount = cycle?.first?.takeIf { it in 0L..100000L }?.toInt()
        val warnings = buildList {
            if (soh != null) add("State of Health is an estimate based on OEM capacity calibration")
            if (health == com.febricahyaa.fluxlab.model.BatteryHealthState.UNKNOWN) add("Android battery health state is unavailable")
            if (cycle != null && cycleCount == null) add("Battery cycle count is outside the validated range")
        }
        return BatteryDiagnostics(
            status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)?.toString(),
            health = health,
            technology = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY),
            present = intent?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false),
            cycleCount = cycleCount,
            cycleCountSource = cycle?.second,
            cycleCountWarning = if (cycle == null) "Cycle count is not exposed by this device" else null,
            capacity = com.febricahyaa.fluxlab.model.BatteryCapacityDiagnostics(
                currentCharge = currentCharge, designCapacity = design, fullChargeCapacity = full, estimatedSoHPercent = soh,
                sohWarning = if (soh != null) "Estimated from validated capacity sources; OEM calibration may affect accuracy" else null,
                capacitySource = listOfNotNull(currentCharge.source, design.source, full.source).distinct().joinToString().ifBlank { null },
                capacityConfidence = listOf(currentCharge.confidence, design.confidence, full.confidence).maxByOrNull { it.ordinal } ?: BatteryPowerConfidence.UNAVAILABLE,
            ),
            warnings = warnings,
            sources = listOfNotNull(currentCharge.source, full.source, design.source, cycle?.second).distinct(),
        )
    }

    private fun readThermal(battery: BatteryTelemetry): ThermalTelemetry {
        val power = appContext.getSystemService<PowerManager>()
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) power?.currentThermalStatus else null
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            power?.getThermalHeadroom(0)?.toDouble()?.takeIf { it.isFinite() && it >= 0.0 }
        } else null
        val zones = File("/sys/class/thermal").listFiles()?.filter { it.name.startsWith("thermal_zone") }
            ?.take(64)?.mapNotNull { directory ->
                val type = readText(File(directory, "type").path)?.trim()?.takeIf(String::isNotEmpty)
                    ?: return@mapNotNull null
                val raw = readLong(File(directory, "temp").path)
                val normalized = raw?.let(ThermalSensorParser::normalize)
                ThermalZone(type, normalized?.celsius, directory.path, normalized?.rawValue, normalized?.unitSource ?: com.febricahyaa.fluxlab.model.TemperatureUnitSource.UNKNOWN)
            }.orEmpty()
        val evidenceZone = zones.firstOrNull { zone ->
            val type = zone.name.lowercase()
            zone.temperatureCelsius != null && ("cpu" in type || "soc" in type || "ap" == type)
        }
        val primary = evidenceZone?.temperatureCelsius ?: battery.temperatureCelsius
        val source = evidenceZone?.let { "thermal zone: ${it.name}" }
            ?: battery.temperatureCelsius?.let { "battery sensor" }
        val eligibility = status?.let { ThermalEligibilityEvaluator.evaluate(it, BenchmarkPreset.QUICK).eligibility } ?: ThermalEligibility.THERMAL_STATUS_UNAVAILABLE
        return ThermalTelemetry(status, headroom, zones, battery.temperatureCelsius, primary, source, eligibility, emptyList())
    }

    @Suppress("DEPRECATION")
    private fun readSystem(): SystemTelemetry {
        val refresh = appContext.getSystemService<WindowManager>()?.defaultDisplay?.refreshRate?.toDouble()
            ?.takeIf { it > 0.0 }
        val kernel = System.getProperty("os.version").orEmpty()
        val isGki = when {
            kernel.contains("-android") || kernel.contains("gki", true) -> true
            kernel.isBlank() -> null
            else -> null
        }
        return SystemTelemetry(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE,
            sdk = Build.VERSION.SDK_INT,
            buildFingerprint = Build.FINGERPRINT,
            kernelVersion = kernel,
            isGki = isGki,
            uptimeMs = SystemClock.elapsedRealtime(),
            refreshRateHz = refresh,
        )
    }

    private fun readText(path: String): String? = runCatching {
        File(path).takeIf { it.isFile && it.canRead() }?.readText()
    }.getOrNull()

    private fun readLong(path: String): Long? = readText(path)?.trim()?.toLongOrNull()
}
