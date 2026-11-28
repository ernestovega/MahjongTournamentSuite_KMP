package org.json

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray as KJsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject as KJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/*
 * The small part of the `org.json` API of Android that the end-to-end tests use, in common Kotlin, so the same tests
 * compile for the iOS simulator. Values are kept as Kotlin values: String, Int, Long, Double, Boolean, JSONObject,
 * JSONArray or null.
 */

/** The value of a JSON null, like `JSONObject.NULL`. */
object JSONNull {
    override fun toString() = "null"
}

class JSONException(message: String) : RuntimeException(message)

class JSONObject private constructor(private val values: LinkedHashMap<String, Any?>) {
    constructor() : this(LinkedHashMap())

    constructor(text: String) : this(fromElement(Json.parseToJsonElement(text)) as? JSONObject ?: throw JSONException("Not a JSON object: ${text.take(80)}"))

    private constructor(other: JSONObject) : this(other.values)

    fun put(key: String, value: Any?): JSONObject = apply { values[key] = value }

    fun has(key: String): Boolean = values.containsKey(key)

    fun isNull(key: String): Boolean = values[key] == null || values[key] === JSONNull

    fun keys(): Iterator<String> = values.keys.iterator()

    fun length(): Int = values.size

    fun get(key: String): Any = values[key] ?: if (has(key)) JSONNull else throw JSONException("JSONObject[\"$key\"] not found.")

    fun opt(key: String): Any? = values[key]?.takeUnless { it === JSONNull }

    fun getString(key: String): String = when (val value = get(key)) {
        is String -> value
        else -> throw JSONException("JSONObject[\"$key\"] is not a string.")
    }

    fun optString(key: String, fallback: String = ""): String = opt(key)?.toString() ?: fallback

    fun getInt(key: String): Int = number(get(key), key).toInt()

    fun optInt(key: String, fallback: Int = 0): Int = opt(key)?.let { runCatching { number(it, key).toInt() }.getOrNull() } ?: fallback

    fun getLong(key: String): Long = number(get(key), key).toLong()

    fun optLong(key: String, fallback: Long = 0L): Long = opt(key)?.let { runCatching { number(it, key).toLong() }.getOrNull() } ?: fallback

    fun getDouble(key: String): Double = number(get(key), key).toDouble()

    fun getBoolean(key: String): Boolean = when (val value = get(key)) {
        is Boolean -> value
        "true" -> true
        "false" -> false
        else -> throw JSONException("JSONObject[\"$key\"] is not a Boolean.")
    }

    fun optBoolean(key: String, fallback: Boolean = false): Boolean = when (val value = opt(key)) {
        is Boolean -> value
        "true" -> true
        "false" -> false
        else -> fallback
    }

    fun getJSONObject(key: String): JSONObject = get(key) as? JSONObject ?: throw JSONException("JSONObject[\"$key\"] is not a JSONObject.")

    fun optJSONObject(key: String): JSONObject? = opt(key) as? JSONObject

    fun getJSONArray(key: String): JSONArray = get(key) as? JSONArray ?: throw JSONException("JSONObject[\"$key\"] is not a JSONArray.")

    fun optJSONArray(key: String): JSONArray? = opt(key) as? JSONArray

    override fun toString(): String = render(this)

    internal fun entries() = values.entries

    companion object {
        @Suppress("unused")
        val NULL: Any = JSONNull
    }
}

class JSONArray private constructor(private val values: MutableList<Any?>) {
    constructor() : this(mutableListOf())

    constructor(items: Collection<*>) : this(items.toMutableList())

    constructor(text: String) : this((fromElement(Json.parseToJsonElement(text)) as? JSONArray ?: throw JSONException("Not a JSON array: ${text.take(80)}")).values)

    fun length(): Int = values.size

    fun put(value: Any?): JSONArray = apply { values += value }

    fun get(index: Int): Any = values.getOrNull(index) ?: throw JSONException("JSONArray[$index] not found.")

    fun opt(index: Int): Any? = values.getOrNull(index)?.takeUnless { it === JSONNull }

    fun getString(index: Int): String = get(index) as? String ?: throw JSONException("JSONArray[$index] is not a string.")

    fun optString(index: Int, fallback: String = ""): String = opt(index)?.toString() ?: fallback

    fun getInt(index: Int): Int = number(get(index), "$index").toInt()

    fun getLong(index: Int): Long = number(get(index), "$index").toLong()

    fun getBoolean(index: Int): Boolean = get(index) as? Boolean ?: throw JSONException("JSONArray[$index] is not a Boolean.")

    fun getJSONObject(index: Int): JSONObject = get(index) as? JSONObject ?: throw JSONException("JSONArray[$index] is not a JSONObject.")

    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject

    fun getJSONArray(index: Int): JSONArray = get(index) as? JSONArray ?: throw JSONException("JSONArray[$index] is not a JSONArray.")

    override fun toString(): String = render(this)

    internal fun items(): List<Any?> = values
}

private fun number(value: Any, key: String): Number = when (value) {
    is Number -> value
    is String -> value.toLongOrNull() ?: value.toDoubleOrNull() ?: throw JSONException("JSON[\"$key\"] is not a number.")
    else -> throw JSONException("JSON[\"$key\"] is not a number.")
}

private fun fromElement(element: JsonElement): Any? = when (element) {
    is JsonNull -> JSONNull
    is KJsonObject -> JSONObject().also { target -> element.forEach { (key, value) -> target.put(key, fromElement(value)) } }
    is KJsonArray -> JSONArray(element.map { fromElement(it) })
    is JsonPrimitive -> when {
        element.isString -> element.content
        element.booleanOrNull != null -> element.booleanOrNull
        element.longOrNull != null -> element.longOrNull!!.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else it }
        else -> element.doubleOrNull
    }
}

private fun render(value: Any?): String = when (value) {
    null, JSONNull -> "null"
    is String -> JsonPrimitive(value).toString()
    is Number, is Boolean -> value.toString()
    is JSONObject -> value.entries().joinToString(",", "{", "}") { (key, item) -> JsonPrimitive(key).toString() + ":" + render(item) }
    is JSONArray -> value.items().joinToString(",", "[", "]") { render(it) }
    is Collection<*> -> value.joinToString(",", "[", "]") { render(it) }
    is Map<*, *> -> value.entries.joinToString(",", "{", "}") { (key, item) -> JsonPrimitive(key.toString()).toString() + ":" + render(item) }
    else -> JsonPrimitive(value.toString()).toString()
}
