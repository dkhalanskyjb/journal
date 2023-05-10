/*
 * Copyright 2019-2022 JetBrains s.r.o. and contributors.
 * Use of this source code is governed by the Apache 2.0 License that can be found in the LICENSE.txt file.
 */

package kotlinx.datetime.internal

import kotlinx.datetime.format.migration.*
import kotlinx.datetime.format.migration.UnicodeFormat

internal data class FormatPopularity(
    val format: UnicodeFormat,
    val popularity: Int,
) {
    override fun toString(): String = "[$format]: $popularity"
}

/**
 * relies on the specific format: no " inside format strings
 * Check if your csv file fits with the shell command `grep '\(".*\)\{9\}' patterns.csv`.
 */
internal fun parsePatternsCsv(fileContents: String): List<FormatPopularity> {
    val result: MutableList<FormatPopularity> = mutableListOf()
    val stringLiteral = Regex("\"([^\"]*)\"")
    for (originalLine in fileContents.lineSequence().drop(1)) {
        if (originalLine.contains('$')) continue // skip formats with embedded expressions
        if (originalLine.contains("SomeString")) continue
        val line = originalLine.replace("\\n", "\t")
        val fields = stringLiteral.findAll(line).map { it.value.replace("\"", "") }.toList()
        val format = try {
            parseUnicodeFormat(fields[0])
        } catch (e: Throwable) {
            continue
        }
        val occurrences = fields[1]
        val files = fields[2]
        val repos = fields[3]
        result.add(FormatPopularity(format, files.toInt()))
    }
    return result
}

internal fun hasMinglingComponents(format: UnicodeFormat): Boolean {
    var dateBasedEncountered = false
    var timeBasedEncountered = false
    var zoneBasedEncountered = false
    var lastEncountered: Any = DateBasedUnicodeDirective::class
    for (directive in directivesInFormat(format).map { UnicodeDirective(it) }) {
        when (directive) {
            is DateBasedUnicodeDirective -> {
                if (dateBasedEncountered && lastEncountered != DateBasedUnicodeDirective::class)
                    return false
                else {
                    dateBasedEncountered = true
                    lastEncountered = DateBasedUnicodeDirective::class
                }
            }

            is TimeBasedUnicodeDirective -> {
                if (timeBasedEncountered && lastEncountered != TimeBasedUnicodeDirective::class)
                    return false
                else {
                    timeBasedEncountered = true
                    lastEncountered = TimeBasedUnicodeDirective::class
                }
            }

            is ZoneBasedUnicodeDirective -> {
                if (zoneBasedEncountered && lastEncountered != ZoneBasedUnicodeDirective::class)
                    return false
                else {
                    zoneBasedEncountered = true
                    lastEncountered = ZoneBasedUnicodeDirective::class
                }
            }

            else -> {}
        }
    }
    return false
}

internal typealias FormatPopularityMap = Map<String, Triple<Int, Double, List<FormatPopularity>>>
internal typealias FormatPopularityMapUpdater = (FormatPopularityMap) -> FormatPopularityMap

internal fun onlyGetNPopularUsages(n: Int): FormatPopularityMapUpdater = { map ->
    buildMap {
        for (directive in map.keys) {
            val (count, frequency, formats) = map[directive]!!
            put(directive, Triple(count, frequency, formats.sortedBy { -it.popularity }.take(n)))
        }
    }
}

internal val sortDirectivesByPopularity: FormatPopularityMapUpdater = { map ->
    buildMap {
        for ((directive, entry) in map.entries.sortedByDescending { it.value.first }) {
            put(directive, entry)
        }
    }
}

internal val conflateStylesOfSameDirective: FormatPopularityMapUpdater = { map ->
    buildMap {
        val directives = map.keys.groupBy { it[0] }
        for ((directive, styles) in directives) {
            var totalCount = 0
            var totalFrequency = 0.0
            val totalFormats = mutableListOf<FormatPopularity>()
            for (style in styles) {
                val (count, frequency, formats) = map[style]!!
                totalCount += count
                totalFrequency += frequency
                totalFormats += formats
            }
            put(directive.toString(), Triple(totalCount, totalFrequency, totalFormats))
        }
    }
}

internal fun buildPatternMap(
    patternsFile: String,
    updatePatterns: (List<FormatPopularity>) -> List<FormatPopularity>,
    vararg updaters: FormatPopularityMapUpdater
): FormatPopularityMap {
    val patterns = updatePatterns(parsePatternsCsv(patternsFile))
    val directiveMap: MutableMap<String, Triple<Int, Double, List<FormatPopularity>>> = mutableMapOf()
    val totalPatterns = patterns.sumOf { it.popularity }
    for (pattern in patterns) {
        for (directive in directivesInFormat(pattern.format)) {
            val (count, frequency, formats) = directiveMap.getOrPut(directive) {
                Triple(pattern.popularity, pattern.popularity / totalPatterns.toDouble(), listOf(pattern))
            }
            if (formats.contains(pattern)) continue
            directiveMap[directive] = Triple(
                count + pattern.popularity,
                frequency + pattern.popularity / totalPatterns.toDouble(),
                formats + pattern
            )
        }
    }
    var result: FormatPopularityMap = directiveMap
    for (updater in updaters) {
        result = updater(result)
    }
    return result
}

internal fun availableForFreeWith(directive: String): List<String> {
    val result = mutableListOf(directive)
    val yearList = listOf("y", "yy", "yyy", "yyyy", "yyyyy", "u", "uu", "uuu", "uuuu", "uuuuu")
    val fractionList = listOf("S", "SS", "SSS", "SSSS", "SSSSS", "SSSSSS", "SSSSSSS", "SSSSSSSS", "SSSSSSSSS")
    when (directive) {
        in yearList -> result += yearList
        in fractionList -> result += fractionList
    }
    return result
}

internal fun takeEnoughDirectivesToSatisfyTheFraction(fraction: Double): (List<FormatPopularity>) -> List<FormatPopularity> =
    { initialPatterns ->
        val patterns = initialPatterns.sortedBy { it.popularity }.toMutableList()
        val totalPatterns = patterns.sumOf { it.popularity }
        var found = 0
        val result = mutableListOf<FormatPopularity>()
        val takenDirectives = mutableSetOf<String>()
        val remainingDirectives: MutableMap<String, Int> =
            patterns.flatMap { format ->
                directivesInFormat(format.format).map {
                    it to format.popularity
                }
            }.fold(mutableMapOf()) { acc, (directive, popularity) ->
                acc[directive] = (acc[directive] ?: 0) + popularity
                acc
            }
        while (found < totalPatterns * fraction) {
            val startDirective = remainingDirectives.maxBy { it.value }.key
            val available = availableForFreeWith(startDirective)
            for (directive in available) {
                remainingDirectives.remove(directive)
                takenDirectives.add(directive)
                var popularities = 0
                for (pattern in patterns.toSet()) {
                    if (takenDirectives.containsAll(directivesInFormat(pattern.format))) {
                        patterns.remove(pattern)
                        result.add(pattern)
                        popularities += pattern.popularity
                    }
                }
                found += popularities
            }
        }
        result
    }

internal fun buildConflatedPatternMap(text: String): FormatPopularityMap = buildPatternMap(
    text,
    takeEnoughDirectivesToSatisfyTheFraction(0.99),
    // conflateStylesOfSameDirective,
    onlyGetNPopularUsages(10),
    sortDirectivesByPopularity,
)
