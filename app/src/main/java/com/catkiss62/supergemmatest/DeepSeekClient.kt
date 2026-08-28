package com.catkiss62.supergemmatest

import java.io.IOException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class DeepSeekClient(
    private val client: OkHttpClient = OkHttpClient(),
) {
    fun send(
        endpointInput: String,
        apiKey: String,
        model: String,
        messages: List<ChatMessage>,
    ): String {
        require(apiKey.isNotBlank()) { "请先在设置页填写 DeepSeek API Key。" }
        require(model.isNotBlank()) { "模型名称不能为空。" }

        val payload = JSONObject().apply {
            put("model", model.trim())
            put("stream", false)
            put("messages", JSONArray().apply {
                messages
                    .filter { it.role == ChatMessage.Role.USER || it.role == ChatMessage.Role.ASSISTANT }
                    .forEach { item ->
                        put(JSONObject().apply {
                            put("role", if (item.role == ChatMessage.Role.USER) "user" else "assistant")
                            put("content", item.text)
                        })
                    }
            })
        }

        val request = Request.Builder()
            .url(normalizeEndpoint(endpointInput))
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(body).optJSONObject("error")?.optString("message")
                }.getOrNull().orEmpty()
                throw IOException("DeepSeek 请求失败 ${response.code}${if (detail.isNotBlank()) "：$detail" else ""}")
            }
            val root = JSONObject(body)
            val content = root
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) throw IOException("DeepSeek 返回成功，但正文为空。")
            return content
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        fun normalizeEndpoint(value: String): String {
            val trimmed = value.trim().trimEnd('/')
            require(trimmed.startsWith("https://")) { "API 地址必须以 https:// 开头。" }
            return when {
                trimmed.endsWith("/chat/completions") -> trimmed
                trimmed.endsWith("/v1") -> "$trimmed/chat/completions"
                else -> "$trimmed/chat/completions"
            }
        }
    }
}
