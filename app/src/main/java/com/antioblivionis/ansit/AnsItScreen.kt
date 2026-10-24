package com.antioblivionis.ansit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt

@Composable
internal fun AnsItScreen(model: MainViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(model) {
        model.events.collect { notice ->
            if (notice.saved) { focus.clearFocus(); keyboard?.hide() }
            snackbar.showSnackbar(notice.text)
        }
    }
    if (state.showAdjustment) {
        OverlayEditor(state.overlay, state.overlaySaving, snackbar, model::saveOverlay, model::cancelAdjustment)
    } else {
        AnsItContent(state, snackbar, model::edit, model::save, model::toggle, model::check,
            model::showGuide, model::dismissGuide, model::restartSystemUi, model::showAdjustment,
            model::editSearch, model::saveSearch, model::cancelValidation, model::editTrigger, model::saveTrigger)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnsItContent(
    state: MainState,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    onEdit: (ApiForm) -> Unit = {},
    onSave: () -> Unit = {},
    onToggle: () -> Unit = {},
    onCheck: () -> Unit = {},
    onShowGuide: () -> Unit = {},
    onDismissGuide: () -> Unit = {},
    onRestart: () -> Unit = {},
    onAdjustment: () -> Unit = {},
    onSearchEdit: (SearchForm) -> Unit = {},
    onSearchSave: () -> Unit = {},
    onCancelValidation: () -> Unit = {},
    onTriggerEdit: (String) -> Unit = {},
    onTriggerSave: () -> Unit = {},
) {
    var confirmRestart by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { insets ->
        Box(Modifier.fillMaxSize().testTag("main_screen").padding(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 600.dp).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(top = 20.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111A35)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AnsIt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text("屏幕里的问题，随时解答", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showAbout = true }, modifier = Modifier.testTag("open_about")) {
                        Icon(Icons.Default.Info, "关于 AnsIt")
                    }
                }

                ServiceCard(state, onToggle, onCheck, onAdjustment)

                if (!state.hookCurrent || state.root != RootAccess.GRANTED) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (state.root != RootAccess.GRANTED) "完成首次设置" else "SystemUI 模块尚未就绪",
                                    style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                Text(if (state.snapshot?.hook == RootServiceStarter.Hook.LOADED) "模块版本有变化，请重启系统界面。"
                                    else "授权 Root，并在 LSPosed 中启用系统界面作用域。",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = onShowGuide) { Text("去设置") }
                        }
                    }
                }

                TriggerSettingsCard(state, onTriggerEdit, onTriggerSave)
                ConfigurationCard(state, onEdit, onSave, onCancelValidation)
                SearchConfigurationCard(state, onSearchEdit, onSearchSave, onCancelValidation)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("长按状态栏，就能问", style = MaterialTheme.typography.titleMedium)
                    Text("按住状态栏电池附近达到已保存阈值（" + state.triggerHoldMillis + " ms），感到轻振后松手。再次长按可取消解答或收起答案。无法识别电池布局时，请长按状态栏右侧。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("关闭应用后，已启动的服务仍会运行；重启手机后需重新开始。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onShowGuide) { Text("使用引导") }
                    TextButton(onClick = { showAbout = true }) { Text("关于 AnsIt · " + BuildConfig.VERSION_NAME) }
                }
            }
        }
    }

    if (state.showGuide) {
        ModalBottomSheet(onDismissRequest = onDismissGuide, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            SetupGuide(state, onCheck, { confirmRestart = true }, onDismissGuide)
        }
    }
    if (confirmRestart) {
        AlertDialog(
            onDismissRequest = { confirmRestart = false },
            title = { Text("重启系统界面？") },
            text = { Text("状态栏和导航栏会短暂消失，系统会重新启动 SystemUI。请先在 LSPosed 启用 AnsIt，并仅勾选“系统界面（com.android.systemui）”。") },
            confirmButton = { TextButton(onClick = { confirmRestart = false; onRestart() }) { Text("重启") } },
            dismissButton = { TextButton(onClick = { confirmRestart = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun TriggerSettingsCard(state: MainState, onEdit: (String) -> Unit, onSave: () -> Unit) {
    val millis = state.triggerText.toIntOrNull()
    val valid = millis != null && millis in TriggerConfig.MIN_MS..TriggerConfig.MAX_MS
    val enabled = !state.triggerLoading && !state.triggerSaving
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("触发设置", style = MaterialTheme.typography.titleLarge)
            Text("调整长按电池多久后判定。仅在服务运行且确认就绪时响应；停止后不接管长按。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(state.triggerText, onEdit, enabled = enabled, singleLine = true,
                label = { Text("触发阈值（毫秒）") }, supportingText = { Text("200–3000 ms，默认 650 ms") },
                isError = !valid, modifier = Modifier.fillMaxWidth().testTag("trigger_millis"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done))
            Slider(value = (millis ?: TriggerConfig.DEFAULT_MS).coerceIn(TriggerConfig.MIN_MS, TriggerConfig.MAX_MS).toFloat(),
                onValueChange = { onEdit(((it / 50).roundToInt() * 50).toString()) }, enabled = enabled,
                valueRange = TriggerConfig.MIN_MS.toFloat()..TriggerConfig.MAX_MS.toFloat(), steps = 55,
                modifier = Modifier.testTag("trigger_slider"))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.triggerDirty) "有未保存的更改" else "用于识屏与收起答案",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { onEdit(TriggerConfig.DEFAULT_MS.toString()) }, enabled = enabled,
                    modifier = Modifier.testTag("reset_trigger")) { Text("恢复默认") }
            }
            FilledTonalButton(onClick = onSave, enabled = enabled && valid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_trigger")) {
                if (state.triggerSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.triggerSaving) "正在保存…" else "保存阈值")
            }
            state.triggerNote?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (state.triggerError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServiceCard(state: MainState, onToggle: () -> Unit, onCheck: () -> Unit, onAdjustment: () -> Unit) {
    val running = state.service == RootServiceStarter.State.RUNNING
    val scheme = MaterialTheme.colorScheme
    val container = scheme.surfaceContainer
    val content = scheme.onSurface
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = container, contentColor = content)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("识屏服务", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(when (state.service) {
                        RootServiceStarter.State.RUNNING -> "运行中"
                        RootServiceStarter.State.STOPPED -> "已停止"
                        RootServiceStarter.State.UNKNOWN -> if (state.root == RootAccess.UNAVAILABLE) "状态暂时不可用" else "准备好再开始"
                    }, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
                IconButton(onClick = onCheck, enabled = !state.busy, modifier = Modifier.testTag("service_refresh")) {
                    if (state.operation == Operation.CHECK || state.operation == Operation.UPDATE)
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = content)
                    else Icon(Icons.Default.Refresh, "重新检查服务与模块")
                }
            }
            Text(when (state.operation) {
                Operation.UPDATE -> "正在将运行中的 Root 服务更新到当前版本…"
                Operation.RESTART -> "正在等待系统界面重新加载模块…"
                Operation.START -> "正在从当前安装包启动服务…"
                Operation.STOP -> "正在取消任务并停止服务…"
                Operation.ADJUST -> "正在收起当前答案，进入调整模式…"
                else -> if (running) "长按状态栏电池区域即可识屏，答案会直接显示在屏幕上。"
                    else "保存模型配置后，开启服务即可使用。"
            }, style = MaterialTheme.typography.bodyMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusPill(if (state.root == RootAccess.GRANTED) "Root 已授权" else "Root 待检查", content)
                StatusPill(if (state.hookCurrent) "模块已生效" else "模块待就绪", content)
            }
            Button(onClick = onToggle, enabled = !state.busy && (running || (!state.saving && !state.searchSaving)),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("service_control")) {
                if (running) Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(LocalContentColor.current))
                else Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text(when (state.operation) {
                    Operation.START -> "正在启动…"
                    Operation.STOP -> "正在停止…"
                    Operation.UPDATE -> "更新服务中…"
                    else -> when (state.service) {
                        RootServiceStarter.State.RUNNING -> "停止"
                        RootServiceStarter.State.STOPPED -> "开始"
                        RootServiceStarter.State.UNKNOWN -> "授权并检查"
                    }
                })
            }
            state.error?.let { Text(it, color = scheme.error, style = MaterialTheme.typography.bodySmall) }
            FilledTonalButton(onClick = onAdjustment, enabled = !state.overlayLoading && !state.busy && !state.saving && !state.searchSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("adjustment_mode")) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("调整模式")
            }
            Text(if (state.checkedAt == null) "首次使用需完成 Root 和 LSPosed 设置"
                else "上次核对 " + android.text.format.DateFormat.format("HH:mm:ss", state.checkedAt) +
                    if (running && state.serviceCurrent) " · 服务为当前版本" else " · 可点右上角刷新",
                style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = .7f))
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Surface(shape = CircleShape, color = color.copy(alpha = .08f), contentColor = color) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ConfigurationCard(state: MainState, onEdit: (ApiForm) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    val form = state.form
    var showKey by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val enabled = !state.saving && !state.searchSaving
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("模型配置", style = MaterialTheme.typography.titleLarge)
                Text("使用支持图片输入的 OpenAI 兼容接口。每次识屏都是独立会话。",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(form.address, { onEdit(form.copy(address = it)) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("接口地址") }, placeholder = { Text("https://api.example.com/v1") }, singleLine = true,
                enabled = enabled, supportingText = { Text("支持基础地址或完整 /chat/completions 地址") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next))
            OutlinedTextField(form.key, { onEdit(form.copy(key = it)) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("API Key") }, singleLine = true, enabled = enabled,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next))
            OutlinedTextField(form.model, { onEdit(form.copy(model = it)) }, modifier = Modifier.fillMaxWidth(),
                label = { Text("模型名称") }, placeholder = { Text("填写支持视觉输入的模型 ID") }, singleLine = true, enabled = enabled,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }))
            if (state.dirty) Text("有未保存的更改", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("检测会发送一张本地测试图片；启用搜索时也会核对工具调用。", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onSave, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("model_save_check")) {
                if (state.saving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.saving) "正在检测…" else "检测并保存")
            }
            state.modelCheck?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (state.modelCheckPassed == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            if (state.saving) TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_model_check")) { Text("取消检测") }
        }
    }
}

