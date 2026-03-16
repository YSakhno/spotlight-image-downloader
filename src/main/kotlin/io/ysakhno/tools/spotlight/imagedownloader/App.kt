package io.ysakhno.tools.spotlight.imagedownloader

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.help
import com.github.ajalt.clikt.parameters.options.versionOption
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import org.jsoup.Jsoup
import java.io.File
import java.io.FileWriter
import kotlin.system.exitProcess

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
        .help(
            "The initial URL to start scraping from" +
                " (this page must contain the list of Categories, not the particular category)",
        )

    private val downloadsDir = File("downloads")
    private val dbFile = File("spotlight_downloader.db")
    private val dbManager = DatabaseManager(dbFile.absolutePath)
    private val processingStats = ProcessingStats()
    private val httpSession = Jsoup.newSession()
    private val downloader = ImageFileDownloader(processingStats, httpSession, dbManager, downloadsDir)
    private val scraper = SpotlightScraper(
        processingStats = processingStats,
        httpSession = httpSession,
        downloader = downloader,
    )

    init {
        versionOption(version = AppVersion.version, message = { AppVersion.nameAndVersion })
    }

    /** Executes the application's main logic. */
    override fun run() {
        echo(AppVersion.fullName)
        echo()

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        try {
            // Prepare the database and connect to it
            dbManager.migrateDatabase()
            dbManager.connect()

            scraper.processInitialPage(initialUrl)
            println()
            generateSummaries()
            printFinalStats()
        } catch (e: Exception) {
            System.err.println("Fatal error: ${e.message}")
            e.printStackTrace()
            exitProcess(1)
        } finally {
            dbManager.close()
        }
    }

    private fun generateSummaries() {
        for ((category, files) in processingStats.newFilesByCategory) {
            val safeCategory = category.replace(Regex("[ -]"), "_")
            val csvFile = File("${safeCategory}.csv")
            try {
                FileWriter(csvFile).use { writer ->
                    val printer = CSVPrinter(
                        writer,
                        CSVFormat.DEFAULT.builder().setHeader("Filename", "Title", "Description").get(),
                    )
                    for (file in files) {
                        printer.printRecord(file.filename, file.title, file.description)
                    }
                    printer.flush()
                }
            } catch (e: Exception) {
                processingStats.reportError("Error generating summary for category $category: ${e.message}")
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
            System.err.println("\nExecution finished with ${processingStats.errorCount} errors.")
        } else {
            println("\nExecution finished successfully.")
        }
    }
}

/**
 * The application's main entry point.
 *
 * @param args an array containing the command-line arguments.
 */
fun main(args: Array<String>) = App().main(args)
