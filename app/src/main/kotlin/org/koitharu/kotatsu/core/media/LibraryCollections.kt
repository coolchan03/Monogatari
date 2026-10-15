package org.koitharu.kotatsu.core.media

import org.json.JSONArray
import org.json.JSONObject

/**
 * The user's own collections ("folders") across every kind of media: a collection is a name and a set of item keys
 * (manga id, or a local file path). Pure and unit-tested; the screen stores it as one JSON string on the device.
 */
internal object LibraryCollections {

	const val MAX_COLLECTIONS = 50
	const val MAX_NAME = 40

	fun clean(name: String): String = name.trim().replace(Regex("\\s+"), " ").take(MAX_NAME)

	fun add(data: Map<String, Set<String>>, collection: String, key: String): Map<String, Set<String>> {
		val name = clean(collection)
		if (name.isEmpty() || key.isBlank()) return data
		if (name !in data && data.size >= MAX_COLLECTIONS) return data
		return data + (name to (data[name].orEmpty() + key))
	}

	fun remove(data: Map<String, Set<String>>, collection: String, key: String): Map<String, Set<String>> {
		val members = data[collection] ?: return data
		return data + (collection to (members - key))
	}

	fun delete(data: Map<String, Set<String>>, collection: String): Map<String, Set<String>> = data - collection

	/** Collection names that contain [key], sorted. */
	fun containing(data: Map<String, Set<String>>, key: String): List<String> =
		data.filterValues { key in it }.keys.sortedBy { it.lowercase() }

	fun names(data: Map<String, Set<String>>): List<String> = data.keys.sortedBy { it.lowercase() }

	fun encode(data: Map<String, Set<String>>): String {
		val root = JSONObject()
		for ((name, keys) in data) root.put(name, JSONArray(keys.toList()))
		return root.toString()
	}

	fun decode(text: String?): Map<String, Set<String>> {
		if (text.isNullOrBlank()) return emptyMap()
		val root = runCatching { JSONObject(text) }.getOrNull() ?: return emptyMap()
		val out = LinkedHashMap<String, Set<String>>()
		for (name in root.keys()) {
			val array = root.optJSONArray(name) ?: continue
			out[name] = (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
		}
		return out
	}
}
