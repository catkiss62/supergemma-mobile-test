package com.catkiss62.supergemmatest

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private val appViewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SuperGemmaTestApp(appViewModel) }
    }
}

private val AppColors = lightColorScheme(
    primary = Color(0xFF7355C6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE3FF),
    secondary = Color(0xFFC64F81),
    secondaryContainer = Color(0xFFFFE0EC),
    background = Color(0xFFFFF8FC),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF1EAF3),
    error = Color(0xFFB3261E),
)

@Composable
fun SuperGemmaTestApp(vm: AppViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    MaterialTheme(colorScheme = AppColors) {
        Scaffold(
            topBar = { AppHeader(tab) },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf("💬" to "DeepSeek", "🖼️" to "本地识图", "⚙️" to "设置")
                        .forEachIndexed { index, (icon, label) ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Text(icon, fontSize = 20.sp) },
                                label = { Text(label) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                ),
                            )
                        }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    0 -> ChatPage(vm)
                    1 -> VisionPage(vm, onSendToDeepSeek = {
                        vm.sendRecognitionToDeepSeek()
                        tab = 0
                    })
                    else -> SettingsPage(vm)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(tab: Int) {
    val title = when (tab) {
        0 -> "DeepSeek API 对话"
        1 -> "SuperGemma 本地识图"
        else -> "测试设置"
    }
    Surface(shadowElevation = 2.dp, color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().padding(top = 42.dp, start = 18.dp, end = 18.dp, bottom = 12.dp),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                "独立测试 APK · 模型权重不在安装包内",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF756A77),
            )
        }
    }
}

