package io.ysakhno.tools.spotlight.imagedownloader

/**
 * Base class for all exceptions thrown by the Spotlight image downloader application.
 *
 * @param message the detail message.
 * @param cause the cause of the exception. Can be `null`.
 * @author Yurii Sakhno
 */
open class SpotlightDownloaderException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Thrown when an error occurs while scraping the Spotlight website.
 *
 * @param message the detail message.
 * @param cause the cause of the exception. Can be `null`.
 * @author Yurii Sakhno
 */
class ScrapingException(message: String, cause: Throwable? = null) : SpotlightDownloaderException(message, cause)
