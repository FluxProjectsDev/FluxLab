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
import com.febricahyaa.fluxlab.model.CpuCoreTelemetry
import com.febricahyaa.fluxlab.model.CpuTelemetry
import com.febricahyaa.fluxlab.model.DeviceTelemetrySnapshot
import com.febricahyaa.fluxlab.model.DeviceTelemetrySource
import com.febricahyaa.fluxlab.model.GpuTelemetry
import com.febricahyaa.fluxlab.model.MemoryTelemetry
import com.febricahyaa.fluxlab.model.SystemTelemetry
import com.febricahyaa.fluxlab.model.ThermalTelemetry
import com.febricahyaa.fluxlab.model.ThermalZone
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AndroidDeviceTelemetrySource(context: Context) : DeviceTelemetrySource {
    private val appContext = context.applicationContext
    private var previousCpu: Map<String, CpuTimes> = emptyMap()

    override suspend fun sample(): DeviceTelemetrySnapshot = withContext(Dispatchers.IO) {
        val battery = readBattery()
        DeviceTelemetrySnapshot(
            elapsedRealtimeMs = SystemClock.elapsedRealtime(),
            cpu = readCpu(),
            memory = readMemory(),
            thermal = readThermal(battery),
            battery = battery,
            gpu = readGpu(),
            system = readSystem(),
        )
    }

    override fun stream(intervalMs: Long): Flow<DeviceTelemetrySnapshot> = flow {
        val boundedInterval = intervalMs.coerceIn(250L, 10_000L)
        while (currentCoroutineContext().isActive) {
            emit(sample())
            delay(boundedInterval)
        }
    }

    private fun readCpu(): CpuTelemetry {
        val current = readText("/proc/stat")?.let(ProcStatParser::parse).orEmpty()
        val previous = previousCpu
        previousCpu = current
        val count = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
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
            architecture = Build.SUPPORTED_ABIS.firstOrNull() ?: System.getProperty("os.arch").orEmpty(),
            coreCount = count,
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
            ?.takeUnless { it == Long.MIN_VALUE }
        val power = BatteryPowerEstimator.watts(current, voltage)
        return BatteryTelemetry(percent, charging, plugType, temperature, current, voltage, counter, power, power != null)
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
                val celsius = raw?.let { if (kotlin.math.abs(it) > 500) it / 1_000.0 else it.toDouble() }
                    ?.takeIf { it in -40.0..200.0 }
                ThermalZone(type, celsius, directory.path)
            }.orEmpty()
        val evidenceZone = zones.firstOrNull { zone ->
            val type = zone.name.lowercase()
            zone.temperatureCelsius != null && ("cpu" in type || "soc" in type || "ap" == type)
        }
        val primary = evidenceZone?.temperatureCelsius ?: battery.temperatureCelsius
        val source = evidenceZone?.let { "thermal zone: ${it.name}" }
            ?: battery.temperatureCelsius?.let { "battery sensor" }
        return ThermalTelemetry(status, headroom, zones, battery.temperatureCelsius, primary, source)
    }

    private fun readGpu(): GpuTelemetry {
        val kgsl = "/sys/class/kgsl/kgsl-3d0"
        val kgslCurrent = readLong("$kgsl/gpuclk")
        if (kgslCurrent != null) {
            return GpuTelemetry(
                vendor = "Qualcomm",
                model = readText("$kgsl/gpu_model")?.trim(),
                currentFrequencyHz = kgslCurrent,
                minimumFrequencyHz = readLong("$kgsl/devfreq/min_freq"),
                maximumFrequencyHz = readLong("$kgsl/devfreq/max_freq"),
                frequencySource = "$kgsl/gpuclk",
            )
        }
        val devfreq = File("/sys/class/devfreq").listFiles()?.firstOrNull {
            val name = it.name.lowercase()
            "gpu" in name || "mali" in name
        }
        val current = devfreq?.let { readLong(File(it, "cur_freq").path) }
        val name = devfreq?.name?.lowercase().orEmpty()
        val vendor = when {
            "mali" in name -> "Arm"
            "gpu" in name -> Build.HARDWARE.takeIf(String::isNotBlank)
            else -> null
        }
        return GpuTelemetry(
            vendor = vendor,
            model = devfreq?.name,
            currentFrequencyHz = current,
            minimumFrequencyHz = devfreq?.let { readLong(File(it, "min_freq").path) },
            maximumFrequencyHz = devfreq?.let { readLong(File(it, "max_freq").path) },
            frequencySource = current?.let { File(devfreq, "cur_freq").path },
        )
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
