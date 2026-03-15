package io.ysakhno.tools.spotlight.imagedownloader

/** Represents the result of a download operation. */
enum class DownloadResult {
    /** The image was successfully downloaded and saved. */
    SAVED,

    /** The image was already present in the database and was skipped. */
    DUPLICATE,
}
