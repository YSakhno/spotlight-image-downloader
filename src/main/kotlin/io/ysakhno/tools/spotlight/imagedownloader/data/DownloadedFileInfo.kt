package io.ysakhno.tools.spotlight.imagedownloader.data

/**
 * Holds information about a downloaded image file.
 *
 * @property hash The SHA-256 hash of the image data. Served as a unique identifier for the record about the download.
 * @property filename The name of the file as saved on disk.
 * @property category The name of the category the image belongs to.
 * @property imageName The short (concise) name of the image file.
 * @property title The title of the image.
 * @property description A description of the image. Can be empty if not available.
 * @property downloadTime The time when the image was downloaded and saved to disk, in ISO-8601 format.
 * @property lastModifiedTime The time when the image was last modified, in ISO-8601 format, or `null` if the time of
 * last modification is unknown.
 * @author Yurii Sakhno
 */
data class DownloadedFileInfo(
    val hash: String,
    val filename: String,
    val category: String,
    val imageName: String,
    val title: String,
    val description: String,
    val downloadTime: String,
    val lastModifiedTime: String? = null,
)
