# Telemetry architecture

Phase 3.1 corrections keep raw counter and source metadata separate from
presentation. Linux guest and guest_nice fields are parsed for diagnostics but
are not added a second time to the CPU total because Linux already accounts for
them in user and nice time. PSI averages outside the validated 0–100 range are
treated as malformed. Storage diagnostics retain both slave traversal and
holder evidence, while only the slave chain is used to identify physical
backing media.

FluxLab’s telemetry path is a capability-driven, sampled pipeline:

'AndroidTelemetryRepository' owns one sampler per application process. It retains
the previous CPU/GPU counter snapshots and exposes a shared StateFlow to Overview,
detail screens, and benchmark telemetry. 'AndroidDeviceTelemetrySource' performs
reads on 'Dispatchers.IO'; the repository retains at most 120 snapshots. Starting
the same interval is idempotent, and reset only occurs on explicit stop, source
reset, counter reset, process recreation, or material provider configuration change.
Compose animation interpolates only between stored points; it never reads
procfs/sysfs on an animation frame.

## CPU

The aggregate 'cpu' and per-core 'cpuN' lines in '/proc/stat' are parsed as
non-negative cumulative counters. Optional guest fields are accepted when present
and treated as zero when absent. Utilization requires two valid snapshots:

    idle = idle + iowait
    nonIdle = user + nice + system + irq + softirq + steal
    total = idle + nonIdle
    utilization = (totalDelta - idleDelta) / totalDelta

Negative deltas, counter resets, zero intervals, malformed lines, and disappearing
cores produce a gap or temporary-unavailable state. The first snapshot is
'Collecting initial samples', never 'Unsupported'. Aggregate and per-core histories
are independent and capped at 120 points.

CPU frequency probing checks per-core and cpufreq policy nodes dynamically. It
accepts validated Hz, kHz, and MHz magnitudes, records the raw source/unit, reports
online state and governor, and uses policy/related-core groups to avoid counting a
shared policy more than once in aggregate frequency.

## GPU

KGSL and devfreq probing remain the existing provider architecture. Model values are
shown conservatively with the raw model in technical details. GPU busy/total counters
are validated and utilization is calculated from interval deltas; the first counter
sample and resets have explicit unavailable reasons. Frequency is still useful when
utilization is not exposed. Raw paths remain diagnostic metadata rather than primary
content.

## Memory, PSI, and ZRAM

'/proc/meminfo' values are retained in KiB. Used memory is documented as
'MemTotal - MemAvailable' when 'MemAvailable' is present; a documented legacy
fallback uses free, buffers, and reclaimable cache when it is absent. Reclaimable
cache is 'Cached + SReclaimable - Shmem', clamped at zero. Active swap devices
are parsed from '/proc/swaps'; aggregate fallback usage is 'SwapTotal - SwapFree'.
Multiple '/sys/block/zram*' devices are summed and their per-device availability
is retained through warnings and aggregate metadata.

PSI is read from the file '/proc/pressure/memory' (without a trailing slash).
The parser accepts independent 'some' and 'full' records and their avg10, avg60,
and avg300 fields. Missing PSI is an explicit capability reason. ZRAM is discovered
from '/sys/block/zram*'; disk size, memory used, original data, compressed data,
and compression ratio are only shown when exposed.

## Storage topology and health

The relevant application-private mount is selected from '/proc/self/mountinfo'
with '/proc/mounts' fallback. Its major:minor source is resolved through
'/sys/dev/block' and then '/sys/class/block'. Device-mapper, mapper aliases,
logical partitions, parent relationships, and slave chains are traversed with
cycle prevention. StatFs data-volume capacity is separate from physical nominal
capacity, and every resolution step is retained in technical diagnostics.

UFS requires UFS/ufshcd/controller evidence. eMMC requires an mmc/mmcblk or
validated eMMC descriptor signal. Modern-device age or marketing names alone never
select a transport. Capacity, filesystem, mount state, and identity are independent
of health descriptors.

UFS/eMMC lifetime descriptors are coarse ranges. Missing, malformed, permission
denied, unsupported, and unknown health states remain distinct from storage
performance. App-private buffered reads and writes measure filesystem-visible
workloads and are not labeled as physical UFS/eMMC speed.

## Thermal and battery

Thermal nodes are normalized from Celsius, deci-Celsius, or milli-Celsius with
source-specific plausibility bounds. A raw zero placeholder is rejected; valid
cold temperatures are not rejected solely for being low. Sensors are grouped
conservatively (CPU, GPU, battery, charger, modem, Wi-Fi, skin, PMIC, camera,
display, other), and raw names/paths are behind technical disclosure. Readiness
is localized separately from Android thermal status.

BatteryManager and validated power-supply nodes retain raw values and units.
Charge counter, design capacity, and full-charge capacity are normalized to mAh
only for validated compatible units. Estimated state of health is
'fullChargeCapacity / designCapacity * 100' and is labeled as an estimate;
Android’s health enum is a separate field. Current sign is preserved, power uses
absolute current magnitude multiplied by validated voltage, and cycle count,
charging limits, and graph tabs are unavailable when the device does not expose
them.

## Localization and limitations

Technical identifiers such as CPU, GPU, UFS, eMMC, ZRAM, F2FS, and EXT4 are kept
stable. User-facing state labels and explanations are Android string resources in
English and Indonesian. OEM-specific sysfs permissions, vendor unit conventions,
missing UFS/eMMC descriptors, GPU busy counter semantics, and devices without PSI
remain unsupported telemetry rather than fabricated values.
