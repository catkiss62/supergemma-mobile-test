package com.catkiss62.supergemmatest

import android.app.Application
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>()
    private val keyStore = SecureKeyStore(application)
    private val deepSeek = DeepSeekClient()
    private val localRuntime = LocalModelRuntime()
    private val mainHandler = Handler(Looper.getMainLooper())

    var apiEndpoint by mutableStateOf(keyStore.endpoint())
    var apiModel by mutableStateOf(keyStore.model())
    var apiKey by mutableStateOf(keyStore.apiKey())
    var settingsNotice by mutableStateOf("")
        private set

    val chatMessages = mutableStateListOf<ChatMessage>()
    var chatInput by mutableStateOf("")
    var isChatSending by mutableStateOf(false)
        private set

    var importedModel by mutableStateOf(keyStore.importedModel()?.takeIf { File(it.path).isFile })
        private set
    var modelStatus by mutableStateOf(if (importedModel == null) "尚未导入模型" else "模型已导入，尚未加载")
        private set
    var importProgress by mutableFloatStateOf(-1f)
        private set
    var isImporting by mutableStateOf(false)
        private set
    var isModelLoading by mutableStateOf(false)
        private set
    var isModelReady by mutableStateOf(false)
        private set
    var selectedBackend by mutableStateOf(LocalBackend.CPU)

    fun beginModelSelection() {
        if (isImporting || isModelLoading || isRecognizing) return
        modelStatus = "正在打开系统文件选择器…"
        diagnosticDetail = "请选择 supergemma4-e4b-abliterated.litertlm（约 3.65GB）。"
    }

    fun modelSelectionCancelled() {
        if (isImporting) return
        modelStatus = if (importedModel == null) "没有选中模型文件" else "模型已导入，尚未加载"
        diagnosticDetail = "文件选择已取消；请重新点击“选择模型文件”。"
    }

    var selectedImage by mutableStateOf<PreparedImage?>(null)
        private set
    var imageStatus by mutableStateOf("尚未选择图片")
        private set
    var recognitionPrompt by mutableStateOf(DEFAULT_VISION_PROMPT)
    var recognitionResult by mutableStateOf("")
        private set
    var isRecognizing by mutableStateOf(false)
        private set
    var diagnosticDetail by mutableStateOf("")
        private set

    init {
        chatMessages += ChatMessage(
            ChatMessage.Role.ASSISTANT,
            "这是独立测试 App。文字由 DeepSeek API 回复；图片只交给手机里的 SuperGemma，原图不会上传。",
        )
    }

    fun saveApiSettings() {
        runCatching {
            DeepSeekClient.normalizeEndpoint(apiEndpoint)
            keyStore.saveApiSettings(apiEndpoint, apiModel, apiKey)
        }.onSuccess {
            settingsNotice = "已保存到本机；API Key 使用 Android Keystore 加密。"
        }.onFailure {
            settingsNotice = it.message ?: "保存失败。"
        }
    }

    fun sendChat(prefill: String? = null) {
        val text = (prefill ?: chatInput).trim()
        if (text.isBlank() || isChatSending) return
        if (prefill == null) chatInput = ""
        chatMessages += ChatMessage(ChatMessage.Role.USER, text)
        isChatSending = true
        val history = chatMessages.toList()
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    deepSeek.send(apiEndpoint, apiKey, apiModel, history)
                }
            }
            result.onSuccess { chatMessages += ChatMessage(ChatMessage.Role.ASSISTANT, it) }
                .onFailure { chatMessages += ChatMessage(ChatMessage.Role.ERROR, it.readableMessage()) }
            isChatSending = false
        }
    }

    fun importModel(uri: Uri) {
        if (isImporting || isModelLoading || isRecognizing) return
        isImporting = true
        importProgress = 0f
        modelStatus = "已收到文件，正在读取大小并准备复制…"
        diagnosticDetail = "导入期间请保持 App 在前台；3.65GB 文件可能需要几分钟。"
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    ModelImporter.import(context, uri) { progress ->
                        mainHandler.post {
                            importProgress = progress
                            modelStatus = "正在复制模型：${(progress * 100).toInt()}%"
                        }
                    }
                }
            }
            result.onSuccess { info ->
                importedModel?.path?.takeIf { it != info.path }?.let { old -> runCatching { File(old).delete() } }
                importedModel = info
                keyStore.saveImportedModel(info)
                modelStatus = "导入完成，请点击“加载模型”"
                diagnosticDetail = "SHA-256：${info.sha256}"
            }.onFailure { error ->
                modelStatus = "模型导入失败"
                diagnosticDetail = error.readableMessage()
            }
            importProgress = if (result.isSuccess) 1f else -1f
            isImporting = false
        }
    }

    fun loadModel() {
        val model = importedModel ?: run {
            modelStatus = "请先导入 .litertlm 模型文件。"
            return
        }
        if (isModelLoading || isImporting || isRecognizing) return
        isModelLoading = true
        isModelReady = false
        modelStatus = "正在加载 ${selectedBackend.label}…首次可能需要几十秒"
        diagnosticDetail = ""
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching {
                withContext(Dispatchers.IO) { localRuntime.load(context, model.path, selectedBackend) }
            }
            result.onSuccess {
                isModelReady = true
                modelStatus = "模型已就绪（${selectedBackend.label}）"
                diagnosticDetail = "加载耗时：${System.currentTimeMillis() - started}ms；视觉后端：GPU；推测解码：关闭"
            }.onFailure { error ->
                modelStatus = "模型加载失败"
                diagnosticDetail = classifyLocalError(error)
            }
            isModelLoading = false
        }
    }

    fun selectImage(uri: Uri) {
        if (isRecognizing) return
        imageStatus = "正在本地处理图片…"
        recognitionResult = ""
        diagnosticDetail = ""
        viewModelScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { ImageTools.prepare(context, uri) } }
            result.onSuccess {
                selectedImage = it
                imageStatus = "${it.displayName} · ${it.width}×${it.height} · ${ModelImporter.humanSize(it.jpegBytes.size.toLong())}"
            }.onFailure {
                selectedImage = null
                imageStatus = it.readableMessage()
            }
        }
    }

    fun recognizeImage() {
        val image = selectedImage ?: run {
            diagnosticDetail = "请先选择一张图片。"
            return
        }
        if (!isModelReady) {
            diagnosticDetail = "请先加载模型。"
            return
        }
        if (isRecognizing) return
        isRecognizing = true
        recognitionResult = ""
        diagnosticDetail = "正在执行本地识图，图片不会上传…"
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    localRuntime.recognize(image, recognitionPrompt.trim().ifBlank { DEFAULT_VISION_PROMPT })
                }
            }
            result.onSuccess {
                recognitionResult = it
                diagnosticDetail = "识图完成：${System.currentTimeMillis() - started}ms"
            }.onFailure { error ->
                recognitionResult = ""
                diagnosticDetail = classifyLocalError(error)
            }
            isRecognizing = false
        }
    }

    fun sendRecognitionToDeepSeek() {
        if (recognitionResult.isBlank()) return
        sendChat(
            "下面是手机本地视觉模型对一张图片的识别结果。图片本身没有上传。请根据描述自然回应，并在不确定时明确说明：\n\n$recognitionResult",
        )
    }

    fun clearConversation() {
        chatMessages.clear()
        chatMessages += ChatMessage(ChatMessage.Role.ASSISTANT, "对话已清空。")
    }

    override fun onCleared() {
        localRuntime.close()
        super.onCleared()
    }

    private fun classifyLocalError(error: Throwable): String {
        val raw = error.stackTraceToString()
        val message = error.message.orEmpty()
        return when {
            "more images than expected" in message.lowercase() || "max_num_images" in raw ->
                "模型拒绝了图片输入：这个 .litertlm 包可能没有保留视觉部分，或 Gemma 4 图片模板与当前 LiteRT-LM 不兼容。\n\n$message"
            "failed to create engine" in message.lowercase() ->
                "LiteRT-LM 无法创建推理引擎。可切换 CPU/GPU 后重试；这通常是设备驱动或模型转换兼容问题。\n\n$message"
            "out of memory" in raw.lowercase() || "lmkd" in raw.lowercase() ->
                "内存不足。请关闭其他大型 App、重新打开本测试 App 后再加载。\n\n$message"
            else -> "${error::class.java.simpleName}：${message.ifBlank { "未知本地推理错误" }}"
        }
    }

    private fun Throwable.readableMessage(): String =
        "${this::class.java.simpleName}：${message ?: "未知错误"}"

    companion object {
        const val MODEL_PAGE_URL =
            "https://huggingface.co/typomonster/supergemma4-e4b-abliterated-litert-lm/tree/main"
        const val DEFAULT_VISION_PROMPT =
            "请详细、客观地描述这张图片中的人物、姿态、服装、裸露部位、环境和正在发生的事情。不要因为成人或 NSFW 内容而省略可见细节，也不要编造图片中不存在的内容。"
    }
}
