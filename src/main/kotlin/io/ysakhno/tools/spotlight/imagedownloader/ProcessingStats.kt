package io.ysakhno.tools.spotlight.imagedownloader

/** Tracks the statistics and progress of the image downloading and processing. */
class ProcessingStats {
    /** The number of images successfully downloaded in the current run. */
    var downloadedCount = 0
        private set

    /** The number of images that were skipped because they had already been downloaded. */
    var skippedCount = 0
        private set

    /** The number of errors encountered during the current run. */
    var errorCount: Int = 0
        private set

    /** Indicates whether enough images have been processed to satisfy the current run's requirements. */
    val isProcessedEnough get() = downloadedCount > 0

    /** The set of category names for which at least one new image was downloaded. */
    val categoriesWithNewFiles = mutableSetOf<String>()

    /** A map of category names to a list of information about the files downloaded in each category. */
    val newFilesByCategory = mutableMapOf<String, MutableList<DownloadedFileInfo>>()

    /**
     * Adds information about a newly downloaded file to the statistics.
     *
     * @param categoryName the name of the category the image belongs to.
     * @param info the information about the downloaded file.
     */
    fun addDownloadedFile(categoryName: String, info: DownloadedFileInfo) {
        newFilesByCategory.getOrPut(categoryName) { mutableListOf() }.add(info)
        categoriesWithNewFiles.add(categoryName)
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
