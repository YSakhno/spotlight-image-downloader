package io.ysakhno.tools.spotlight.imagedownloader

import io.ysakhno.tools.spotlight.imagedownloader.data.DownloadedFileInfo
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import org.jsoup.Connection

/**
 * Handles the downloading of actual image files from a Web server.
 *
 * @param processingStats tracks the statistics of the entire download process.
 * @param httpSession the session to use for HTTP requests.
 * @param dbManager manages the local database of downloaded files.
 * @param downloadsDir the directory where images are saved.
 * @author Yurii Sakhno
 */
class ImageFileDownloader(
    private val processingStats: ProcessingStats,
    private val httpSession: Connection,
    private val dbManager: DatabaseManager,
    private val downloadsDir: File,
) {
    /**
     * Downloads an image and saves its metadata to the database.
     *
     * @param url the URL of the image.
     * @param categoryName the name of the category the image belongs to.
     * @param imageName the short (concise) name for the image file.
     * @param title the title of the image.
     * @param description a description of the image.
     * @return the result of the download operation.
     */
    fun downloadImage(
        url: String,
        categoryName: String,
        imageName: String,
        title: String,
        description: String,
    ): DownloadResult {
        val response = httpSession.newRequest().url(url).ignoreContentType(true).execute()
        val imgData = response.bodyAsBytes()
        val imgHash = imgData.hashStrSha256

        dbManager.getDownloadInfoByHash(imgHash)?.let { info ->
            processingStats.incrementSkipped()
            return DownloadResult.Duplicate(info)
        }

        val filename = generateFilename(categoryName, imageName)
        val file = File(downloadsDir, filename)
        Files.write(file.toPath(), imgData)

        val info = DownloadedFileInfo(
            hash = imgHash,
            filename = filename,
            category = categoryName,
            imageName = imageName,
            title = title,
            description = description,
        )

        dbManager.saveToDatabase(info)
        processingStats.addDownloadedFile(info)

        return DownloadResult.Saved(info)
    }

    private fun generateFilename(categoryName: String, title: String): String {
        val safeCategory = categoryName.replace(Regex("[ -]"), "_")
        val safeTitle = title.replace(Regex("[ -]"), "_")
        val baseName = "$safeCategory-$safeTitle"
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

/** Computes the SHA-256 hash of the bytes stored in this array and returns that hash as a hexadecimal string. */
private val ByteArray.hashStrSha256
    get() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }
