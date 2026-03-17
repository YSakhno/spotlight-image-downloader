package io.ysakhno.tools.spotlight.imagedownloader

import io.ysakhno.tools.spotlight.imagedownloader.data.DownloadedFileInfo

/** Tracks the statistics and progress of the image downloading and processing. */
class ProcessingStats(
    /**
     * The maximum number of images allowed to be downloaded in the current run. A `null` value indicates no limit.
     */
    private val maxDownloadsPerRun: Int? = null,
) {
    /** The number of images successfully downloaded in the current run. */
    var downloadedCount = 0
        private set

    /** The number of images that were skipped because they had already been downloaded. */
    var skippedCount = 0
        private set

    /** The number of errors encountered during the current run. */
    var errorCount: Int = 0
        private set

    /** Indicates whether the download limit for the current run has been reached. */
    val isDownloadsLimitReached get() = maxDownloadsPerRun != null && downloadedCount >= maxDownloadsPerRun

    /** The set of category names for which at least one new image was downloaded. */
    val categoriesWithNewFiles = mutableSetOf<String>()

    /** A map of category names to a list of information about the files downloaded in each category. */
    val newFilesByCategory = mutableMapOf<String, MutableList<DownloadedFileInfo>>()

    /** Adds information about a newly downloaded file to the statistics. */
    fun addDownloadedFile(info: DownloadedFileInfo) {
        newFilesByCategory.getOrPut(info.category) { mutableListOf() }.add(info)
        categoriesWithNewFiles.add(info.category)
        downloadedCount++
    }

    /** Increments the count of images that were skipped. */
    fun incrementSkipped() {
        skippedCount++
    }

    /**
     * Reports an error that occurred during processing and increments the error count.
     *
     * @param message a description of the error.
     */
    fun reportError(message: String) {
        System.err.println("ERROR: $message")
        errorCount++
    }
}
