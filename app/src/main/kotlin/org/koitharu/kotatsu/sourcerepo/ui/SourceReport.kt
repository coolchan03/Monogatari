package org.koitharu.kotatsu.sourcerepo.ui

/**
 * Builds the copyable source-health report. Failures are grouped by who most likely has to act
 * (website, user, or Monogatari/extension code) so dead domains are not mistaken for app defects.
 */
internal fun formatSourceReport(title: String, lines: Collection<String>): String {
	val counts = lines.groupingBy { line ->
		line.substringBefore(" |").takeIf { it in setOf("PASS", "PARTIAL", "FAIL") } ?: "OTHER"
	}.eachCount()
	val failures = lines.filter { it.startsWith("FAIL |") }
	val categories = failures.groupingBy { categoryOf(it) }.eachCount()
	val emptyPartials = lines.count { it.startsWith("PARTIAL |") && categoryOf(it).startsWith("EMPTY_") }
	return buildString {
		appendLine(title)
		appendLine("Saved checks=${lines.size} PASS=${counts["PASS"] ?: 0} PARTIAL=${counts["PARTIAL"] ?: 0} FAIL=${counts["FAIL"] ?: 0}")
		appendLine("Incomplete checks are not marked failed. Partial scans can be resumed.")
		if (categories.isNotEmpty()) {
			appendLine(
				"Failure categories: " + categories.entries.sortedByDescending { it.value }
					.joinToString { "${it.key}=${it.value}" },
			)
			appendLine()
			appendLine("Likely causes:")
			categories.entries.groupBy({ sourceFailureCause(it.key) }, { it })
				.entries.sortedByDescending { group -> group.value.sumOf { it.value } }
				.forEach { (cause, members) ->
					val total = members.sumOf { it.value }
					appendLine("- ${cause.title}: $total (${members.joinToString { "${it.key}=${it.value}" }})")
					appendLine("  ${cause.explanation}")
				}
			val defects = failures.count { sourceFailureCause(categoryOf(it)) == SourceFailureCause.CODE_DEFECT }
			appendLine(
				"Only the $defects \"${SourceFailureCause.CODE_DEFECT.title}\" failures point at fixable code; " +
					"the rest depend on the website, the network or the user's access.",
			)
		}
		if (emptyPartials > 0) {
			appendLine(
				"$emptyPartials sources answered with no titles. A scan never disables a source: global search only hides " +
					"sections with no matches for that query (Hide empty) and skips sources with repeated real failures and no recent success.",
			)
		}
		appendLine()
		lines.forEach { appendLine(it) }
		appendLine("Website listing tests do not prove reading or video playback.")
	}
}

private fun categoryOf(line: String): String = CATEGORY_REGEX.find(line)?.groupValues?.get(1) ?: "OTHER"
