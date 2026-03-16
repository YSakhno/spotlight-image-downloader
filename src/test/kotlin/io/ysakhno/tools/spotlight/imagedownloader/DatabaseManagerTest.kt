package io.ysakhno.tools.spotlight.imagedownloader

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.sql.SQLException
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contains integration tests for the [DatabaseManager] class.
 *
 * @author Yurii Sakhno
 */
class DatabaseManagerTest {

    /**
     * The [DatabaseManager] instance under the test. It is reinitialized for each test, but since it references the
     * persistent file used as a database storage, the content of the database is not reset between the individual
     * tests.
     */
    private lateinit var databaseManager: DatabaseManager

    @BeforeEach
    fun setUp() {
        databaseManager = DatabaseManager(dbFile).apply(DatabaseManager::initDatabase)
    }

    @AfterEach
    fun tearDown() {
        databaseManager.close()
    }

    @Test
    fun `isFilenameTaken finds colliding filename`() {
        assertTrue(databaseManager.isFilenameTaken("TestImage.jpg"), "Original name should be taken")
    }

    @Test
    fun `isFilenameTaken does not report non-colliding filename`() {
        assertFalse(databaseManager.isFilenameTaken("TestImage-2.jpg"))
    }

    @Test
    fun `isFilenameTaken should be case-insensitive for standard ASCII`() {
        assertTrue(databaseManager.isFilenameTaken("testimage.jpg"), "Lowercase name should be taken")
        assertTrue(databaseManager.isFilenameTaken("TESTIMAGE.JPG"), "Uppercase name should be taken")
        assertTrue(databaseManager.isFilenameTaken("tESTiMAGE.jPG"), "Mixed case name should be taken")
    }

    @Test
    fun `saveToDatabase should throw exception when filename violates unique case-insensitive index`() {
        assertThrows<SQLException> {
            databaseManager.saveToDatabase("test.jpg", "Dogs", "Puppy", "pup", "hash2")
        }
    }

    /**
     * The companion object for the [DatabaseManagerTest] class. Holds lifecycle initialization hooks for the entire set
     * of tests in the test class.
     */
    companion object {
        /**
         * The temporary file used for the test database. This file is automatically deleted at the end of the test run
         * (or at most, when the JVM terminates).
         */
        private lateinit var dbFile: File

        @BeforeAll
        @JvmStatic
        fun before() {
            dbFile = File.createTempFile("spotlight_downloader_test", ".db").apply(File::deleteOnExit)
            DatabaseManager(dbFile).use { mgr ->
                mgr.initDatabase()
                mgr.putTestData()
            }
        }

        @AfterAll
        @JvmStatic
        fun after() {
            if (::dbFile.isInitialized) dbFile.delete()
        }

        private fun DatabaseManager.putTestData() {
            saveToDatabase("TestImage.jpg", "Tests", "Test Image", "Test of case-insensitive search", "hash123")
            saveToDatabase("Test.jpg", "Cats", "Kitten", "", "hash999")
        }
    }
}
