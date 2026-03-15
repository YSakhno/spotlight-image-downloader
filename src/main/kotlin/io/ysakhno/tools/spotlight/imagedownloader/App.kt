package io.ysakhno.tools.spotlight.imagedownloader

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
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
class App : CliktCommand() {
    private val initialUrl by argument()

    private val downloadsDir = File("downloads")
    private val dbFile = File("spotlight_downloader.db")
    private val dbManager = DatabaseManager(dbFile)
    private val processingStats = ProcessingStats()
    private val httpSession = Jsoup.newSession()
    private val downloader = ImageFileDownloader(processingStats, httpSession, dbManager, downloadsDir)
    private val scraper = SpotlightScraper(
        processingStats = processingStats,
        httpSession = httpSession,
        downloader = downloader,
    )

    /** Executes the application's main logic. */
    override fun run() {
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        try {
            dbManager.initDatabase()
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
