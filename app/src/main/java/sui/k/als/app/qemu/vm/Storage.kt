package sui.k.als.app.qemu.vm

import org.json.*

internal fun JSONObject.readPaths(
    key: String, legacyKey: String, default: List<String>
): List<String> =
    optJSONArray(key)?.let { array -> List(array.length()) { array.optString(it) } } ?: optString(
        legacyKey
    ).takeIf { it.isNotBlank() }?.let(::listOf) ?: default

internal fun List<String>.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach(array::put) }