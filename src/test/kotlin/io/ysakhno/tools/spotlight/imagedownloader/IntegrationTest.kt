package io.ysakhno.tools.spotlight.imagedownloader

import com.ginsberg.junit.exit.assertions.SystemExitAssertion.assertThatCallsSystemExit
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.system.NoSystemErrListener
import io.kotest.extensions.system.captureStandardOut
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAllInAnyOrder
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContainAnyOf
import io.ysakhno.tools.spotlight.imagedownloader.util.captureFiles
import io.ysakhno.tools.spotlight.imagedownloader.util.getDownloadsTableAsTsv
import io.ysakhno.tools.spotlight.imagedownloader.util.shouldBeAsIn
import io.ysakhno.tools.spotlight.imagedownloader.util.shouldContainExactlyInAnyOrderAsIn
import io.ysakhno.tools.spotlight.imagedownloader.util.shouldNotContainAnyOfIgnoringCase
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.system.exitProcess
import org.mockserver.verify.VerificationTimes.never
import org.mockserver.verify.VerificationTimes.once

/**
 * Integration tests for the whole application.
 *
 * @author Yurii Sakhno
 */
@OptIn(ExperimentalPathApi::class)
class IntegrationTest : FunSpec() {

    private val testFilesDir = Path.of("test_files")
    private val resultsPath = Path.of("./tmp/test-results")
    private val dbFilePath = resultsPath.resolve("test.db").absolutePathString()
    private val downloadsDir = resultsPath.resolve("downloads")

    override val extensions = listOf(NoSystemErrListener)

