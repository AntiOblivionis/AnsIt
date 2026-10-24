package com.antioblivionis.ansit

import android.graphics.Paint
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.min
import kotlin.math.roundToInt

private val OverlaySaver = listSaver<OverlayConfig, Float>(
    save = { listOf(it.left, it.top, it.width, it.height, it.transparency) },
    restore = { OverlayConfig(it[0], it[1], it[2], it[3], it[4]) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OverlayEditor(
    initial: OverlayConfig,
    saving: Boolean = false,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onSave: (OverlayConfig) -> Unit,
    onCancel: () -> Unit,
) {
    var draft by rememberSaveable(stateSaver = OverlaySaver) { mutableStateOf(initial) }
    BackHandler { if (!saving) onCancel() }
    Scaffold(
        modifier = Modifier.testTag("overlay_editor"),
        topBar = {
            TopAppBar(title = { Text("调整模式") }, navigationIcon = {
                IconButton(onClick = onCancel, enabled = !saving) { Icon(Icons.Default.Close, "取消调整") }
            }, actions = {
                TextButton(onClick = { onSave(draft) }, enabled = !saving, modifier = Modifier.testTag("save_overlay")) {
                    if (saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Text("保存")
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("背景透明度", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text((draft.transparency * 100).roundToInt().toString() + "%",
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Slider(value = draft.transparency * 100, onValueChange = { draft = draft.withTransparency(it / 100) },
                        enabled = !saving, valueRange = 0f..100f, steps = 99,
                        modifier = Modifier.testTag("transparency_slider").semantics { contentDescription = "背景透明度" })
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("宽 " + (draft.width * 100).roundToInt() + "% · 高 " + (draft.height * 100).roundToInt() + "%",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f))
                        TextButton(onClick = { draft = OverlayConfig.defaults() }, enabled = !saving,
                            modifier = Modifier.testTag("reset_overlay")) { Text("恢复默认") }
                    }
                }
            }
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("拖动框体移动 · 拖动四角缩放", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                // Root draws on the whole default display, including when this Activity is in split screen.
                val display = LocalContext.current.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
                val window = LocalWindowInfo.current.containerSize
                val ratio = if (display.width() > 0 && display.height() > 0) display.width().toFloat() / display.height()
                    else window.width.coerceAtLeast(1).toFloat() / window.height.coerceAtLeast(1)
                val previewWidth = min(maxWidth.value, maxHeight.value * ratio).dp
                val previewHeight = previewWidth / ratio
                FramePreview(draft, { draft = it }, enabled = !saving,
                    modifier = Modifier.size(previewWidth, previewHeight).clip(RoundedCornerShape(16.dp))
                        .testTag("frame_preview"))
            }
            Text("按当前屏幕比例预览，保存后用于实际答案框", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(12.dp))
        }
    }
}

private const val MOVE = 16

internal fun dragTarget(config: OverlayConfig, size: IntSize, at: Offset, radius: Float): Int {
    val b = config.pixels(size.width, size.height)
    val x = b.x().toFloat(); val y = b.y().toFloat()
    val right = x + b.width(); val bottom = y + b.height()
    val corners = listOf(
        Offset(x, y) to (OverlayConfig.LEFT or OverlayConfig.TOP),
        Offset(right, y) to (OverlayConfig.RIGHT or OverlayConfig.TOP),
        Offset(x, bottom) to (OverlayConfig.LEFT or OverlayConfig.BOTTOM),
        Offset(right, bottom) to (OverlayConfig.RIGHT or OverlayConfig.BOTTOM),
    )
    val closest = corners.minBy { (it.first - at).getDistanceSquared() }
    if ((closest.first - at).getDistanceSquared() <= radius * radius) return closest.second
    return if (at.x in x..right && at.y in y..bottom) MOVE else -1
}

@Composable
private fun FramePreview(config: OverlayConfig, onChange: (OverlayConfig) -> Unit, enabled: Boolean, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val current by rememberUpdatedState(config)
    val change by rememberUpdatedState(onChange)
    val active by rememberUpdatedState(enabled)
    val hitRadius = with(LocalDensity.current) { 24.dp.toPx() }
    Canvas(modifier.background(scheme.surfaceContainerLow).pointerInput(Unit) {
        var target = -1
        detectDragGestures(onDragStart = { at ->
            target = if (active && size.width > 0 && size.height > 0) dragTarget(current, size, at, hitRadius) else -1
        }, onDragEnd = { target = -1 }, onDragCancel = { target = -1 }) { event, amount ->
            if (target >= 0 && active) {
                event.consume()
                val dx = amount.x / size.width; val dy = amount.y / size.height
                change(if (target == MOVE) current.move(dx, dy) else current.resize(target, dx, dy))
            }
        }
    }) {
        val w = size.width; val h = size.height
        if (w < 1 || h < 1) return@Canvas
        // A local, synthetic page makes transparency visible without capturing or sending any screen content.
        drawRoundRect(scheme.surfaceContainerHigh, Offset(w * .06f, h * .10f),
            Size(w * .88f, h * .14f), CornerRadius(w * .02f))
        repeat(12) { row ->
            drawRoundRect(scheme.onSurface.copy(alpha = .10f),
                Offset(w * .09f, h * (.32f + row * .043f)),
                Size(w * (if (row % 3 == 2) .54f else .82f), h * .009f), CornerRadius(4f))
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = scheme.onSurfaceVariant.toArgb(); textSize = w * .052f
        }
        drawContext.canvas.nativeCanvas.drawText("屏幕内容预览", w * .10f, h * .18f, label)
        val b = config.pixels(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
        val native = drawContext.canvas.nativeCanvas
        val saved = native.save()
        try {
            native.translate(b.x().toFloat(), b.y().toFloat())
            AnswerPainter.draw(native, AnswerPainter.SAMPLE, b.width(), b.height(), w.roundToInt(), h.roundToInt(), config)
        } finally { native.restoreToCount(saved) }
        val origin = Offset(b.x().toFloat(), b.y().toFloat())
        drawRoundRect(scheme.primary, origin, Size(b.width().toFloat(), b.height().toFloat()),
            CornerRadius(28 * min(w, h) / 1200), style = Stroke(2.dp.toPx()))
        val corners = listOf(origin, origin + Offset(b.width().toFloat(), 0f),
            origin + Offset(0f, b.height().toFloat()), origin + Offset(b.width().toFloat(), b.height().toFloat()))
        corners.forEach { point ->
            drawCircle(scheme.surface, 7.dp.toPx(), point)
            drawCircle(scheme.primary, 7.dp.toPx(), point, style = Stroke(2.dp.toPx()))
        }
    }
}
