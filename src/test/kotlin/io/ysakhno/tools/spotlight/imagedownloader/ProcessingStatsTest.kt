package io.ysakhno.tools.spotlight.imagedownloader

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.system.NoSystemOutListener
import io.kotest.extensions.system.SystemErrWireListener
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.ysakhno.tools.spotlight.imagedownloader.data.DownloadedFileInfo

/**
 * Contains unit tests for the [ProcessingStats] class.
 *
 * @author Yurii Sakhno
 */
class ProcessingStatsTest : FunSpec({
    test("initial state should have all counts at zero and collections empty") {
        val stats = ProcessingStats()

        stats.downloadedCount shouldBe 0
        stats.skippedCount shouldBe 0
        stats.errorCount shouldBe 0
        stats.isDownloadsLimitReached.shouldBeFalse()
        stats.categoriesWithNewFiles.shouldBeEmpty()
        stats.newFilesByCategory.shouldBeEmpty()
    }

    context("Property isDownloadsLimitReached") {
        test("should always be false if maxDownloadsPerRun is null") {
            val stats = ProcessingStats(maxDownloadsPerRun = null)
            repeat(100) { i ->
                val info = DownloadedFileInfo(
                    hash = "h$i",
                    originalUrl = "/u$i.jpg",
                    filename = "f$i.jpg",
                    category = "C",
                    imageName = "l",
                    title = "ll",
                    description = "",
                    downloadTime = "2026-03-18T16:30:48.362Z",
                )
                stats.addDownloadedFile(info)
                withClue("iteration $i") { stats.isDownloadsLimitReached.shouldBeFalse() }
            }
        }
        test("should become true when downloadedCount reaches maxDownloadsPerRun") {
            val stats = ProcessingStats(maxDownloadsPerRun = 2)
            stats.isDownloadsLimitReached.shouldBeFalse()

            stats.addDownloadedFile(
                DownloadedFileInfo(
                    hash = "h1",
                    originalUrl = "/u1.jpg",
                    filename = "f1.jpg",
                    category = "C",
                    imageName = "n",
                    title = "nn",
                    description = "",
                    downloadTime = "2026-03-18T16:33:12.556Z",
                ),
            )
            stats.isDownloadsLimitReached.shouldBeFalse()

            stats.addDownloadedFile(
                DownloadedFileInfo(
                    hash = "h2",
                    originalUrl = "/u2.jpg",
                    filename = "f2.jpg",
                    category = "C",
                    imageName = "m",
                    title = "mm",
                    description = "",
                    downloadTime = "2026-03-18T16:34:40.137Z",
                ),
            )
            stats.isDownloadsLimitReached.shouldBeTrue()

            // Even if it is exceeded
            stats.addDownloadedFile(
                DownloadedFileInfo(
                    hash = "h3",
                    originalUrl = "/u3.jpg",
                    filename = "f3.jpg",
                    category = "C",
                    imageName = "o",
                    title = "pp",
                    description = "",
                    downloadTime = "2026-03-18T16:34:41.619Z",
                ),
            )
            stats.isDownloadsLimitReached.shouldBeTrue()
        }
    }

    context("Method addDownloadedFile") {
        test("should increment downloadedCount and update collections") {
            val stats = ProcessingStats()
            val info = DownloadedFileInfo(
                hash = "hash1",
                originalUrl = "/url1.jpg",
                filename = "file1.jpg",
                category = "CategoryA",
                imageName = "Image 1",
                title = "Title 1",
                description = "Desc 1",
                downloadTime = "2026-03-18T17:56:48Z",
            )

            stats.addDownloadedFile(info)

            stats.downloadedCount shouldBe 1
            stats.categoriesWithNewFiles shouldContainExactly setOf("CategoryA")
            stats.newFilesByCategory shouldContainKey "CategoryA"
            stats.newFilesByCategory["CategoryA"] shouldContainExactly listOf(info)
        }
        test("should handle multiple files in the same and different categories") {
            val stats = ProcessingStats()
            val info1 = DownloadedFileInfo(
                hash = "h1",
                originalUrl = "/imgs/u1.jpg",
                filename = "f1.jpg",
                category = "Cat1",
                imageName = "n1",
                title = "t1",
                description = "d1",
                downloadTime = "2026-03-18T17:57:06.879Z",
            )
            val info2 = DownloadedFileInfo(
                hash = "h2",
                originalUrl = "/imgs/u2.jpg",
                filename = "f2.jpg",
                category = "Cat2",
                imageName = "n2",
                title = "t2",
                description = "d2",
                downloadTime = "2026-03-18T17:57:26.630Z",
            )
            val info3 = DownloadedFileInfo(
                hash = "h3",
                originalUrl = "/imgs/u3.jpg",
                filename = "f3.jpg",
                category = "Cat1",
                imageName = "n3",
                title = "t3",
                description = "d3",
                downloadTime = "2026-03-18T17:57:44.373Z",
            )

            stats.addDownloadedFile(info1)
            stats.addDownloadedFile(info2)
            stats.addDownloadedFile(info3)

            stats.downloadedCount shouldBe 3
            stats.categoriesWithNewFiles shouldContainExactly setOf("Cat1", "Cat2")
            stats.newFilesByCategory["Cat1"] shouldContainExactly listOf(info1, info3)
            stats.newFilesByCategory["Cat2"] shouldContainExactly listOf(info2)
        }
    }

    context("Method incrementSkipped") {
        test("Method incrementSkipped should increment skippedCount") {
            val stats = ProcessingStats()
            repeat(100) { cnt ->
                withClue("before increment #${cnt + 1}") { stats.skippedCount shouldBe cnt }
                stats.incrementSkipped()
            }
            stats.skippedCount shouldBe 100
        }
    }

    context("Method reportError") {
        val errorListener = SystemErrWireListener(tee = false)
        extensions(NoSystemOutListener, errorListener)

        test("Method reportError should increment errorCount and write to stderr") {
            val stats = ProcessingStats()
            stats.reportError("Some error")
            stats.reportError("Another error")
            stats.errorCount shouldBe 2

            val outputLines = errorListener.output().lines().filter(String::isNotBlank)
            outputLines shouldContainExactly listOf("ERROR: Some error", "ERROR: Another error")
        }
    }
})
