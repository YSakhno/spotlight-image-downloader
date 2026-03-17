package io.ysakhno.tools.spotlight.imagedownloader

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.ysakhno.tools.spotlight.imagedownloader.data.DownloadedFileInfo
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
            databaseManager = DatabaseManager(file.absolutePath)
            databaseManager.use(DatabaseManager::putTestData)
        }
    }

    afterSpec {
        dbFile?.delete()
        dbFile = null
    }

    beforeTest {
        /* The beforeTest lifecycle hook is invoked for containers also, which means that for the first
         * nested test in the container it gets invoked *immediately* without invoking afterTest hook first.
         * This leads to the connection not getting properly closed on the old DatabaseManager
         * when a new instance is created, so closing it here explicitly. */
        databaseManager.close()
        databaseManager = DatabaseManager(requireNotNull(dbFile).absolutePath)
            .apply(DatabaseManager::connect)
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
            val preparedInfo = DownloadedFileInfo("hash2", "test.jpg", "Dogs", "Doggy", "Puppy", "pup")
            shouldThrow<SQLException> {
                databaseManager.saveToDatabase(preparedInfo)
            }
        }
    }
})

/** Populates the database with predefined test data. */
private fun DatabaseManager.putTestData() {
    migrateDatabase()
    connect()
    saveToDatabase(
        DownloadedFileInfo(
            "hash123",
            "TestImage.jpg",
            "Tests",
            "Test",
            "Test Image",
            "Test of case-insensitive search",
        ),
    )
    saveToDatabase(DownloadedFileInfo("hash999", "Test.jpg", "Cats", "Cat", "Kitten", ""))
}