    init {
        val server = MockHttpServer(testFilesDir)
        val dbManager = DatabaseManager(dbFilePath)

        beforeSpec {
            if (resultsPath.exists()) {
                resultsPath.deleteRecursively()
            }
            resultsPath.createDirectories()

            dbManager.migrateDatabase()
            dbManager.connect()

            // Pre-populate DB with necessary test data for scenarios
            DriverManager.getConnection("jdbc:sqlite:$dbFilePath").use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeUpdate(Path.of("test_files/integration-testing/pre-seed.sql").readText())
                }
            }
        }

        afterSpec {
            dbManager.close()
            server.close()
        }

        test("Scenario 1: Download and save images to disk and database") {
            val initialUrl = "http://localhost:${server.port}/pages/initial-1.html"

            val alreadyExistingFiles = downloadsDir.captureFiles()
            val alreadyExistingRows = getDownloadsTableAsTsv(dbFilePath).toSet()

            // Pre-check
            alreadyExistingFiles.map { it.split(':').first() }.shouldNotContainAnyOfIgnoringCase(
                "Birds-American_Robin.jpg",
                "Birds-White_Stork_Leleka_bilyy.jpg",
                "Flowers-Nasturtium.jpg",
                "Flowers-Pelargonium_hortorum.jpg",
                "Flowers-Rock_purslane.jpg",
                "Nature-Forest.jpg",
                "Nature-Winter_Trees.jpg",
            )
            alreadyExistingRows.map { it.split('\t') }.map { (_, _, hash) -> hash }.shouldNotContainAnyOf(
                "543621:IOn1pXtV0IUsQPVP7bnZkiYaIJp6xlkPezaTdumJM2U", // Birds-American_Robin.jpg
                "121729:OrJj4pUbBk6nfsMtPc2RcbJutK/mtZdIlcFa1Rj1ODs", // Birds-White_Stork_Leleka_bilyy.jpg
                "196833:cS6btICsv9FjEdO6Bj6YFWaGWOCQmdhS354gsZUKq1s", // Flowers-Nasturtium.jpg
                "188182:glyeggVThd0GTG+7pMUIzTcDeI6i4XEzXIHIS0uh28I", // Flowers-Pelargonium_hortorum.jpg
                "169354:vp9jzFtfawoswc1iIFAVTI/6G19FRlqRyilTgHYIMbE", // Flowers-Rock_purslane.jpg
                "457917:v7PyNW+BvLYLPjDImzht4TReUHHgLJrerQFLUcvPZDs", // Nature-Forest.jpg
                "238757:AWZTfwsw8uEV6FWDfW7iukjYITcZGYW5sQ8miYaek18", // Nature-Winter_Trees.jpg
            )

            val consoleOutput = runDownload(initialUrl)

            consoleOutput.shouldBeAsIn("scenario-1-expected.out")

            val newlyCapturedFiles = downloadsDir.captureFiles()
            val newFiles = newlyCapturedFiles - alreadyExistingFiles

            newlyCapturedFiles shouldContainAllInAnyOrder alreadyExistingFiles
            newFiles.shouldContainExactlyInAnyOrder(
                "Birds-American_Robin.jpg:543621:20e9f5a57b55d0852c40f54fedb9d992261a209a7ac6590f7b369376e9893365",
                "Birds-White_Stork_Leleka_bilyy.jpg:121729" +
                    ":3ab263e2951b064ea77ec32d3dcd9171b26eb4afe6b5974895c15ad518f5383b",
                "Flowers-Nasturtium.jpg:196833:712e9bb480acbfd16311d3ba063e9815668658e09099d852df9e20b1950aab5b",
                "Flowers-Pelargonium_hortorum.jpg:188182" +
                    ":825c9e82055385dd064c6fbba4c508cd3703788ea2e171335c81c84b4ba1dbc2",
                "Flowers-Rock_purslane.jpg:169354:be9f63cc5b5f6b0a2cc1cd622050154c8ffa1b5f45465a91ca295380760831b1",
                "Nature-Forest.jpg:457917:bfb3f2356f81bcb60b3e30c89b386de1345e5071e02c9adead014b51cbcf643b",
                "Nature-Winter_Trees.jpg:238757:0166537f0b30f2e115e855837d6ee2ba48d82137191985b9b10f2689869e935f",
            )

            val downloadsData = getDownloadsTableAsTsv(dbFilePath)
            val newRows = downloadsData.filterNot(alreadyExistingRows::contains)

            downloadsData shouldContainAllInAnyOrder alreadyExistingRows
            newRows.shouldContainExactlyInAnyOrderAsIn("scenario-1-expected.csv")
        }

        test("Scenario 2: Skip duplicate by URL") {
            val initialUrl = "http://localhost:${server.port}/pages/animals-2.html"

            val alreadyExistingFiles = downloadsDir.captureFiles()
            val alreadyExistingRows = getDownloadsTableAsTsv(dbFilePath).toSet()
            val existingHashes = alreadyExistingRows.map { it.split('\t') }.map { (_, _, hash) -> hash }

            // Pre-check
            alreadyExistingFiles.map { it.split(':').first() }.shouldNotContainAnyOfIgnoringCase(
                "Birds-California_Scrub_Jay.jpg",
                "Birds-Goose_in_Seedskadee.jpg",
                "Ocean-Pacific_sea_nettle.jpg",
            )
            existingHashes.shouldNotContainAnyOf(
                "349638:8C34pnzkb0M6NyMYdKZhsjz8mBb58Zv7szC5wt2dNk4", // Birds-California_Scrub_Jay.jpg
            )
            existingHashes.shouldContainAllInAnyOrder(
                "340782:KyiHyfmWFjzg5EfsiPEOK6PxJOj0/XueR4wgWg4N2O0", // Birds-Goose_in_Seedskadee.jpg
                "414900:JSmMd1HkTCbtnTmqQtbuDOt88P5aR736rr7Wxr/M4hQ", // Ocean-Pacific_sea_nettle.jpg
            )

            val consoleOutput = runDownload(initialUrl)

            consoleOutput.shouldBeAsIn("scenario-2-expected.out")

            val newlyCapturedFiles = downloadsDir.captureFiles()
            val newFiles = newlyCapturedFiles - alreadyExistingFiles

            newlyCapturedFiles shouldContainAllInAnyOrder alreadyExistingFiles
            newFiles.shouldContainExactlyInAnyOrder(
                "Birds-California_Scrub_Jay.jpg:349638" +
                    ":f02df8a67ce46f433a37231874a661b23cfc9816f9f19bfbb330b9c2dd9d364e",
            )

            val downloadsData = getDownloadsTableAsTsv(dbFilePath)
            val newRows = downloadsData.filterNot(alreadyExistingRows::contains)

            downloadsData shouldContainAllInAnyOrder alreadyExistingRows
            newRows.shouldContainExactlyInAnyOrderAsIn("scenario-2-expected.csv")

            // Verify that duplicate images (already known by URL) were NOT requested from the server
            server.verify("/images/597017797.jpg", never()) // Birds > Canada Goose
            server.verify("/images/632109363.jpg", never()) // Ocean > Jellyfish
        }

        test("Scenario 3: Skip duplicate by content hash") {
            val initialUrl = "http://localhost:${server.port}/pages/initial-3.html"

            val alreadyExistingFiles = downloadsDir.captureFiles()
            val alreadyExistingRows = getDownloadsTableAsTsv(dbFilePath).toSet()
            val existingHashes = alreadyExistingRows.map { it.split('\t') }.map { (_, _, hash) -> hash }

            // Pre-check
            alreadyExistingFiles.map { it.split(':').first() }.shouldNotContainAnyOfIgnoringCase(
                "Animals-Jellyfish.jpg",
                "Ocean-Pacific_sea_nettle.jpg",
                "Space-The_Moon.jpg",
                "Unusual-Blue_Moon.jpg",
            )
            existingHashes.shouldContainAllInAnyOrder(
                "414900:JSmMd1HkTCbtnTmqQtbuDOt88P5aR736rr7Wxr/M4hQ", // Ocean-Pacific_sea_nettle.jpg
                "49150:2vkUSYYVceHvv2b1FHt4TmX7wuqGsxWoUvw0pCqN7ts", // Unusual-Blue_Moon.jpg
            )

            val consoleOutput = runDownload(initialUrl)

            consoleOutput.shouldBeAsIn("scenario-3-expected.out")

            val newlyCapturedFiles = downloadsDir.captureFiles()
            val newFiles = newlyCapturedFiles - alreadyExistingFiles

            newlyCapturedFiles shouldContainAllInAnyOrder alreadyExistingFiles
            newFiles.shouldBeEmpty()

            val downloadsData = getDownloadsTableAsTsv(dbFilePath)
            val newRows = downloadsData.filterNot(alreadyExistingRows::contains)

            downloadsData shouldContainAllInAnyOrder alreadyExistingRows
            newRows.shouldBeEmpty()

            // Verify that duplicate images (detected by content hash) WERE still requested from the server
            server.verify("/images/367575361.jpg", once()) // Animals > Jellyfish
            server.verify("/images/603064932.jpg", once()) // Space > The Moon
        }

        test("Scenario 4: Download with filename suffix for conflict") {
            val initialUrl = "http://localhost:${server.port}/pages/initial-4.html"

            val alreadyExistingFiles = downloadsDir.captureFiles()
            val alreadyExistingRows = getDownloadsTableAsTsv(dbFilePath).toSet()
            val existingHashes = alreadyExistingRows.map { it.split('\t') }.map { (_, _, hash) -> hash }

            // Pre-check
            alreadyExistingFiles.map { it.split(':').first() }.shouldNotContainAnyOfIgnoringCase(
                "Cities-Seattle_Skyline.jpg",
                "Cities-Seattle_Skyline-1.jpg",
                "Cities-Seattle_Skyline-2.jpg",
                "Cities-Seattle_Skyline-3.jpg",
                "Cities-Seattle_Skyline-4.jpg",
                "Cities-Seattle_Skyline-5.jpg",
            )
            existingHashes.shouldNotContainAnyOf(
                "273899:ngSyjnUIg+KB7XHGwcI/iTKMW042JP42NBKnip8voKo", // Cities-Seattle_Skyline-3.jpg
            )
            existingHashes.shouldContainAllInAnyOrder(
                "278615:QXdsC7D2S+lKB2P4OHcRIufr6LiGU8u27Mq88IzS+nA", // Cities-Seattle_Skyline.jpg
                "348777:2J54ijQu+b+vMkFiKERyLnKRW4jrkLIc776XU0aPglk", // Cities-Seattle_Skyline-2.jpg
                "324233:YVTKlqAHrQAW1KQ3GE6wcH3MOkYnXBY78dqQZLrjr3M", // Cities-Seattle_Skyline-4.jpg
            )

            val consoleOutput = runDownload(initialUrl)

            consoleOutput.shouldBeAsIn("scenario-4-expected.out")

            val newlyCapturedFiles = downloadsDir.captureFiles()
            val newFiles = newlyCapturedFiles - alreadyExistingFiles

            newlyCapturedFiles shouldContainAllInAnyOrder alreadyExistingFiles
            newFiles.shouldContainExactlyInAnyOrder(
                "Cities-Seattle_Skyline-3.jpg:273899:9e04b28e750883e281ed71c6c1c23f89328c5b4e3624fe363412a78a9f2fa0aa",
            )

            val downloadsData = getDownloadsTableAsTsv(dbFilePath)
            val newRows = downloadsData.filterNot(alreadyExistingRows::contains)

            downloadsData shouldContainAllInAnyOrder alreadyExistingRows
            newRows.shouldContainExactlyInAnyOrderAsIn("scenario-4-expected.csv")

            // Verify that the image file was downloaded exactly once
            server.verify("/images/530577818.jpg", once()) // Cities > Seattle Skyline
        }
    }

    /**
     * Executes the download operation using the specified initial URL, simulating a full execution of the application
     * with specific parameters. Upon completion, this method asserts that the program's exit code is `0`. Additionally,
     * console output (on `stdout`) is captured and returned as a string at the end of the run.
     *
     * @param initialUrl the initial URL to start the scraping process from.
     */
    private fun runDownload(initialUrl: String) = captureStandardOut {
        assertThatCallsSystemExit {
            main(
                "--db-file",
                dbFilePath,
                "download",
                "--downloads-dir",
                downloadsDir.pathString,
                "--max-downloads-per-run",
                "10000",
                initialUrl,
            )
            exitProcess(0) // call explicitly in case Clikt exited cleanly
        }.withExitCode(0)
    }
}

/**
 * Convenience function to call the `main()` entry point while supplying command line arguments as a `vararg` list
 * rather than an array of strings.
 */
private fun main(vararg args: String) = main(arrayOf(*args))
