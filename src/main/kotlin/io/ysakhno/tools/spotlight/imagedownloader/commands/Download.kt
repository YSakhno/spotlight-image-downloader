package io.ysakhno.tools.spotlight.imagedownloader.commands

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ysakhno.tools.spotlight.imagedownloader.AppVersion
import io.ysakhno.tools.spotlight.imagedownloader.DatabaseManager
import io.ysakhno.tools.spotlight.imagedownloader.ImageFileDownloader
import io.ysakhno.tools.spotlight.imagedownloader.ProcessingStats
import io.ysakhno.tools.spotlight.imagedownloader.SpotlightScraper
import io.ysakhno.tools.spotlight.imagedownloader.util.sanitizedName
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.jsoup.Jsoup

/**
 * The `download` command for the Spotlight image downloader application.
 *
 * @author Yurii Sakhno
 */
class Download : SkippableCommand() {

    /** Specifies the default path for storing downloaded files. */
    @Suppress("ktlint:standard:property-naming", "detekt:naming:VariableNaming") // this is effectively a constant
    private val DEFAULT_DOWNLOADS_PATH = "./${if (AppVersion.isDevelopment) "tmp/" else ""}downloads/"

    /** The command line argument to specify the initial URL from which the web scraping process starts. */
    internal val initialUrl by argument(name = "initial-url")
        .help("The initial URL to start crawling and downloading from")

    /** A hidden command line option to specify the maximum number of images to download in a single run. */
    internal val maxDownloadsPerRun by option("--max-downloads-per-run", hidden = true)
        .int()
        .restrictTo(min = 1)
        .run { if (AppVersion.isDevelopment) default(1) else this }
        .help("The maximum number of images to download in a run")

    /** The command line option to specify the path to save downloaded files to. */
    internal val downloadsDir by option()
        .path(mustExist = false, canBeFile = false, canBeDir = true, canBeSymlink = true)
        .default(Path.of(DEFAULT_DOWNLOADS_PATH))
        .help("The path to save downloaded files to (default: $DEFAULT_DOWNLOADS_PATH)")

    /**
     * The command line option to specify whether to check for duplicate images by their original URL before downloading
     * them.
     *
     * If the value of the option is `true` (the default), duplicates will be checked by the images' URLs. If the value
     * is `false`, duplicates will be checked by hashes of the images' data only.
     */
    internal val isCheckByUrl by option("--check-by-url")
        .flag("--no-check-by-url", default = true)
        .help("Check for duplicate images by their original URL before downloading them (on by default)")

    private val dbManager by requireObject<DatabaseManager>(DatabaseManager::class.java.name)
    private val processingStats by lazy { ProcessingStats(maxDownloadsPerRun) }
    private val httpSession = Jsoup.newSession()
    private val downloader by lazy {
        ImageFileDownloader(
            processingStats = processingStats,
            httpSession = httpSession,
            dbManager = dbManager,
            downloadsDir = downloadsDir,
            isCheckByUrl = isCheckByUrl,
        )
    }
    private val scraper by lazy {
        SpotlightScraper(
            processingStats = processingStats,
            httpSession = httpSession,
            downloader = downloader,
        )
    }

    /** Provides a short description of the `download` command. */
    override fun help(context: Context) = "Downloads images from the specified initial URL"

    /**
     * Executes the primary logic of the `download` command.
     *
     * This method is only executed when [isSkipRun][Root.isSkipRun] of the root command is `false`.
     */
    override fun runForReal() {
        if (!downloadsDir.exists()) {
            downloadsDir.createDirectories()
        }

        val executionOutcome = runCatching {
            // Prepare the database and connect to it
            dbManager.migrateDatabase()
            dbManager.connect()

            scraper.processUrl(initialUrl)
            println()
            generateSummaries()
            printFinalStats()
        }.recoverCatching { throwable ->
            echo("Fatal error: ${throwable.message}", err = true)
            echo(
                message = StringWriter().apply { throwable.printStackTrace(PrintWriter(this)) },
                err = true,
            )
            throw ProgramResult(1)
        }

        dbManager.close()
        executionOutcome.getOrThrow()
    }

    private fun generateSummaries() {
        for ((category, files) in processingStats.newFilesByCategory) {
            runCatching {
                FileWriter("${category.sanitizedName}.csv").use { writer ->
                    val printer = CSVPrinter(
                        writer,
                        CSVFormat.DEFAULT.builder().setHeader("Filename", "Title", "Description").get(),
                    )
                    for (file in files) {
                        printer.printRecord(file.filename, file.title, file.description)
                    }
                    printer.flush()
                }
            }.onFailure { throwable ->
                processingStats.reportError("Error generating summary for category $category: ${throwable.message}")
            }
        }
    }

    private fun printFinalStats() {
        println("Run summary:")
        println("Files downloaded: ${processingStats.downloadedCount}")
        println("Files skipped (duplicates): ${processingStats.skippedCount}")
        if (processingStats.errorCount > 0) println("Errors encountered: ${processingStats.errorCount}")

        if (processingStats.categoriesWithNewFiles.isNotEmpty()) {
            println("\nCategories with new downloads:")
            processingStats.categoriesWithNewFiles.sorted().map { "- $it" }.forEach(::println)
        }

        if (processingStats.errorCount > 0) {
            echo("", err = true)
            echo("Execution finished with ${processingStats.errorCount} errors.", err = true)
        } else {
            echo()
            echo("Execution finished successfully.")
        }
    }
}
