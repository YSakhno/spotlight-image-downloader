@file:Suppress("ktlint:standard:filename") // the casing of the file name is to signify that it is a utility file

package io.ysakhno.tools.spotlight.imagedownloader.util

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.kotest.matchers.shouldBe
import java.io.StringWriter
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import java.util.TreeMap
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.use
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter

/**
 * Captures information about files in a directory specified by this path.
 *
 * For each regular file in the directory, this function computes a string representation containing the file name, file
 * size, and its SHA-256 hash. The resulting strings are returned as a sorted set in a case-insensitive order.
 *
 * If the invoking path does not exist or is not a directory, an empty set is returned.
 *
 * File details are formatted as follows: `<file-name>:<file-size>:<sha256-hash>`
 *
 * @receiver the directory path from which information about files will be captured.
 * @return a sorted set of strings representing file details, or an empty set if no valid files are found.
 */
internal fun Path.captureFiles() = if (exists() && isDirectory()) listDirectoryEntries("*.*")
    .filter { it.isRegularFile(NOFOLLOW_LINKS) }
    .map { entry ->
        val sha256Hash = MessageDigest.getInstance("SHA-256")
            .digest(entry.readBytes())
            .joinToString(separator = "", transform = Byte::toHexString)
        "${entry.name}:${entry.fileSize()}:$sha256Hash"
    }
    .toSortedSet(String.CASE_INSENSITIVE_ORDER)
else emptySet()

/**
 * Retrieves the `downloads` table from the specified SQLite database file and converts it to a TSV string.
 *
 * Columns containing date information, such as `download_time` and `last_modified_time`, are excluded from the TSV
 * output. SQL `NULL` values in the table are replaced with the string "`<NULL>`" in the resulting TSV.
 *
 * @param dbFilePath the path to the SQLite database file from which the `downloads` table is retrieved.
 * @return list of lines representing the contents of the `downloads` table in TSV format. Each row in the table is
 * represented by a separate entry in the list; the first entry is the header containing the names of the columns.
 */
@Suppress("detekt:complexity:NestedBlockDepth") // it's all right
internal fun getDownloadsTableAsTsv(dbFilePath: String): List<String> {
    val writer = StringWriter()

    DriverManager.getConnection("jdbc:sqlite:$dbFilePath").use { conn ->
        conn.createStatement().use { stmt ->
            stmt.executeQuery("SELECT * FROM downloads").use { rs ->
                val metaData = rs.metaData
                val dateColumns = setOf("download_time", "last_modified_time")
                val columnNames = (1..metaData.columnCount)
                    .associateByTo(TreeMap(), metaData::getColumnName)
                    .filterKeys { it !in dateColumns }

                val format = CSVFormat.DEFAULT
                    .builder()
                    .setHeader(*columnNames.keys.toTypedArray())
                    .setDelimiter('\t')
                    .get()
                CSVPrinter(writer, format).use { printer ->
                    while (rs.next()) {
                        printer.printRecord(columnNames.map { (_, idx) -> rs.getString(idx) ?: "<NULL>" })
                    }
                }
            }
        }
    }

    return writer.toString().lines().filter(String::isNotBlank)
}

/**
 * Verifies that this string equals the textual context of the specified file, character for character.
 *
 * Note: Comparison is case-sensitive. Whitespace and even line-ending characters must also match exactly.
 *
 * @receiver the text to verify.
 * @param verificationFilename the path to a file containing the expected text. This path may be either relative or
 * absolute. If the path is relative, it is resolved against the `test_files/integration-testing/` directory.
 * @return the input string.
 */
@IgnorableReturnValue
internal fun String.shouldBeAsIn(verificationFilename: String) =
    Path.of("test_files/integration-testing/").resolve(verificationFilename).readText().let { expected ->
        this shouldBe expected
    }

/**
 * Verifies that this collection contains all the strings as the specified CSV/TSV file and no others, but in any order.
 *
 * The first line of the file is considered to have the header and is skipped. All the rest of the lines are read in as
 * strings and considered to be the expected elements.
 *
 * Note: Comparison is case-sensitive.
 *
 * @receiver the collection of strings to verify. Can be `null` (this relaxition is similar to the Kotest's own
 * assertions on collections, such as `shouldContainAllInAnyOrder`, for example).
 * @param verificationFilename the path to the CSV/TSV file containing the expected strings. This path may be either
 * relative or absolute. If the path is relative, it is resolved against the `test_files/integration-testing/`
 * directory.
 * @return the input collection.
 */
@IgnorableReturnValue
internal fun Collection<String>?.shouldContainExactlyInAnyOrderAsIn(verificationFilename: String) =
    Path.of("test_files/integration-testing/").resolve(verificationFilename).readLines(UTF_8).drop(1).let { expected ->
        this shouldContainExactlyInAnyOrder expected
    }

/**
 * Verifies that the given [Collection] of strings does not contain any of the specified strings, ignoring case. The
 * collection may additionally contain other strings.
 */
internal fun Collection<String>.shouldNotContainAnyOfIgnoringCase(vararg ts: String) =
    shouldNotContainAnyOf(ts.toSortedSet(String.CASE_INSENSITIVE_ORDER))
