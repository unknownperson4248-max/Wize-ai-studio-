package app.nova.assistant

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.TimeZone

/** Talks to the NOVA backend (Cloudflare Worker). All calls block, so run them off the main thread. */
class Api(private val prefs: Prefs) {

    class ApiError(message: String) : Exception(message)

    data class Action(val id: String, val name: String, val args: JSONObject, val confirm: Boolean)
    data class Reply(val text: String, val actions: List<Action>)

    private fun request(method: String, path: String, body: JSONObject? = null, auth: Boolean = true): JSONObject {
        val conn = URL(prefs.server.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("content-type", "application/json")
            if (auth) conn.setRequestProperty("authorization", "Bearer " + prefs.token)
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
            if (code !in 200..299) throw ApiError(json.optString("error", "Server error ($code)"))
            return json
        } catch (e: ApiError) {
            throw e
        } catch (e: Exception) {
            throw ApiError("No connection to the server. Check the internet.")
        } finally {
            conn.disconnect()
        }
    }

    fun pair(code: String, name: String): String {
        val r = request("POST", "/api/pair", JSONObject().put("code", code).put("name", name), auth = false)
        return r.getString("token")
    }

    private fun toReply(r: JSONObject): Reply {
        val list = mutableListOf<Action>()
        val arr = r.optJSONArray("actions") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val a = arr.getJSONObject(i)
            list.add(Action(a.optString("id"), a.getString("name"), a.optJSONObject("args") ?: JSONObject(), a.optBoolean("confirm")))
        }
        return Reply(r.optString("reply", ""), list)
    }

    fun say(text: String): Reply =
        toReply(request("POST", "/api/chat", JSONObject().put("text", text).put("tz", TimeZone.getDefault().id)))

    fun sendResult(action: Action, result: String): Reply {
        val item = JSONObject().put("name", action.name).put("args", action.args).put("result", result)
        return toReply(request("POST", "/api/chat", JSONObject().put("tool_results", JSONArray().put(item)).put("tz", TimeZone.getDefault().id)))
    }

    /** Recent conversation as (role, text) pairs, oldest first. */
    fun history(): List<Pair<String, String>> {
        val arr = request("GET", "/api/history").optJSONArray("messages") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it).let { m -> m.getString("role") to m.getString("content") } }
    }

    fun clearHistory() { request("DELETE", "/api/history") }

    fun memories(): List<Pair<Int, String>> {
        val arr = request("GET", "/api/memories").optJSONArray("memories") ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it).let { m -> m.getInt("id") to m.getString("text") } }
    }

    fun deleteMemory(id: Int) { request("DELETE", "/api/memories/$id") }
}
