package io.ysakhno.tools.spotlight.imagedownloader

import io.ysakhno.tools.spotlight.imagedownloader.data.DownloadedFileInfo
import io.ysakhno.tools.spotlight.imagedownloader.util.sanitizedName
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit.MILLIS
import java.util.Base64
import org.jsoup.Connection

/**
 * Handles the downloading of actual image files from a Web server.
 *
 * @param processingStats tracks the statistics of the entire download process.
 * @param httpSession the session to use for HTTP requests.
 * @param dbManager manages the local database of downloaded files.
 * @param downloadsDir the path of the directory where images are saved.
 * @param isCheckByUrl whether to check for duplicate images by their original URL before downloading them.
 * @author Yurii Sakhno
 */
class ImageFileDownloader(
    private val processingStats: ProcessingStats,
    private val httpSession: Connection,
    private val dbManager: DatabaseManager,
    private val downloadsDir: Path,
    private val isCheckByUrl: Boolean = true,
) {
    /**
     * Downloads an image and saves its metadata to the database.
     *
     * @param url the URL of the image.
     * @param originalUrl the original image URL (as it is specified for the background image in the HTML).
     * @param categoryName the name of the category the image belongs to.
     * @param imageName the short (concise) name for the image file.
     * @param title the title of the image.
     * @param description a description of the image.
     * @return the result of the download operation.
     */
    @Suppress("detekt:complexity:LongParameterList") // making an exception for this method
    fun downloadImage(
        url: String,
        originalUrl: String,
        categoryName: String,
        imageName: String,
        title: String,
        description: String,
    ): DownloadResult {
        if (isCheckByUrl) {
            dbManager.getDownloadInfoByOriginalUrl(originalUrl)?.let { info ->
                processingStats.incrementSkipped()
                return DownloadResult.Duplicate(info)
            }
        }

        val response = httpSession.newRequest().url(url).ignoreContentType(true).execute()
        val imgData = response.bodyAsBytes()
        val imgHash = imgData.hash

        dbManager.getDownloadInfoByHash(imgHash)?.let { info ->
            processingStats.incrementSkipped()
            return DownloadResult.Duplicate(info)
        }

        val lastModifiedHeader = response.header("Last-Modified")
        val lastModifiedTimestamp = lastModifiedHeader?.runCatching {
            Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(this))
        }?.getOrNull()

        val filename = generateFilename(categoryName, imageName)
        val path = downloadsDir.resolve(filename)

        Files.write(path, imgData)
        if (lastModifiedTimestamp != null) Files.setLastModifiedTime(path, lastModifiedTimestamp.toFileTime())

        val info = DownloadedFileInfo(
            hash = imgHash,
            originalUrl = originalUrl,
            filename = filename,
            category = categoryName,
            imageName = imageName,
            title = title,
            description = description,
            downloadTime = Instant.now().truncatedTo(MILLIS).toString(),
            lastModifiedTime = lastModifiedTimestamp?.toString(),
        )

        dbManager.saveToDatabase(info)
        processingStats.addDownloadedFile(info)

        return DownloadResult.Saved(info)
    }

    /**
     * Generates a unique filename for an image file based on the provided category name and title. Both category and
     * title are [sanitized][String.sanitizedName] before being used as part of the filename.
     *
     * The method ensures that there are no collisions with existing filenames in the database.
     *
     * @param categoryName the name of the category the file belongs to.
     * @param title the title of the image.
     * @return a unique filename for the image file, formatted as `"{categoryName}-{title}.jpg"`, with an optional
     * numeric suffix to avoid collisions.
     */
    private fun generateFilename(categoryName: String, title: String): String {
        val baseName = "${categoryName.sanitizedName}-${title.sanitizedName}"
        var suffix = ""
        var counter = 2

        while (true) {
            val filename = "$baseName$suffix.jpg"
            // Check database for name collision
            if (!dbManager.isFilenameTaken(filename)) return filename
            else suffix = "-${counter++}" // Collision with a different file, increment breaker suffix
        }
    }
}

/**
 * Computes the hash of the bytes stored in this array and returns the Base64-encoded SHA-256 hash prefixed with the
 * size of the original (this) array.
 */
internal val ByteArray.hash: String
    get() {
        val sha256Hash = MessageDigest.getInstance("SHA-256").digest(this)
        val sha256Encoded = Base64.getEncoder().withoutPadding().encodeToString(sha256Hash)

        return "${this.size}:$sha256Encoded"
    }

/** Converts this Java [Instant] to a [FileTime] representing the same point of time value on the time-line. */
private fun Instant.toFileTime() = FileTime.from(this)
