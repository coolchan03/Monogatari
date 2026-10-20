package org.koitharu.kotatsu.favourites.smart

import java.util.Locale

/**
 * Draft (v1.5) smart collections: a saved rule such as `media:manga tag:romance -tag:horror status:ongoing unread`
 * that selects library titles automatically. Terms are ANDed; a leading `-` negates one; free words match the title.
 */
data class SmartCollectionRule(val terms: List<Term>) {

	data class Term(val field: Field, val value: String, val negated: Boolean)

	enum class Field { TITLE, TAG, MEDIA, SOURCE, STATUS, UNREAD, FAVOURITE }

	/** What a rule can see about a library title. */
	data class Item(
		val title: String,
		val tags: Collection<String>,
		val mediaType: String,
		val source: String,
		val status: String?,
		val unreadChapters: Int,
		val favourite: Boolean,
	)

	fun matches(item: Item): Boolean = terms.all { term -> termMatches(term, item) != term.negated }

	private fun termMatches(term: Term, item: Item): Boolean = when (term.field) {
		Field.TITLE -> item.title.contains(term.value, ignoreCase = true)
		Field.TAG -> item.tags.any { it.equals(term.value, ignoreCase = true) }
		Field.MEDIA -> item.mediaType.equals(term.value, ignoreCase = true)
		Field.SOURCE -> item.source.contains(term.value, ignoreCase = true)
		Field.STATUS -> item.status?.equals(term.value, ignoreCase = true) == true
		Field.UNREAD -> item.unreadChapters > 0
		Field.FAVOURITE -> item.favourite
	}

	/** Canonical text form, so a rule can be stored as a single preference string. */
	fun encode(): String = terms.joinToString(" ") { term ->
		val prefix = if (term.negated) "-" else ""
		when (term.field) {
			Field.UNREAD, Field.FAVOURITE -> prefix + term.field.name.lowercase(Locale.ROOT)
			Field.TITLE -> prefix + quote(term.value)
			else -> prefix + term.field.name.lowercase(Locale.ROOT) + ":" + quote(term.value)
		}
	}

	companion object {

		private val TOKEN = Regex("""(-?)(?:(\w+):)?(?:"([^"]*)"|(\S+))""")

		fun parse(text: String): SmartCollectionRule {
			val terms = ArrayList<Term>()
			for (match in TOKEN.findAll(text.trim())) {
				val negated = match.groupValues[1] == "-"
				val key = match.groupValues[2].lowercase(Locale.ROOT)
				val value = match.groupValues[3].ifEmpty { match.groupValues[4] }.trim()
				val field = when {
					key.isEmpty() && value.equals("unread", true) -> Field.UNREAD
					key.isEmpty() && value.equals("favourite", true) -> Field.FAVOURITE
					key.isEmpty() -> Field.TITLE
					else -> Field.entries.firstOrNull { it.name.equals(key, true) } ?: Field.TITLE
				}
				if (value.isEmpty() && field != Field.UNREAD && field != Field.FAVOURITE) continue
				terms += Term(field, if (field == Field.TITLE && key.isNotEmpty() && Field.entries.none { it.name.equals(key, true) }) "$key:$value" else value, negated)
			}
			return SmartCollectionRule(terms)
		}

		private fun quote(value: String) = if (value.any { it.isWhitespace() }) "\"$value\"" else value
	}
}
