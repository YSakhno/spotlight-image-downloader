package io.ysakhno.tools.spotlight.imagedownloader

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import com.github.ajalt.clikt.parameters.types.restrictTo
import io.ysakhno.tools.spotlight.imagedownloader.util.sanitizedName
import java.io.File
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
 * Main class for the Spotlight image downloader application.
 *
 * @author Yurii Sakhno
 */
class App : CliktCommand(name = "spotlight-image-downloader") {

    /**
     * Stores the initial URL from which the web scraping process starts. This URL is received from the command line
     * argument.
     */
    private val initialUrl by argument(name = "initial-url")
        .help("The initial URL to start crawling and downloading from")

    /** The maximum number of images to download in a single run. This limit is set by a hidden command line option. */
    private val maxDownloadsPerRun by option("--max-downloads-per-run", hidden = true)
        .int()
        .restrictTo(min = 1)
        .help("The maximum number of images to download in a run")

    /** The command line option to specify the path to save downloaded files to. */
    private val downloadsDir by option()
        .path(mustExist = false, canBeFile = false, canBeDir = true, canBeSymlink = true)
        .default(Path.of("./downloads/"))
        .help("The path to save downloaded files to (default: ./downloads/)")

    /** The command line option to specify the path to the SQLite database file. */
    private val dbFile by option()
        .file(mustExist = false, canBeDir = false, canBeFile = true, canBeSymlink = true)
        .default(File("spotlight_downloader.db"))
        .help("The path to the SQLite database file (default: spotlight_downloader.db)")

    /**
     * The command line option to specify whether to check for duplicate images by their original URL before downloading
     * them.
     *
     * If the value of the option is `true` (the default), duplicates will be checked by the images' URLs. If the value
     * is `false`, duplicates will be checked by hashes of the images' data only.
     */
    private val isCheckByUrl by option("--check-by-url")
        .flag("--no-check-by-url", default = true)
        .help("Check for duplicate images by their original URL before downloading them (on by default)")

    private val dbManager by lazy { DatabaseManager(dbFile.absolutePath) }
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

    init {
        versionOption(version = AppVersion.version, message = { AppVersion.nameAndVersion })
    }

    /** Executes the application's main logic. */
    override fun run() {
        echo(AppVersion.fullName)
        echo()

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

/**
 * The application's main entry point.
 *
 * @param args an array containing the command-line arguments.
 */
fun main(args: Array<String>) = App().main(args)
