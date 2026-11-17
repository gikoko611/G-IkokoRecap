package com.gikoko.giai

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object AiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    data class Msg(val role: String, val text: String, val imgB64: String? = null)

    fun chat(ctx: Context, msgs: List<Msg>): String {
        val arr = JSONArray()
        for (m in msgs) {
            val mo = JSONObject()
            mo.put("role", m.role)
            if (m.imgB64 != null && m.role == "user") {
                val parts = JSONArray()
                parts.put(JSONObject().put("type", "text").put("text", m.text))
                parts.put(
                    JSONObject().put("type", "image_url")
                        .put("image_url", JSONObject().put("url", "data:image/jpeg;base64," + m.imgB64))
                )
                mo.put("content", parts)
            } else {
                mo.put("content", m.text)
            }
            arr.put(mo)
        }
        val body = JSONObject().put("model", Prefs.aiModel).put("messages", arr)
        val req = Request.Builder()
            .url(Prefs.aiBase.trimEnd('/') + "/chat/completions")
            .addHeader("Authorization", "Bearer " + Prefs.aiKey)
            .addHeader("HTTP-Referer", "https://github.com/gikoko611")
            .addHeader("X-Title", "G.I AI")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string() ?: ""
            if (!r.isSuccessful) throw Exception("HTTP " + r.code + ": " + t.take(300))
            val content = JSONObject(t).optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content") ?: ""
            if (content.isEmpty()) throw Exception("Empty reply: " + t.take(300))
            return content
        }
    }

    fun models(ctx: Context, freeOnly: Boolean): List<String> {
        val req = Request.Builder()
            .url(Prefs.aiBase.trimEnd('/') + "/models")
            .addHeader("Authorization", "Bearer " + Prefs.aiKey)
            .build()
        http.newCall(req).execute().use { r ->
            val t = r.body?.string() ?: ""
            if (!r.isSuccessful) throw Exception("HTTP " + r.code)
            val data = JSONObject(t).optJSONArray("data") ?: JSONArray()
            val out = ArrayList<String>()
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id") ?: continue
                if (freeOnly && !id.contains(":free") && !id.contains("free")) continue
                out.add(id)
            }
            return out.sorted()
        }
    }
}
