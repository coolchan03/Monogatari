package org.koitharu.kotatsu.jsext

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Mangayomi's `parseDates(values, dateFormat, locale)`: turns chapter date strings into epoch-millisecond strings.
 * Handles epoch numbers, ISO-8601, "today/yesterday", "3 days ago" style phrases (several languages), an explicit
 * pattern and a set of common patterns. Unparseable values fall back to the time of the call, like upstream.
 */
internal class JsDateParser(
	private val now: () -> Long = System::currentTimeMillis,
	private val zone: TimeZone = TimeZone.getDefault(),
) {

	fun parseAll(values: List<String>, dateFormat: String, locale: String): List<String> {
		val fallback = now()
		var format = dateFormat
		var formatLocale = locale
		return values.map { it.trim() }.filter { it.isNotEmpty() }.map { value ->
			val parsed = parse(value, format, formatLocale) { learnedFormat, learnedLocale ->
				format = learnedFormat
				formatLocale = learnedLocale
			}
			(parsed ?: fallback).toString()
		}
	}

	/** @return epoch millis, or null if nothing matched. */
	fun parse(raw: String, dateFormat: String, locale: String, onLearned: (String, String) -> Unit = { _, _ -> }): Long? {
		val value = raw.trim()
		if (value.isEmpty()) return null
		val lower = value.lowercase(Locale.ROOT)

		if (value.all { it.isDigit() }) {
			when (value.length) {
				10 -> return value.toLong() * 1000
				in 12..14 -> return value.toLong()
			}
		}
		if ('T' in value) parseIso(value)?.let { return it }

		when {
			TODAY.any { lower.startsWith(it) } -> return startOfDay(0)
			YESTERDAY.any { lower.startsWith(it) } -> return startOfDay(1)
			TWO_DAYS_AGO.any { lower.startsWith(it) } -> return startOfDay(2)
		}
		if (AGO_SUFFIXES.any { lower.contains(it) } || AGO_PREFIXES.any { lower.startsWith(it) }) {
			parseRelative(lower)?.let { return it }
		}

		val cleaned = value.replace(ORDINAL, "$1")
		if (dateFormat.isNotBlank()) {
			parsePattern(cleaned, dateFormat, localeOf(locale))?.let { return it }
		}
		val hasLetters = cleaned.any { it.isLetter() }
		val candidates = if (hasLetters) TEXTUAL_FORMATS else NUMERIC_FORMATS
		val locales = buildList {
			if (locale.isNotBlank()) add(localeOf(locale))
			add(Locale.ENGLISH)
			if (hasLetters) addAll(COMMON_LOCALES)
		}.distinct()
		for (loc in locales) {
			for (pattern in candidates) {
				parsePattern(cleaned, pattern, loc)?.let {
					onLearned(pattern, loc.toLanguageTag())
					return it
				}
			}
		}
		parseRelative(lower)?.let { return it }
		return parseIso(value)
	}

	private fun parsePattern(value: String, pattern: String, locale: Locale): Long? = try {
		val format = SimpleDateFormat(pattern, locale).apply {
			isLenient = false
			timeZone = zone
		}
		val position = ParsePosition(0)
		val date = format.parse(value, position)
		// Require the whole string (ignoring trailing spaces/punctuation) to be consumed.
		if (date != null && value.substring(position.index).isBlank()) date.time else null
	} catch (e: IllegalArgumentException) {
		null
	}

	private fun parseIso(value: String): Long? {
		runCatching { return OffsetDateTime.parse(value).toInstant().toEpochMilli() }
		runCatching { return Instant.parse(value).toEpochMilli() }
		runCatching { return LocalDateTime.parse(value).atZone(ZoneId.of(zone.id)).toInstant().toEpochMilli() }
		runCatching { return LocalDate.parse(value).atStartOfDay(ZoneId.of(zone.id)).toInstant().toEpochMilli() }
		return null
	}

	private fun parseRelative(lower: String): Long? {
		val amount = NUMBER.find(lower)?.value?.toLongOrNull() ?: return null
		val unitMillis = UNITS.firstOrNull { (words, _) ->
			words.any { word -> if (word.length <= 2) shortToken(word).containsMatchIn(lower) else lower.contains(word) }
		}?.second ?: return null
		val result = now() - amount * unitMillis
		return result.takeIf { it > 0 }
	}

	private fun startOfDay(daysAgo: Int): Long = Calendar.getInstance(zone).apply {
		timeInMillis = now()
		add(Calendar.DAY_OF_MONTH, -daysAgo)
		set(Calendar.HOUR_OF_DAY, 0)
		set(Calendar.MINUTE, 0)
		set(Calendar.SECOND, 0)
		set(Calendar.MILLISECOND, 0)
	}.timeInMillis

	private fun localeOf(tag: String): Locale =
		if (tag.isBlank()) Locale.ENGLISH else Locale.forLanguageTag(tag.replace('_', '-'))

	private companion object {

		const val MINUTE = 60_000L
		const val HOUR = 60 * MINUTE
		const val DAY = 24 * HOUR

		val NUMBER = Regex("""\d+""")
		val ORDINAL = Regex("""(\d+)(st|nd|rd|th)\b""", RegexOption.IGNORE_CASE)

		/** One- and two-letter units ("3d", "5 h") only count as standalone tokens next to a number. */
		fun shortToken(token: String) = Regex("""\d\s*${Regex.escape(token)}\b""")

		val TODAY = listOf("today", "hoy", "aujourd'hui", "oggi", "heute", "bugün", "hari ini", "اليوم", "今天", "hôm nay", "just now")
		val YESTERDAY = listOf("yesterday", "ayer", "hier", "ieri", "gestern", "dün", "kemarin", "أمس", "昨天", "hôm qua")
		val TWO_DAYS_AGO = listOf("يومين", "anteayer", "avant-hier", "l'altro ieri", "vorgestern", "前天", "hôm kia")
		val AGO_SUFFIXES = listOf("ago", "atrás", "önce", "قبل", " fa", "vor", "trước", "前", "lalu", "назад")
		val AGO_PREFIXES = listOf("hace", "il y a", "vor", "há")

		// Longer words first within each unit; units ordered so e.g. "minute" is not taken for "m"onth.
		val UNITS: List<Pair<List<String>, Long>> = listOf(
			listOf("second", "segundo", "seconde", "sekunde", "detik", "saniye", "secondi", "giây", "วินาที", "秒", "sec", "s") to 1_000L,
			listOf("minute", "minuto", "menit", "dakika", "minuti", "phút", "นาที", "دقيقة", "دقائق", "分钟", "min", "m") to MINUTE,
			listOf("hour", "hora", "heure", "stunde", "jam", "saat", "ore", "giờ", "ชั่วโมง", "ساعة", "小时", "hr", "h") to HOUR,
			listOf("day", "día", "dia", "jour", "tag", "hari", "gün", "giorn", "ngày", "วัน", "يوم", "أيام", "天", "d") to DAY,
			listOf("week", "semana", "semaine", "woche", "settimana", "minggu", "hafta", "tuần", "สัปดาห์", "周", "w") to 7 * DAY,
			listOf("month", "mes", "mois", "monat", "mese", "bulan", "ay ", "tháng", "เดือน", "月", "mo") to 30 * DAY,
			listOf("year", "año", "ans", "ano", "jahr", "anno", "tahun", "yıl", "năm", "ปี", "年", "y") to 365 * DAY,
		)

		val NUMERIC_FORMATS = listOf(
			"yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd",
			"dd/MM/yyyy", "MM/dd/yyyy", "dd-MM-yyyy", "MM-dd-yyyy", "dd.MM.yyyy",
			"dd/MM/yy", "MM/dd/yy", "dd.MM.yy", "yyyyMMdd",
		)
		val TEXTUAL_FORMATS = listOf(
			"MMMM d, yyyy", "MMMM dd, yyyy", "MMM d, yyyy", "MMM dd, yyyy", "MMM. d, yyyy",
			"d MMMM yyyy", "dd MMMM yyyy", "d MMM yyyy", "dd MMM yyyy", "MMMM d yyyy", "MMM d yyyy",
			"EEEE, MMMM d, yyyy", "EEE, d MMM yyyy", "dd MMM, yyyy", "d MMM, yyyy", "MMMM yyyy",
		)
		val COMMON_LOCALES = listOf("es", "fr", "pt", "de", "it", "id", "tr", "ru", "vi").map { Locale.forLanguageTag(it) }
	}
}
