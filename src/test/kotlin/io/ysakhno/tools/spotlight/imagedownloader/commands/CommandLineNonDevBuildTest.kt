package io.ysakhno.tools.spotlight.imagedownloader.commands

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.IsolationMode.InstancePerLeaf
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.ysakhno.tools.spotlight.imagedownloader.AppVersion
import java.io.File
import java.nio.file.Path

/**
 * Tests for the command line's output and default options' values for the non-dev version of the build.
 *
 * @author Yurii Sakhno
 */
class CommandLineNonDevBuildTest : FunSpec({

    isolationMode = InstancePerLeaf

    beforeSpec {
        mockkObject(AppVersion)
        every { AppVersion.isDevelopment } returns false
        every { AppVersion.title } returns "TestImageDownloader"
        every { AppVersion.version } returns "vX.Y.Z"
    }

    afterSpec {
        unmockkObject(AppVersion)
    }

    context("The root command") {
        val cli = Root(true)

        test("should have correct default database file") {
            cli.test("")
            cli.dbFile shouldBe File("spotlight_downloader.db")
        }
        test("option --version should show version and exit") {
            val result = cli.test("--version")

            result.statusCode shouldBe 0
            result.stdout.normalized() shouldBe "TestImageDownloader vX.Y.Z"
            result.stderr.shouldBeEmpty()
        }
    }

    context("Command 'download'") {
        val cli = Root(true)
        val download = cli.downloadSubCommand

        test("argument <initial-url> should show version in output") {
            val result = cli.test("download https://localhost:30882/check-version")

            result.statusCode shouldBe 0
            result.stdout.normalized() shouldBe """
                TestImageDownloader vX.Y.Z by Yurii Sakhno

                Execution of command 'spotlight-image-downloader > download' has been skipped
            """.trimIndent()
            result.stderr.shouldBeEmpty()
        }
        test("options should have correct defaults") {
            cli.test("download https://localhost:56749/defaults")

            download.downloadsDir shouldBe Path.of("./downloads/")
            download.maxDownloadsPerRun.shouldBeNull()
        }
        withData(
            nameFn = { "option --max-downloads-per-run should still be settable (value: ${it.first}" },
            "1" to 1,
            "4" to 4,
            "12" to 12,
            "2147483647" to 2_147_483_647,
        ) { (value, expected) ->
            cli.test(
                listOf("download", "--max-downloads-per-run", value, "https://localhost:65241/max-downloads-test"),
            )
            download.maxDownloadsPerRun shouldBe expected
        }
    }
})
