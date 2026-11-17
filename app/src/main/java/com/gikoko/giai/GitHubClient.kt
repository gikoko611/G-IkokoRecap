package com.gikoko.giai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object GitHubClient {
    private val http = OkHttpClient()
    private const val API = "https://api.github.com"

    private fun req(url: String, method: String = "GET", body: String? = null): Request {
        val b = Request.Builder().url(url)
            .addHeader("Authorization", "Bearer " + Prefs.ghToken)
            .addHeader("Accept", "application/vnd.github+json")
            .addHeader("User-Agent", "GIAI")
        when (method) {
            "GET" -> b.get()
            "DELETE" -> b.delete()
            else -> b.put((body ?: "{}").toRequestBody("application/json".toMediaType()))
        }
        return b.build()
    }

    private fun exec(r: Request): String {
        http.newCall(r).execute().use { resp ->
            val t = resp.body?.string() ?: ""
            if (!resp.isSuccessful) throw Exception("GitHub HTTP " + resp.code + ": " + t.take(300))
            return t
        }
    }

    fun listRepos(): List<String> {
        val arr = JSONArray(exec(req(API + "/user/repos?per_page=100&sort=updated")))
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.optJSONObject(i)?.optString("full_name") ?: continue)
        return out
    }

    fun getFile(ownerRepo: String, path: String, ref: String = "main"): String {
        val t = exec(req(API + "/repos/" + ownerRepo + "/contents/" + path + "?ref=" + ref))
        return JSONObject(t).optString("content")
            .replace("\n", "").let { android.util.Base64.decode(it, android.util.Base64.DEFAULT) }
            .toString(Charsets.UTF_8)
    }

    fun putFile(ownerRepo: String, path: String, content: String, message: String, branch: String = "main"): String {
        var sha: String? = null
        try {
            val t = exec(req(API + "/repos/" + ownerRepo + "/contents/" + path + "?ref=" + branch))
            sha = JSONObject(t).optString("sha")
        } catch (_: Exception) { }
        val body = JSONObject()
            .put("message", message)
            .put("content", android.util.Base64.encodeToString(content.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP))
            .put("branch", branch)
        if (sha != null) body.put("sha", sha)
        return exec(req(API + "/repos/" + ownerRepo + "/contents/" + path, "PUT", body.toString()))
    }

    fun deleteFile(ownerRepo: String, path: String, branch: String = "main") {
        val t = exec(req(API + "/repos/" + ownerRepo + "/contents/" + path + "?ref=" + branch))
        val sha = JSONObject(t).optString("sha")
        val body = JSONObject().put("message", "delete " + path).put("sha", sha).put("branch", branch)
        exec(req(API + "/repos/" + ownerRepo + "/contents/" + path, "DELETE", body.toString()))
    }

    fun listWorkflows(ownerRepo: String): List<String> {
        val t = exec(req(API + "/repos/" + ownerRepo + "/actions/workflows"))
        val arr = JSONObject(t).optJSONArray("workflows") ?: JSONArray()
        val out = ArrayList<String>()
        for (i in 0 until arr.length()) out.add(arr.optJSONObject(i)?.optString("path") ?: continue)
        return out
    }

    fun runWorkflow(ownerRepo: String, workflowPath: String, ref: String = "main") {
        exec(req(API + "/repos/" + ownerRepo + "/actions/workflows/" + workflowPath + "/dispatches", "PUT",
            JSONObject().put("ref", ref).toString()))
    }

    fun listRuns(ownerRepo: String, perPage: Int = 10): String {
        return exec(req(API + "/repos/" + ownerRepo + "/actions/runs?per_page=" + perPage))
    }
}
