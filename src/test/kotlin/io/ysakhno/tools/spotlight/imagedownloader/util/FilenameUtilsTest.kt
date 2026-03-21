package io.ysakhno.tools.spotlight.imagedownloader.util

import io.kotest.common.DelicateKotest
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.asString
import io.kotest.property.arbitrary.codepoints
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll

/**
 * Contains unit tests for filename conversions.
 *
 * @author Yurii Sakhno
 */
@OptIn(DelicateKotest::class) // to be able to use distinct()
class FilenameUtilsTest : FunSpec({
    context("Extension function String.toAscii") {
        withData(
            nameFn = { "expected result: ${it.second}" },
            "João" to "Joao",
            "Zoe\u0308" to "Zoe", // U+0308 is a Combining Diaeresis ( ̈)
            "encyclopædia" to "encyclopaedia",
            "Özil" to "Ozil",
            "über" to "uber",
            "yağmur" to "yagmur", // Turkish for "rain"
            "Österreich" to "Osterreich", // Austria
            "Großglockner" to "Grossglockner", // The highest mountain in Austria
            // Georgian words
            "გამარჯობა" to "gamarjoba", // Georgian for "Hello"
            "გენაცვალე" to "genatsvale",
            "თამადა" to "tamada", // Georgian for "toastmaster"
            "ღვინო" to "ghvino", // Georgian for "wine"
            // Greek words
            "Θ" to "Th",
            "θ" to "th",
            "ΘΘ" to "ThTh",
            "Θθ" to "Thth",
            "Γράμμα" to "Gramma", // Greek for "Letter"
            "Θάλασσα" to "Thalassa", // Greek for "Sea"
            "Κόσμος" to "Kosmos", // Greek for "Cosmos"
            "αλήθεια" to "alitheia", // Greek for "truth"
            "θέατρο" to "theatro", // Greek for "theater"
            // Ukrainian words
            "Математика" to "Matematyka", // Ukrainian for "Mathematics"
            "з'єднання" to "z'yednannya", // Ukrainian for "connection"
            "шмат" to "shmat", // Ukrainian for "piece"
            "ящірка" to "yashchirka", // Ukrainian for "lizard"
            "вийшла Галя на ґанок" to "vyyshla Halya na ganok",
            // other uses
            "Жомова яма" to "Zhomova yama", // What Kyva used as a source of income and where he eventually ended up
            "їбало в москаля" to "yibalo v moskalya",
            "эхо сраной маа-асквы" to "ekho sranoy maa-askvy",
            "въёбу-дождь!" to "v\"ebu-dozhd'!",
            // Some weird stuff
            "\u00A0" to " ", // non-breaking space is converted to ordinary space
            "١٢٣" to "123",
            "４５６" to "456",
            "ｉ" to "i",
        ) { (input, expected) ->
            input.toAscii() shouldBe expected
        }

        context("corner cases") {
            withData(
                nameFn = { it.second },
                "" to "empty string",
                "\t" to "TAB character",
                "\n" to "LF character",
                "\r" to "CR character",
                "\r\n" to "CRLF character pair",
                " " to "single space",
                "\u0085" to "next line character",
                "\u2028" to "line separator character",
                "\u2029" to "paragraph separator character",
            ) { (input) ->
                input.toAscii() shouldBe input
            }
        }

        context("emojis") {
            withData(
                nameFn = { "expected: ${it.second}" },
                "🚀" to "rocket",
                "💩" to "pile of poo",
                "❤️" to "heavy black heart",
                "🙂🔥" to "slightly smiling face fire",
                "💙💛" to "blue heart yellow heart",
                "🐒eats🍌" to "monkey eats banana",
                "🐔 lays 🥚" to "chicken lays egg",
                "Hello 🗺️!" to "Hello world map!",
            ) { (input, expected) ->
                input.toAscii() shouldBe expected
            }
        }

        test("should not throw an exception for any codepoint") {
            checkAll(iterations = 10, Arb.list(Arb.codepoints(), range = 1..100)) { codepoints ->
                val str = codepoints.joinToString(separator = "", transform = Codepoint::asString)
                str.toAscii().shouldNotBeNull()
            }
        }
    }

    context("Extension property String.sanitizedName") {
        withData(
            nameFn = { "expected result: ${it.second}" },
            "Simple string" to "Simple_string",
            "Nuño&María" to "Nuno_and_Maria",
            "Sacré-Cœur" to "Sacre_Coeur",
            "Straße" to "Strasse",
            "info@example.net" to "info_at_example_net",
            "It's a 'test'!" to "Its_a_test",
            "  multiple   spaces  " to "multiple_spaces",
            "__leading and trailing__" to "leading_and_trailing",
            "& @ ' \" `" to "and_at",
        ) { (input, expected) ->
            input.sanitizedName shouldBe expected
        }
    }
})
