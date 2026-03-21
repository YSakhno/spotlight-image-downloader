package io.ysakhno.tools.spotlight.imagedownloader.util

import com.ibm.icu.text.BreakIterator
import com.ibm.icu.text.BreakIterator.DONE
import com.ibm.icu.text.Transliterator
import kotlin.streams.toList

/** The maximum value for the ASCII character range. */
private const val MAX_ASCII = 127

/**
 * Transliterates this string into the ASCII range while trying to maintain its visual similarity.
 *
 * In cases where a single capital letter transliterates into a sequence of multiple characters, the first one
 * remains uppercase, and the rest are made lowercase.
 *
 * If a character cannot be transliterated into the ASCII range, its Unicode name is used instead (e.g., emojis).
 */
fun String.toAscii() = buildString {
    val boundary = BreakIterator.getCharacterInstance().apply { setText(this@toAscii) }

    var start = boundary.first()
    var end = boundary.next()

    while (end != DONE) {
        val next = boundary.next()
        val nextCodePoint = if (next != DONE) this@toAscii.codePointAt(end) else null

        appendTransliteratedCluster(this@toAscii.substring(start, end), nextCodePoint)

        start = end
        end = next
    }
}

/** An ICU transliterator used to retrieve the Unicode name of a character. */
private val unicodeNameTransliterator = Transliterator.getInstance("Any-Name")

/** A regular expression used to extract the name of a character from the output of the "Any-Name" transliterator. */
private val unicodeNameRegex = """\\N\{(.*?)\}""".toRegex()

/**
 * Appends the Unicode name of the provided character to this [StringBuilder].
 *
 * @param subClstr the character (or surrogate pair) whose name to append.
 * @param nextCodePoint the first code point of the next cluster, or `null` if there is no next cluster.
 */
private fun StringBuilder.appendUnicodeName(subClstr: String, nextCodePoint: Int?) {
    val name = unicodeNameTransliterator.transliterate(subClstr)
    val extracted = unicodeNameRegex.findAll(name)
        .map { it.groupValues[1].lowercase() }
        .filterNot { it.startsWith("variation selector-") }
        .joinToString(" ")

    if (extracted.isNotEmpty()) {
        if (isNotEmpty() && this[length - 1].isLetterOrDigit()) {
            append(' ')
        }
        append(extracted)
        if (nextCodePoint != null && Character.isLetterOrDigit(nextCodePoint)) {
            append(' ')
        }
    }
}

/** An ICU transliterator used to map Unicode characters to their ASCII "counterparts". */
private val transliterator = Transliterator.getInstance("Ukrainian-Latin/BGN; Any-Latin/BGN; Latin-ASCII")

/** Determines whether a given code point is not an ASCII character and not a control character. */
private fun isNotAsciiOrControl(cp: Int) = cp > MAX_ASCII && !Character.isWhitespace(cp) && !Character.isISOControl(cp)

/** Transliterates a single grapheme [cluster] and appends the result to this [StringBuilder]. */
private fun StringBuilder.appendTransliteratedCluster(cluster: String, nextCodePoint: Int?) {
    val transliterated = transliterator.transliterate(cluster)
    val codePoints = transliterated.codePoints().toList()

    if (codePoints.any(::isNotAsciiOrControl)) {
        codePoints.map(Character::toString).forEach { appendUnicodeName(it, nextCodePoint) }
    } else if (transliterated.length > 1 && cluster.any(Char::isUpperCase)) {
        append(transliterated.take(1).uppercase())
        append(transliterated.drop(1).lowercase())
    } else {
        append(transliterated)
    }
}

/** A regular expression pattern that matches one or more double quotes, single quotes, or apostrophes (backticks). */
private val quotesRegex = "[\"'`]+".toRegex()

/**
 * A regular expression pattern that matches one or more non-alphanumeric characters.
 * This includes any character that is not a letter (a-z, A-Z) or a digit (0-9).
 */
private val nonAlphanumericRegex = "[^a-zA-Z0-9]+".toRegex()

/**
 * Converts this string into a version that can be safely used as a filename by:
 * - transliterating it into the ASCII range while maintaining visual similarity;
 * - converting all ampersands (`&`) and at-signs (`@`) to "and" and "at" respectively;
 * - removing any quotes or apostrophes from the result;
 * - replacing all remaining non-alphanumeric characters with underscores (multiple consecutive occurrences are
 * collapsed);
 * - removing any leading or trailing underscores from the result.
 */
val String.sanitizedName get() = toAscii()
    .replace("&", " and ")
    .replace("@", " at ")
    .replace(quotesRegex, "")
    .replace(nonAlphanumericRegex, "_")
    .trim('_')