@Composable
private fun SearchConfigurationCard(state: MainState, onEdit: (SearchForm) -> Unit, onSave: () -> Unit, onCancel: () -> Unit) {
    val form = state.search
    val enabled = !state.saving && !state.searchSaving
    var showKey by remember { mutableStateOf(false) }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("DeepSeek 网页搜索", style = MaterialTheme.typography.titleLarge)
            Text("让主模型在需要时搜索公开网页。启用后，主模型须同时支持图片输入与工具调用。",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("允许 AI 按需搜索", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = form.enabled, onCheckedChange = { onEdit(form.copy(enabled = it)) }, enabled = enabled,
                    modifier = Modifier.testTag("search_enabled"))
            }
            OutlinedTextField(form.key, { onEdit(form.copy(key = it)) }, modifier = Modifier.fillMaxWidth().testTag("search_key"),
                label = { Text("DeepSeek API Key") }, singleLine = true, enabled = enabled,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") } },
                supportingText = { Text("使用 DeepSeek 平台 Key；关闭搜索会保留 Key。") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done))
            if (state.searchDirty) Text("有未保存的更改", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("启用时会执行测试搜索；检测成功后保存。检测请求按接口计费规则处理。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onSave, enabled = enabled,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("search_save_check")) {
                if (state.searchSaving) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.searchSaving) { if (form.enabled) "正在检测…" else "正在保存…" }
                    else if (form.enabled) "检测并保存" else "保存关闭状态")
            }
            state.searchCheck?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                color = if (state.searchCheckPassed == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            if (state.searchSaving) TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_search_check")) { Text("取消检测") }
        }
    }
}

