package io.ysakhno.tools.spotlight.imagedownloader

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import java.io.File
import java.sql.SQLException

/**
 * Contains integration tests for the [DatabaseManager] class.
 *
 * @author Yurii Sakhno
 */
class DatabaseManagerTest : FunSpec({

    /**
     * The temporary file used for the test database. This file is automatically deleted at the end of the test run
     * (or at most, when the JVM terminates).
     */
    var dbFile: File? = null

    /**
     * The [DatabaseManager] instance under the test. It is reinitialized for each test, but since it references the
     * persistent file used as a database storage, the content of the database is not reset between the individual
     * tests.
     */
    lateinit var databaseManager: DatabaseManager

    beforeSpec {
        dbFile = File.createTempFile("spotlight_downloader_test", ".db").also { file ->
            file.deleteOnExit()
            DatabaseManager(file).use { mgr ->
                mgr.initDatabase()
                mgr.putTestData()
            }
        }
    }

    afterSpec {
        dbFile?.delete()
        dbFile = null
    }

    beforeTest {
        databaseManager = DatabaseManager(requireNotNull(dbFile) { "Database file is not initialized" })
            .apply(DatabaseManager::initDatabase)
    }

    afterTest {
        databaseManager.close()
    }

    context("Method isFilenameTaken") {
        test("should detect a colliding filename") {
            databaseManager.isFilenameTaken("TestImage.jpg").shouldBeTrue()
        }
        test("should not report a non-colliding filename") {
            databaseManager.isFilenameTaken("TestImage-2.jpg").shouldBeFalse()
        }
        context("should be case-insensitive for standard ASCII") {
            withData(
                nameFn = { it.second },
                "testimage.jpg" to "lower case name",
                "TESTIMAGE.JPG" to "upper case name",
                "tEsTiMaGe.JpG" to "mixed case name",
                "tESTiMAGE.jPG" to "inverted case name",
            ) { (filename) ->
                databaseManager.isFilenameTaken(filename).shouldBeTrue()
            }
        }
    }

    context("Method saveToDatabase") {
        test("should throw an exception when the filename violates the unique case-insensitive index") {
            shouldThrow<SQLException> {
                databaseManager.saveToDatabase("test.jpg", "Dogs", "Puppy", "pup", "hash2")
            }
        }
    }
})

/** Populates the database with predefined test data. */
private fun DatabaseManager.putTestData() {
    saveToDatabase("TestImage.jpg", "Tests", "Test Image", "Test of case-insensitive search", "hash123")
    saveToDatabase("Test.jpg", "Cats", "Kitten", "", "hash999")
}
