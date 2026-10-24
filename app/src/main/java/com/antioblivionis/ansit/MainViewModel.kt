package com.antioblivionis.ansit

import android.app.Application
import android.system.Os
import android.os.SystemClock
import android.util.AtomicFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

internal enum class Operation { NONE, CHECK, UPDATE, START, STOP, RESTART, ADJUST }
internal enum class RootAccess { UNCHECKED, GRANTED, UNAVAILABLE }
internal data class ApiForm(val address: String = "", val key: String = "", val model: String = "")
internal data class SearchForm(val enabled: Boolean = false, val key: String = "")
internal data class MainState(
    val form: ApiForm = ApiForm(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val modelCheck: String? = null,
    val modelCheckPassed: Boolean? = null,
    val search: SearchForm = SearchForm(),
    val searchDirty: Boolean = false,
    val searchSaving: Boolean = false,
    val searchCheck: String? = null,
    val searchCheckPassed: Boolean? = null,
    val operation: Operation = Operation.NONE,
    val root: RootAccess = RootAccess.UNCHECKED,
    val snapshot: RootServiceStarter.Snapshot? = null,
    val hookCurrent: Boolean = false,
    val serviceCurrent: Boolean = false,
    val showGuide: Boolean = false,
    val error: String? = null,
    val checkedAt: Long? = null,
    val overlay: OverlayConfig = OverlayConfig.defaults(),
    val overlayLoading: Boolean = true,
    val overlaySaving: Boolean = false,
    val showAdjustment: Boolean = false,
    val triggerText: String = TriggerConfig.DEFAULT_MS.toString(),
    val triggerHoldMillis: Int = TriggerConfig.DEFAULT_MS,
    val triggerLoading: Boolean = true,
    val triggerSaving: Boolean = false,
    val triggerDirty: Boolean = false,
    val triggerNote: String? = null,
    val triggerError: Boolean = false,
) {
    val busy get() = operation != Operation.NONE
    val service get() = snapshot?.state ?: RootServiceStarter.State.UNKNOWN
}
internal data class UiNotice(val text: String, val saved: Boolean = false)

internal class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("setup", 0)
    private val apk = application.applicationInfo.sourceDir
    private val configFile = File(application.filesDir, ApiConfig.FILE_NAME)
    private val logFile = File(application.filesDir, "root-service.log")
    private val digest by lazy { BuildIdentity.digest(apk) }
    private var savedForm: ApiForm? = null
    private var savedSearch: SearchForm? = null
    private val searchFile = File(application.filesDir, SearchConfig.FILE_NAME)
    private val triggerFile = File(application.filesDir, TriggerConfig.FILE_NAME)
    private val checks = ConfigurationChecks()
    private var validationCall: LlmCall? = null
    private var lastControlFinished = -1L
    private val mutable = MutableStateFlow(MainState(showGuide = !prefs.getBoolean("guide_seen", false)))
    val state = mutable.asStateFlow()
    private val notices = Channel<UiNotice>(Channel.BUFFERED)
    val events = notices.receiveAsFlow()
    private val backend = object : ServiceController.Backend {
        override fun inspect() = RootServiceStarter.inspect(apk)
        override fun start() = RootServiceStarter.start(apk, logFile)
        override fun stop() = RootServiceStarter.stop(apk)
    }

    init {
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                try { ApiConfig.read(configFile).let { ApiForm(it.address, it.key, it.model) } }
                catch (_: Exception) { null }
            }
            savedForm = loaded
            mutable.update {
                if (!it.dirty && loaded != null) it.copy(form = loaded) else it
            }
            val layout = withContext(Dispatchers.IO) {
                try { OverlayConfig.read(File(application.filesDir, OverlayConfig.FILE_NAME)) }
                catch (_: Exception) { OverlayConfig.defaults() }
            }
            mutable.update { it.copy(overlay = layout, overlayLoading = false) }
            val search = withContext(Dispatchers.IO) {
                try { SearchConfig.read(searchFile).let { SearchForm(it.enabled, it.key) } }
                catch (_: Exception) { SearchForm() }
            }
            savedSearch = search
            mutable.update { if (!it.searchDirty) it.copy(search = search) else it }
            val trigger = withContext(Dispatchers.IO) { try { TriggerConfig.read(triggerFile) } catch (_: Exception) { null } }
            mutable.update {
                it.copy(triggerText = (trigger?.holdMillis ?: TriggerConfig.DEFAULT_MS).toString(),
                    triggerHoldMillis = trigger?.holdMillis ?: TriggerConfig.DEFAULT_MS, triggerLoading = false,
                    triggerDirty = trigger == null, triggerError = trigger == null,
                    triggerNote = if (trigger == null) "触发设置读取失败，请重新保存" else null)
            }
        }
    }

    fun edit(form: ApiForm) { mutable.update { it.copy(form = form, dirty = form != savedForm, modelCheck = null, modelCheckPassed = null) } }
    fun editSearch(form: SearchForm) {
        mutable.update { it.copy(search = form, searchDirty = form != savedSearch, searchCheck = null, searchCheckPassed = null) }
    }
    fun editTrigger(value: String) {
        mutable.update { it.copy(triggerText = value, triggerDirty = value.toIntOrNull() != it.triggerHoldMillis,
            triggerNote = null, triggerError = false) }
    }
    fun saveTrigger() {
        if (state.value.triggerLoading || state.value.triggerSaving) return
        val millis = state.value.triggerText.toIntOrNull()
        if (millis == null || millis !in TriggerConfig.MIN_MS..TriggerConfig.MAX_MS) {
            mutable.update { it.copy(triggerNote = "请输入 200–3000 的整数毫秒", triggerError = true) }
            return
        }
        mutable.update { it.copy(triggerSaving = true, triggerNote = null, triggerError = false) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { PrivateSettings.write(triggerFile, TriggerConfig(millis).json()) }
                val message = "已保存，从下一次长按使用 $millis ms"
                mutable.update { it.copy(triggerText = millis.toString(), triggerHoldMillis = millis, triggerDirty = false, triggerNote = message) }
                notices.send(UiNotice(message, saved = true))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { mutable.update { it.copy(triggerNote = "未能确认触发设置保存，请重试", triggerError = true) } }
            finally { mutable.update { it.copy(triggerSaving = false) } }
        }
    }
    fun cancelValidation() { validationCall?.cancel() }

    fun saveSearch() {
        if (state.value.saving || state.value.searchSaving) return
        val form = state.value.search
        val call = LlmCall()
        validationCall = call
        mutable.update { it.copy(searchSaving = true, searchCheck = null, searchCheckPassed = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val config = SearchConfig(form.enabled, form.key)
                    val model = if (config.enabled && configFile.isFile) ApiConfig.read(configFile) else null
                    checks.saveSearch(call, config, model, searchFile)
                }
                savedSearch = form
                val message = result + "，已保存"
                mutable.update { it.copy(searchDirty = it.search != form, searchCheck = message,
                    searchCheckPassed = if (!form.enabled) null else true) }
                notices.send(UiNotice(message, saved = true))
            } catch (cancelled: CancellationException) {
                if (!kotlin.coroutines.coroutineContext.isActive) throw cancelled
                mutable.update { it.copy(searchCheck = "检测已取消，原配置未更改", searchCheckPassed = null) }
            } catch (error: Exception) {
                val message = validationMessage(error)
                mutable.update { it.copy(searchCheck = message, searchCheckPassed = false) }
                notices.send(UiNotice(message))
            } finally {
                if (validationCall === call) validationCall = null
                mutable.update { it.copy(searchSaving = false) }
            }
        }
    }

    fun showAdjustment() {
        if (state.value.overlayLoading || state.value.busy) return
        if (state.value.service != RootServiceStarter.State.RUNNING) {
            mutable.update { it.copy(showAdjustment = true) }
            return
        }
        mutable.update { it.copy(operation = Operation.ADJUST) }
        viewModelScope.launch {
            try {
                val actual = withContext(Dispatchers.IO) {
                    RootServiceStarter.prepareAdjustment(apk)
                    backend.inspect()
                }
                applySnapshot(actual)
                mutable.update { it.copy(showAdjustment = true) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { recover(error) }
            finally { lastControlFinished = SystemClock.elapsedRealtime(); mutable.update { it.copy(operation = Operation.NONE) } }
        }
    }
    fun cancelAdjustment() {
        if (!state.value.overlaySaving) mutable.update { it.copy(showAdjustment = false) }
    }
    fun saveOverlay(config: OverlayConfig) {
        if (state.value.overlaySaving) return
        mutable.update { it.copy(overlaySaving = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val target = AtomicFile(File(getApplication<Application>().filesDir, OverlayConfig.FILE_NAME))
                    val output = target.startWrite()
                    try {
                        Os.fchmod(output.fd, 0x180)
                        output.write(config.json().toString().toByteArray(Charsets.UTF_8))
                        target.finishWrite(output)
                    } catch (error: Exception) { target.failWrite(output); throw error }
                }
                var message = "显示设置已保存，下次识屏使用新布局"
                if (state.value.service == RootServiceStarter.State.RUNNING) {
                    val refreshed = withContext(Dispatchers.IO) {
                        try { RootServiceStarter.refreshOverlay(apk); true } catch (_: Exception) { false }
                    }
                    message = if (refreshed) "显示设置已保存，后续答案使用新布局"
                        else "显示设置已保存；未确认当前答案刷新，下次识屏时生效"
                }
                mutable.update { it.copy(overlay = config, showAdjustment = false) }
                notices.send(UiNotice(message))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { notices.send(UiNotice("显示设置保存失败，请重试")) }
            finally { mutable.update { it.copy(overlaySaving = false) } }
        }
    }

    fun save() {
        if (state.value.saving || state.value.searchSaving) return
        val form = state.value.form
        val call = LlmCall()
        validationCall = call
        mutable.update { it.copy(saving = true, modelCheck = null, modelCheckPassed = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val config = ApiConfig(form.address, form.key, form.model)
                    val search = SearchConfig.read(searchFile)
                    checks.saveModel(call, config, search.enabled, configFile)
                }
                savedForm = form
                val message = result + "，已保存"
                mutable.update { it.copy(dirty = it.form != form, modelCheck = message, modelCheckPassed = true) }
                notices.send(UiNotice(message, saved = true))
            } catch (cancelled: CancellationException) {
                if (!kotlin.coroutines.coroutineContext.isActive) throw cancelled
                mutable.update { it.copy(modelCheck = "检测已取消，原配置未更改", modelCheckPassed = null) }
            }
            catch (error: Exception) {
                val message = validationMessage(error)
                mutable.update { it.copy(modelCheck = message, modelCheckPassed = false) }
                notices.send(UiNotice(message))
            } finally {
                if (validationCall === call) validationCall = null
                mutable.update { it.copy(saving = false) }
            }
        }
    }
    private fun validationMessage(error: Exception) = when (error) {
        is PrivateSettings.SaveNotConfirmed -> error.message ?: "未能确认配置保存，请重试"
        is ApiConfig.Invalid, is SearchConfig.Invalid, is LlmCall.Failure -> (error.message ?: "检测失败") + "；原配置未更改"
        else -> "检测或保存失败，请检查配置与网络后重试；原配置未更改"
    }
    override fun onCleared() { validationCall?.cancel(); super.onCleared() }

    fun onForeground() {
        // Every foreground entry verifies the live processes, including the first entry after an upgrade.
        // Coalesce an immediate return from the root authorization dialog with the check just completed.
        if (lastControlFinished >= 0 && SystemClock.elapsedRealtime() - lastControlFinished < 2000) return
        check()
    }

    fun showGuide() { mutable.update { it.copy(showGuide = true) } }
    fun dismissGuide() {
        prefs.edit().putBoolean("guide_seen", true).apply()
        mutable.update { it.copy(showGuide = false) }
    }

    fun check() = operate(Operation.CHECK) {
        ServiceController.reconcile(backend, digest) {
            mutable.update { it.copy(operation = Operation.UPDATE) }
        }
    }

    fun toggle() {
        when (state.value.service) {
            RootServiceStarter.State.RUNNING -> operate(Operation.STOP) { ServiceController.stop(backend) }
            RootServiceStarter.State.STOPPED -> operate(Operation.START) {
                if (state.value.dirty) throw ApiConfig.Invalid("请先保存当前接口配置")
                ApiConfig.read(configFile)
                ServiceController.start(backend, digest)
            }
            RootServiceStarter.State.UNKNOWN -> check()
        }
    }

    fun restartSystemUi() {
        if (state.value.busy) return
        mutable.update { it.copy(operation = Operation.RESTART, error = null) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { RootServiceStarter.restartSystemUi(apk) }
                // One restart request followed by a bounded readiness check; never a restart watchdog.
                for (attempt in 0 until 4) {
                    delay(1500)
                    val current = withContext(Dispatchers.IO) { backend.inspect() }
                    applySnapshot(current)
                    if (state.value.hookCurrent) break
                }
                notices.send(UiNotice(if (state.value.hookCurrent) "系统界面已加载当前版本模块" else "已发送重启请求；若仍未加载，请确认 LSPosed 作用域后重新检查"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { recover(error) }
            finally { lastControlFinished = SystemClock.elapsedRealtime(); mutable.update { it.copy(operation = Operation.NONE) } }
        }
    }

    private fun operate(operation: Operation, work: () -> RootServiceStarter.Snapshot) {
        if (state.value.busy) return
        // Keep the last confirmed state and button label while checking in the background.
        mutable.update { it.copy(operation = operation, error = null) }
        viewModelScope.launch {
            try {
                val actual = withContext(Dispatchers.IO) { work() }
                applySnapshot(actual)
                if (state.value.operation == Operation.UPDATE) notices.send(UiNotice("Root 服务已更新到当前版本"))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { recover(error) }
            finally { lastControlFinished = SystemClock.elapsedRealtime(); mutable.update { it.copy(operation = Operation.NONE) } }
        }
    }

    private suspend fun applySnapshot(snapshot: RootServiceStarter.Snapshot) {
        val expected = withContext(Dispatchers.IO) { digest }
        mutable.update {
            it.copy(snapshot = snapshot, root = RootAccess.GRANTED,
                serviceCurrent = snapshot.serviceCurrent(expected), hookCurrent = snapshot.hookCurrent(expected),
                checkedAt = System.currentTimeMillis())
        }
    }

    private suspend fun recover(error: Exception) {
        // A denied read must not immediately ask for root a second time. Re-read only after a mutation.
        val mutated = state.value.operation in setOf(Operation.START, Operation.STOP, Operation.UPDATE, Operation.RESTART, Operation.ADJUST)
        if (error is ApiConfig.Invalid) {
            val message = error.message ?: "请先保存接口配置"
            mutable.update { it.copy(error = message) }
            notices.send(UiNotice(message))
            return
        }
        val actual = if (mutated) withContext(Dispatchers.IO) { try { backend.inspect() } catch (_: Exception) { null } } else null
        if (actual != null) applySnapshot(actual)
        else mutable.update { it.copy(snapshot = null, root = RootAccess.UNAVAILABLE, hookCurrent = false, serviceCurrent = false) }
        val message = when (error) {
            is ApiConfig.Invalid, is IOException -> error.message ?: "操作失败，请重试"
            else -> "自检失败，请确认 Root 授权后重新检查"
        }
        mutable.update { it.copy(error = message) }
        notices.send(UiNotice(message))
    }
}