@Composable
private fun SetupGuide(state: MainState, onCheck: () -> Unit, onRestart: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().testTag("setup_guide").verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("让 AnsIt 准备就绪", style = MaterialTheme.typography.headlineSmall)
                Text("首次安装与升级后，可在这里检查。", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭使用引导") }
        }
        SetupStep("1", "允许 Root 权限", if (state.root == RootAccess.GRANTED) "Root 权限已确认。"
            else "点击下方按钮，在 Root 管理器弹窗中允许 AnsIt；若曾拒绝，请先在管理器中改为允许。", state.root == RootAccess.GRANTED) {
            FilledTonalButton(onClick = onCheck, enabled = !state.busy) {
                Text(if (state.operation == Operation.CHECK) "等待授权与自检…" else if (state.root == RootAccess.GRANTED) "重新自检" else "授权并自检")
            }
        }
        HorizontalDivider()
        SetupStep("2", "激活 LSPosed 模块", "打开 LSPosed → 模块 → AnsIt，启用模块。作用域仅勾选“系统界面（com.android.systemui）”，然后返回这里。", state.hookCurrent) {
            Text(when {
                state.hookCurrent -> "已确认：SystemUI 正在加载本版模块"
                state.snapshot?.hook == RootServiceStarter.Hook.LOADED -> "检测到旧版模块，需重启系统界面"
                state.snapshot?.hook == RootServiceStarter.Hook.ABSENT -> "尚未检测到本版模块，请核对作用域并重启系统界面"
                else -> "模块状态待检查；仅开启模块开关还不够"
            }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        HorizontalDivider()
        SetupStep("3", "重启系统界面使模块生效", "首次激活或模块升级后需要重启 SystemUI。状态栏和导航栏会短暂消失，系统随后会恢复。", state.hookCurrent) {
            FilledTonalButton(onClick = onRestart, enabled = state.root == RootAccess.GRANTED && !state.busy) {
                Text(if (state.operation == Operation.RESTART) "正在重启与核对…" else "重启 SystemUI")
            }
        }
        Text("完成后，填写并保存模型配置，再点击“开始”。若还保留旧包 dev.ansit，请在 LSPosed 禁用旧模块，避免重复触发。",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(if (state.hookCurrent) "完成，去配置模型" else "知道了") }
    }
}

@Composable
private fun SetupStep(number: String, title: String, detail: String, done: Boolean, action: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Surface(shape = CircleShape, color = if (done) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(32.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (done) Icon(Icons.Default.Check, contentDescription = "已完成", modifier = Modifier.size(18.dp))
                else Text(number, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}

@Preview(name = "Stopped · Light", widthDp = 393, heightDp = 900, showBackground = true)
@Composable
private fun StoppedPreview() = AnsItTheme(dark = false) {
    AnsItContent(MainState(root = RootAccess.GRANTED, hookCurrent = true, overlayLoading = false,
        snapshot = RootServiceStarter.Snapshot(RootServiceStarter.State.STOPPED, "", "", RootServiceStarter.Hook.LOADED, "", "")))
}

@Preview(name = "Running · Dark", widthDp = 393, heightDp = 900, showBackground = true)
@Composable
private fun RunningPreview() = AnsItTheme(dark = true) {
    AnsItContent(MainState(root = RootAccess.GRANTED, hookCurrent = true, serviceCurrent = true, overlayLoading = false,
        snapshot = RootServiceStarter.Snapshot(RootServiceStarter.State.RUNNING, "", "", RootServiceStarter.Hook.LOADED, "", "")))
}
