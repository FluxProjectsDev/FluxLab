package com.febricahyaa.fluxlab

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

val FluxDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF7168),
    onPrimary = Color(0xFF3B0907),
    secondary = Color(0xFFD9A6A0),
    tertiary = Color(0xFFFFC866),
    background = Color(0xFF151313),
    surface = Color(0xFF1D1A1A),
    surfaceVariant = Color(0xFF2A2525),
    onBackground = Color(0xFFF4EDEB),
    onSurface = Color(0xFFF4EDEB),
    onSurfaceVariant = Color(0xFFD1C1BE),
)

val FluxLightColorScheme = lightColorScheme(
    primary = Color(0xFFC83F3A),
    onPrimary = Color.White,
    secondary = Color(0xFF7E4A47),
    tertiary = Color(0xFF8A5A00),
    background = Color(0xFFFFF9F7),
    surface = Color(0xFFFFF9F7),
    surfaceVariant = Color(0xFFF3E2DF),
    onBackground = Color(0xFF211A19),
    onSurface = Color(0xFF211A19),
    onSurfaceVariant = Color(0xFF6B5A57),
)

/** Shared FluxLab dimensions. Screens should use these tokens instead of new magic values. */
object FluxSpacing {
    val screenHorizontalPadding = 16.dp
    val screenVerticalPadding = 12.dp
    val cardGap = 12.dp
    val compactGap = 8.dp
    val cardInternalPadding = 16.dp
    val largeCardPadding = 20.dp
    val sectionGap = 20.dp
    val heroIconContainer = 48.dp
}

object FluxShapes {
    val heroRadius = 24.dp
    val metricCardRadius = 18.dp
    val smallCardRadius = 14.dp
    val chipRadius = 12.dp
    val buttonRadius = 14.dp
    val graphRadius = 14.dp

    val material = Shapes(
        extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        small = androidx.compose.foundation.shape.RoundedCornerShape(smallCardRadius),
        medium = androidx.compose.foundation.shape.RoundedCornerShape(metricCardRadius),
        large = androidx.compose.foundation.shape.RoundedCornerShape(heroRadius),
        extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
    )
}

object FluxTypography {
    val material = Typography(
        displaySmall = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    )
}

object FluxMotion {
    const val standardDurationMs = 220
    const val reducedDurationMs = 0
}

object FluxChartTokens {
    val overviewSparklineHeight = 52.dp
    val detailChartHeight = 196.dp
    val activeRunVisualHeight = 204.dp
    val strokeWidth = 3.dp
    val gaugeStrokeWidth = 8.dp
}

object FluxElevation {
    val hero = 2.dp
    val card = 1.dp
    val raised = 3.dp
}

enum class FluxMetric { CPU, GPU, MEMORY, STORAGE, THERMAL, BATTERY, SUCCESS, WARNING, ERROR, UNAVAILABLE }

data class FluxMetricColorSet(
    val cpu: Color,
    val gpu: Color,
    val memory: Color,
    val storage: Color,
    val thermal: Color,
    val battery: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val unavailable: Color,
)

/** Semantic colors intentionally remain stable when Material dynamic color is selected. */
object FluxMetricColors {
    val dark = FluxMetricColorSet(
        cpu = Color(0xFFFF7168),
        gpu = Color(0xFFBE9BFF),
        memory = Color(0xFF72B5FF),
        storage = Color(0xFFFFC866),
        thermal = Color(0xFFFF8C5B),
        battery = Color(0xFF76D99B),
        success = Color(0xFF76D99B),
        warning = Color(0xFFFFC866),
        error = Color(0xFFFF7168),
        unavailable = Color(0xFF9B9A9F),
    )
    val light = FluxMetricColorSet(
        cpu = Color(0xFFC83F3A),
        gpu = Color(0xFF7047A8),
        memory = Color(0xFF1769AA),
        storage = Color(0xFF8A5A00),
        thermal = Color(0xFFB74C18),
        battery = Color(0xFF167447),
        success = Color(0xFF167447),
        warning = Color(0xFF8A5A00),
        error = Color(0xFFC83F3A),
        unavailable = Color(0xFF6B6A70),
    )

    fun forTheme(dark: Boolean): FluxMetricColorSet = if (dark) dark else light

    fun color(metric: FluxMetric, dark: Boolean): Color = with(forTheme(dark)) {
        when (metric) {
            FluxMetric.CPU -> cpu
            FluxMetric.GPU -> gpu
            FluxMetric.MEMORY -> memory
            FluxMetric.STORAGE -> storage
            FluxMetric.THERMAL -> thermal
            FluxMetric.BATTERY -> battery
            FluxMetric.SUCCESS -> success
            FluxMetric.WARNING -> warning
            FluxMetric.ERROR -> error
            FluxMetric.UNAVAILABLE -> unavailable
        }
    }
}

@Composable
fun fluxMetricColor(metric: FluxMetric): Color = FluxMetricColors.color(
    metric = metric,
    dark = MaterialTheme.colorScheme.background.luminance() < .5f,
)

@Composable
fun FluxGauge(
    progress: Float?,
    accent: Color,
    modifier: Modifier = Modifier.size(72.dp),
    label: String? = null,
) {
    val description = label ?: progress?.let { "${(it * 100f).toInt()}%" } ?: stringResource(R.string.unavailable)
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = FluxChartTokens.gaugeStrokeWidth.toPx()
            val inset = stroke / 2f
            drawArc(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            progress?.takeIf { it.isFinite() }?.let {
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * it.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
fun FluxSparkline(
    samples: List<Double?>,
    color: Color,
    modifier: Modifier,
    description: String,
) {
    Canvas(modifier.semantics { contentDescription = description }) {
        val valid = samples.mapIndexedNotNull { index, value ->
            value?.takeIf { it.isFinite() }?.let { index to it }
        }
        if (valid.size < 2) return@Canvas
        val min = valid.minOf { it.second }
        val max = max(valid.maxOf { it.second }, min + 0.0001)
        var previous: Offset? = null
        valid.forEach { (index, value) ->
            val point = Offset(
                index.toFloat() / samples.lastIndex.coerceAtLeast(1) * size.width,
                size.height - ((value - min) / (max - min)).toFloat() * size.height,
            )
            previous?.let { drawLine(color, it, point, FluxChartTokens.strokeWidth.toPx(), StrokeCap.Round) }
            previous = point
        }
    }
}
