package io.ysakhno.tools.spotlight.imagedownloader

import io.ysakhno.tools.spotlight.imagedownloader.data.DownloadedFileInfo

/**
 * Represents the result of a download operation.
 *
 * @author Yurii Sakhno
 */
sealed interface DownloadResult {

    /**
     * The image was successfully downloaded and saved.
     *
     * @property info information about the image that was successfully downloaded and saved.
     */
    data class Saved(val info: DownloadedFileInfo) : DownloadResult

    /**
     * The image was already present in the database and was skipped. This class contains additional information about
     * the original file (that this image is a duplicate of).
     *
     * @property info information about the image that was already present in the database.
     */
    data class Duplicate(val info: DownloadedFileInfo) : DownloadResult
}
