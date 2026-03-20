package io.ysakhno.tools.spotlight.imagedownloader

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.property.Arb
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.nonNegativeInt
import io.kotest.property.arbitrary.pair
import io.kotest.property.checkAll

/**
 * Contains unit tests for classes and properties from the `ImageFileDownloader.kt` file.
 *
 * @author Yurii Sakhno
 */
class ImageFileDownloaderTest : FunSpec({
    context("Extension Property ByteArray.hash") {
        val byteArrayGenerator = Arb.byteArray(length = Arb.nonNegativeInt(max = 1000), content = Arb.byte())
        val hexFormatter = HexFormat {
            upperCase = true
            bytes {
                byteSeparator = " "
            }
        }

        withData(
            nameFn = { "computes hash for [${it.first.toHexString(hexFormatter)}]" },
            byteArrayOf() to "0:47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU",
            byteArrayOf(0) to "1:bjQLnP+zepicpUTmu3gKLHiQHT+zNzh2hRGjBhevoB0",
            byteArrayOf(1) to "1:S/USLzRFVMU73i67jNK349FgCtYxw4Wl18ziPHeFRZo",
            byteArrayOf(1, 2) to "2:oShx/uIQ+4YZKR6uoZRYHL0lMeSyN1nSJfaAaSP2MiI",
            byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70, 73, 70, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0, -1, -39) to
                "22:0g9v/VI7eKhs0vkW+jSvXRkY1197FCI3x1KtayVCE6s",
            "Hello World!".toByteArray() to "12:f4OxZX/x/FO5LcGBSKHWXfwtSx+j1ncoSt3SABJtkGk",
        ) { (bytes, expected) ->
            bytes.hash shouldBe expected
        }
        test("does not modify the original array") {
            checkAll(iterations = 100, byteArrayGenerator) { data ->
                val copy = data.copyOf()
                data.hash.shouldNotBeEmpty()
                data shouldBe copy
            }
        }
        test("is stable") {
            checkAll(iterations = 100, byteArrayGenerator, byteArrayGenerator) { data1, data2 ->
                val hash = data1.hash
                data2.hash.shouldNotBeEmpty()
                data1.hash shouldBe hash
            }
        }
        test("generates different hashes for different arrays") {
            checkAll(
                iterations = 100,
                Arb.pair(byteArrayGenerator, byteArrayGenerator).filterNot { it.first.contentEquals(it.second) },
            ) { (data1, data2) ->
                val hash1 = data1.hash
                val hash2 = data2.hash
                hash2 shouldNotBe hash1
            }
        }
        test("has 2 parts with the second one being always 43 characters long") {
            checkAll(iterations = 100, byteArrayGenerator) { data ->
                val hash = data.hash
                val (_, sha256) = hash.split(':', limit = 2) shouldHaveSize 2

                sha256 shouldHaveLength 43
            }
        }
    }
})
