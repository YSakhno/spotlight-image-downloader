package io.ysakhno.tools.spotlight.imagedownloader.commands

import com.github.ajalt.clikt.testing.test
import io.kotest.core.spec.IsolationMode.InstancePerLeaf
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import java.nio.file.Path

/**
 * Tests for the command-line arguments and options.
 *
 * @author Yurii Sakhno
 */
class CommandLineTest : FunSpec({

    isolationMode = InstancePerLeaf

    val cli = Root(true)

    context("The root command") {
        test("should have correct default database file") {
            cli.test("")
            cli.dbFile shouldBe File("spotlight_downloader-dev.db")
        }
        test("option --db-file should set custom database file name") {
            cli.test("--db-file=custom.db")
            cli.dbFile shouldBe File("custom.db")
        }
        test("option --version should show version and exit") {
            val result = cli.test("--version")

            result.statusCode shouldBe 0
            result.stdout.normalized() shouldBe "SpotlightDownloader dev-build"
            result.stderr.shouldBeEmpty()
        }
        withData(nameFn = { "option $it should show help and exit" }, "-h", "--help") { option ->
            val result = cli.test(option)

            result.statusCode shouldBe 0
            result.stdout.normalized() shouldBe """
                Usage: spotlight-image-downloader [<options>] <command> [<args>]...

                  A tool to download Spotlight images (semi-)automatically.

                Options:
                  --db-file=<path>  The path to the SQLite database file (default:
                                    spotlight_downloader-dev.db)
                  --version         Show the version and exit
                  -h, --help        Show this message and exit

                Commands:
                  download  Downloads images from the specified initial URL
            """.trimIndent()
            result.stderr.shouldBeEmpty()
        }
    }

    context("Command 'download'") {
        val download = cli.downloadSubCommand

        context("argument <initial-url>") {
            test("argument <initial-url> should set property initialUrl") {
                val result = cli.test("download https://localhost:51229/sample")

                download.initialUrl shouldBe "https://localhost:51229/sample"
                result.statusCode shouldBe 0
                result.stdout.normalized() shouldBe """
                    SpotlightDownloader dev-build by Yurii Sakhno

                    Execution of command 'spotlight-image-downloader > download' has been skipped
                """.trimIndent()
                result.stderr.shouldBeEmpty()
            }
            test("the command should fail if argument is missing") {
                val result = cli.test("download")

                result.statusCode shouldBe 1
                result.stdout.normalized() shouldBe "SpotlightDownloader dev-build by Yurii Sakhno"
                result.output.normalized() shouldBe """
                    SpotlightDownloader dev-build by Yurii Sakhno

                    Usage: spotlight-image-downloader download [<options>] <initial-url>

                    Error: missing argument <initial-url>
                """.trimIndent()
            }
        }
        test("options should have correct defaults") {
            cli.test("download https://localhost:44844/test")

            download.initialUrl shouldBe "https://localhost:44844/test"
            download.downloadsDir shouldBe Path.of("./tmp/downloads/")
            download.isCheckByUrl.shouldBeTrue()
            download.maxDownloadsPerRun shouldBe 1
        }
        context("option --max-downloads-per-run") {
            withData(
                nameFn = { "should set property maxDownloadsPerRun to ${it.second}" },
                "1" to 1,
                "2" to 2,
                "08" to 8,
                "10" to 10,
                "0644" to 644,
                "2147483647" to 2_147_483_647,
            ) { (value, expected) ->
                cli.test(
                    listOf("download", "--max-downloads-per-run", value, "https://localhost:18357/max-downloads-test"),
                )
                download.maxDownloadsPerRun shouldBe expected
            }
            test("should not appear in suggestions") {
                val result = cli.test("download --min-downloads-per-run=2 https://localhost:57515/min-downloads-test")

                download.maxDownloadsPerRun shouldBe 1
                result.statusCode shouldBe 1
                result.stdout.normalized() shouldBe "SpotlightDownloader dev-build by Yurii Sakhno"
                result.output.normalized() shouldBe """
                    SpotlightDownloader dev-build by Yurii Sakhno

                    Usage: spotlight-image-downloader download [<options>] <initial-url>

                    Error: no such option --min-downloads-per-run. Did you mean --downloads-dir?
                """.trimIndent()
            }
            withData(
                nameFn = { "should not accept an invalid integer value '$it'" },
                "2147483648",
                "2147483649",
                "0x2",
                "1.0",
                "three",
                "abc",
            ) { value ->
                val result = cli.test(
                    listOf("download", "--max-downloads-per-run", value, "https://localhost:60264/wrong-value-test"),
                )

                result.statusCode shouldBe 1
                result.stdout.normalized() shouldBe "SpotlightDownloader dev-build by Yurii Sakhno"
                result.output.normalized() shouldBe """
                    SpotlightDownloader dev-build by Yurii Sakhno

                    Usage: spotlight-image-downloader download [<options>] <initial-url>

                    Error: invalid value for --max-downloads-per-run: $value is not a valid integer
                """.trimIndent()
            }
            withData(
                nameFn = { "should not accept value $it smaller than minumum" },
                "0",
                "-1",
                "-2147483647",
                "-2147483648",
            ) { v ->
                val result = cli.test(
                    listOf("download", "--max-downloads-per-run", v, "https://localhost:60264/wrong-value-test"),
                )

                result.statusCode shouldBe 1
                result.stdout.normalized() shouldBe "SpotlightDownloader dev-build by Yurii Sakhno"
                result.output.normalized() shouldBe """
                    SpotlightDownloader dev-build by Yurii Sakhno

                    Usage: spotlight-image-downloader download [<options>] <initial-url>

                    Error: invalid value for --max-downloads-per-run: $v is smaller than the minimum valid value of 1.
                """.trimIndent()
            }
        }
        context("option --downloads-dir") {
            test("should set property downloadsDir") {
                cli.test("download --downloads-dir=custom-path https://localhost:39009/path-test")
                download.downloadsDir shouldBe Path.of("custom-path")
            }
        }
        context("option --check-by-url") {
            test("should set property isCheckByUrl to true") {
                cli.test("download --check-by-url https://localhost:59503/url-check-test")
                download.isCheckByUrl.shouldBeTrue()
            }
            test("option --no-check-by-url should set property isCheckByUrl to false") {
                cli.test("download --no-check-by-url https://localhost:1429/no-url-check-test")
                download.isCheckByUrl.shouldBeFalse()
            }
            test("should reset after option --no-check-by-url") {
                cli.test("download --no-check-by-url --check-by-url https://localhost:49091/double-option-test")
                download.isCheckByUrl.shouldBeTrue()
            }
            test("option --is-check-by-url should give error") {
                val result = cli.test("download --is-check-by-url https://localhost:21315/wrong-option-test")

                result.statusCode shouldBe 1
                result.stdout.normalized() shouldBe "SpotlightDownloader dev-build by Yurii Sakhno"
                result.output.normalized() shouldBe """
                    SpotlightDownloader dev-build by Yurii Sakhno

                    Usage: spotlight-image-downloader download [<options>] <initial-url>

                    Error: no such option --is-check-by-url. (Possible options: --no-check-by-url, --check-by-url)
                """.trimIndent()
            }
        }
        withData(nameFn = { "option $it should show help for the 'download' command" }, "-h", "--help") { option ->
            val result = cli.test(listOf("download", option))

            result.statusCode shouldBe 0
            result.stdout.normalized() shouldBe """
                Usage: spotlight-image-downloader download [<options>] <initial-url>

                  Downloads images from the specified initial URL

                Options:
                  --downloads-dir=<path>  The path to save downloaded files to (default:
                                          ./tmp/downloads/)
                  --check-by-url / --no-check-by-url
                                          Check for duplicate images by their original URL
                                          before downloading them (on by default)
                  -h, --help              Show this message and exit

                Arguments:
                  <initial-url>  The initial URL to start crawling and downloading from
            """.trimIndent()
            result.stderr.shouldBeEmpty()
        }
        test("command should be accessible through the 'dl' alias") {
            val result = cli.test("dl --help")

            result.statusCode shouldBe 0
            result.stdout.normalized() shouldBe """
                Usage: spotlight-image-downloader download [<options>] <initial-url>

                  Downloads images from the specified initial URL

                Options:
                  --downloads-dir=<path>  The path to save downloaded files to (default:
                                          ./tmp/downloads/)
                  --check-by-url / --no-check-by-url
                                          Check for duplicate images by their original URL
                                          before downloading them (on by default)
                  -h, --help              Show this message and exit

                Arguments:
                  <initial-url>  The initial URL to start crawling and downloading from
            """.trimIndent()
            result.stderr.shouldBeEmpty()
        }
    }

    context("Unknown commands") {
        withData(
            nameFn = { "command $it should not exist" },
            "unknown",
            "help",
            "version",
            "crash-and-burn",
            "root", // no actual command with the name 'root', even though technically there is a root command
            cli.commandName, // the root command is not callable by the name from the command line
        ) { command ->
            val result = cli.test(command)

            result.statusCode shouldBe 1
            result.stdout.shouldBeEmpty()
            result.output.normalized() shouldStartWith """
                Usage: spotlight-image-downloader [<options>] <command> [<args>]...

                Error: no such subcommand
            """.trimIndent()
            result.output.normalized() shouldEndWith command
        }
    }
})

/** Normalizes line separators of this string and removes leading and trailing whitespace from the result. */
internal fun String.normalized() = lines().joinToString("\n").trim()