@Composable
private fun ChatPage(vm: AppViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(vm.chatMessages.size) {
        if (vm.chatMessages.isNotEmpty()) listState.animateScrollToItem(vm.chatMessages.lastIndex)
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(Color(0xFFFFF0F6)).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("模型：${vm.apiModel}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            TextButton(onClick = vm::clearConversation) { Text("清空") }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(vm.chatMessages) { message -> ChatBubble(message) }
            if (vm.isChatSending) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("DeepSeek 正在回复…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = vm.chatInput,
                onValueChange = { vm.chatInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("输入文字测试 DeepSeek API") },
                minLines = 1,
                maxLines = 5,
            )
            Button(
                onClick = { vm.sendChat() },
                enabled = vm.chatInput.isNotBlank() && !vm.isChatSending,
                modifier = Modifier.height(56.dp),
            ) { Text("发送") }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == ChatMessage.Role.USER
    val background = when (message.role) {
        ChatMessage.Role.USER -> MaterialTheme.colorScheme.primaryContainer
        ChatMessage.Role.ASSISTANT -> Color.White
        ChatMessage.Role.LOCAL_MODEL -> Color(0xFFE6F5EE)
        ChatMessage.Role.ERROR -> Color(0xFFFFE5E2)
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.87f),
            colors = CardDefaults.cardColors(containerColor = background),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(1.dp),
        ) {
            Column(Modifier.padding(12.dp)) {
                Text(
                    when (message.role) {
                        ChatMessage.Role.USER -> "你"
                        ChatMessage.Role.ASSISTANT -> "DeepSeek"
                        ChatMessage.Role.LOCAL_MODEL -> "本地 SuperGemma"
                        ChatMessage.Role.ERROR -> "错误"
                    },
                    fontWeight = FontWeight.SemiBold,
                    color = when (message.role) {
                        ChatMessage.Role.ERROR -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(5.dp))
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun VisionPage(vm: AppViewModel, onSendToDeepSeek: () -> Unit) {
    val context = LocalContext.current
    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.importModel(it)
        }
    }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(vm::selectImage)
    }
    val scrollState = rememberScrollState()

    Column(
        Modifier.fillMaxSize().verticalScroll(scrollState).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusCard(vm)
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("1. 下载与导入模型", fontWeight = FontWeight.Bold)
                Text(
                    "先把 3.65GB 的 .litertlm 文件下载到手机，再从文件选择器导入。导入成功后可以删除“下载”目录里的原文件。",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AppViewModel.MODEL_PAGE_URL)))
                        },
                        enabled = !vm.isImporting,
                    ) { Text("打开下载页") }
                    Button(
                        onClick = { modelPicker.launch(arrayOf("*/*")) },
                        enabled = !vm.isImporting && !vm.isModelLoading,
                    ) { Text("选择模型文件") }
                }
                if (vm.isImporting && vm.importProgress >= 0f) {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { vm.importProgress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("正在复制并计算 SHA-256：${(vm.importProgress * 100).toInt()}%")
                }
                vm.importedModel?.let { model ->
                    Text(
                        "${model.fileName} · ${ModelImporter.humanSize(model.sizeBytes)}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("2. 加载模型", fontWeight = FontWeight.Bold)
                LocalBackend.entries.forEach { backend ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = vm.selectedBackend == backend,
                            onClick = { vm.selectedBackend = backend },
                        ).padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = vm.selectedBackend == backend,
                            onClick = { vm.selectedBackend = backend },
                        )
                        Text(backend.label)
                    }
                }
                Button(
                    onClick = vm::loadModel,
                    enabled = vm.importedModel != null && !vm.isModelLoading && !vm.isImporting,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (vm.isModelLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (vm.isModelReady) "重新加载模型" else "加载模型")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("3. 本地识图", fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = { imagePicker.launch("image/*") },
                    enabled = !vm.isRecognizing,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("选择测试图片") }
                Text(vm.imageStatus, style = MaterialTheme.typography.bodySmall)
                vm.selectedImage?.let { image -> ImagePreview(image) }
                OutlinedTextField(
                    value = vm.recognitionPrompt,
                    onValueChange = { vm.recognitionPrompt = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("识图提示词") },
                    minLines = 4,
                    maxLines = 8,
                )
                Text(
                    "图片只在本机处理。只有你点击下方“发送给 DeepSeek”时，识图后的文字描述才会上云。",
                    color = Color(0xFF6A5962),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = vm::recognizeImage,
                    enabled = vm.isModelReady && vm.selectedImage != null && !vm.isRecognizing,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                ) {
                    if (vm.isRecognizing) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("开始本地识图")
                }
            }
        }

        if (vm.recognitionResult.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F6EE))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SuperGemma 识别结果", fontWeight = FontWeight.Bold)
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(vm.recognitionResult)
                    }
                    Button(onClick = onSendToDeepSeek, modifier = Modifier.fillMaxWidth()) {
                        Text("把识图文字发送给 DeepSeek")
                    }
                }
            }
        }

        if (vm.diagnosticDetail.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF2D9))) {
                Column(Modifier.padding(12.dp)) {
                    Text("诊断", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(vm.diagnosticDetail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun StatusCard(vm: AppViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("当前状态", fontWeight = FontWeight.Bold)
            Text(vm.modelStatus)
            Text(
                when {
                    vm.isModelReady -> "✅ 可以选择图片测试"
                    vm.importedModel != null -> "🟡 模型文件已在手机中"
                    else -> "⚪ 等待导入 .litertlm"
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ImagePreview(image: PreparedImage) {
    val bitmap = remember(image.jpegBytes) {
        BitmapFactory.decodeByteArray(image.jpegBytes, 0, image.jpegBytes.size)?.asImageBitmap()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = "待识别图片",
            modifier = Modifier.fillMaxWidth().height(260.dp),
        )
    }
}

@Composable
private fun SettingsPage(vm: AppViewModel) {
    var showKey by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("DeepSeek API", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "密钥只保存在这台手机，并使用 Android Keystore 加密；不会写进源码或上传到 GitHub。",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = vm.apiEndpoint,
                    onValueChange = { vm.apiEndpoint = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API 地址或 Base URL") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = vm.apiModel,
                    onValueChange = { vm.apiModel = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("模型名称") },
                    supportingText = { Text("官方 API 可用 deepseek-chat；你的中转也可填自定义模型名。") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = vm.apiKey,
                    onValueChange = { vm.apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API Key") },
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") }
                    },
                    singleLine = true,
                )
                Button(onClick = vm::saveApiSettings, modifier = Modifier.fillMaxWidth()) { Text("保存到本机") }
                if (vm.settingsNotice.isNotBlank()) {
                    Text(vm.settingsNotice, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("本轮测试重点", fontWeight = FontWeight.Bold)
                HorizontalDivider()
                Text("• K80 Ultra 能否稳定加载 3.65GB 模型")
                Text("• 这个社区转换包是否真正保留图片输入")
                Text("• 普通图片与 NSFW 图片的识别差异")
                Text("• CPU/GPU 主后端在天玑设备上的兼容性")
            }
        }
    }
}
