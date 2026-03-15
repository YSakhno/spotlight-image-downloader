package io.ysakhno.tools.spotlight.imagedownloader

/**
 * Holds information about a downloaded image file.
 *
 * @property filename The name of the file as saved on disk.
 * @property category The name of the category the image belongs to.
 * @property title The title of the image.
 * @property description A description of the image. Can be empty if not available.
 * @property hash The SHA-256 hash of the image data.
 * @author Yurii Sakhno
 */
data class DownloadedFileInfo(
    val filename: String,
    val category: String,
    val title: String,
    val description: String,
    val hash: String,
)
