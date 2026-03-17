package io.ysakhno.tools.spotlight.imagedownloader

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.logging.Level
import java.util.logging.Logger
import org.flywaydb.core.Flyway

/**
 * Manages the SQLite database used to store information about downloaded images.
 *
 * @param databaseFilePath path to the file where the database's binary data is stored.
 * @author Yurii Sakhno
 */
class DatabaseManager(databaseFilePath: String) : AutoCloseable {

    /** Stores the URL used to establish a connection to the SQLite database. */
    private val dbUrl = "jdbc:sqlite:$databaseFilePath"

    /**
     * The database connection, or `null` if the connection has not been established yet or has already been closed.
     *
     * This field is initialized to `null` and is set to a valid connection when the [connect] method is called (and
     * successfully returns). It is set back to `null` when the [close] method is called.
     */
    private var connection: Connection? = null

    /** Returns the current database connection or throws an exception if there is no established connection. */
    private val validConnection: Connection get() {
        val conn = connection
        check(conn != null) { "Database connection not established. Call connect() first." }
        return conn
    }

    /**
     * Connects to the database. This method must be called before any database operations can be made.
     *
     * @throws IllegalStateException if the database connection is already established.
     */
    @Throws(IllegalStateException::class)
    fun connect() {
        check(connection == null) { "Database connection already established" }
        connection = DriverManager.getConnection(dbUrl)
    }

    /**
     * Migrates the database schema to the latest version, or in case the database is empty, initializes the schema
     * first. If the database is already up to date, this method does nothing.
     */
    fun migrateDatabase() {
        val flyway = Flyway.configure()
            .dataSource(dbUrl, null, null)
            .load()

        Logger.getLogger("org.flywaydb.core.FlywayExecutor").level = Level.WARNING
        // Silence Flyway logging if no migrations are pending to avoid noise during application startup
        if (flyway.info().pending().isEmpty()) {
            Logger.getLogger("org.flywaydb").level = Level.OFF
        }

        flyway.migrate()
    }

    /**
     * Checks if an image with the given hash has already been downloaded.
     *
     * @param hash the SHA-256 hash of the image data.
     * @return `true` if the image is a duplicate, `false` otherwise.
     */
    fun isDuplicate(hash: String): Boolean {
        validConnection.prepareStatement("SELECT count(*) FROM downloads WHERE file_hash = ?")?.use { stmt ->
            stmt.setString(1, hash)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getInt(1) > 0
                }
            }
        }
        return false
    }

    /**
     * Checks whether a file with the specific [filename] was already saved, and therefore the name is now considered
     * taken (cannot be used for another file).
     *
     * @param filename the filename to check.
     * @return `true` if the filename is taken, `false` otherwise.
     */
    fun isFilenameTaken(filename: String) =
        validConnection.prepareStatement("SELECT 1 FROM downloads WHERE lower(filename) = lower(?)")?.use { stmt ->
            stmt.setString(1, filename)
            stmt.executeQuery().use(ResultSet::next)
        } == true

    /**
     * Saves information about a downloaded image to the database.
     *
     * @param filename the name of the saved file.
     * @param category the name of the category the image belongs to.
     * @param title the title of the image.
     * @param description a description of the image.
     * @param hash the SHA-256 hash of the image data.
     */
    fun saveToDatabase(filename: String, category: String, title: String, description: String, hash: String) {
        @Suppress("detekt:style:MagicNumber") // SQL parameter indexes do not need constants
        validConnection.prepareStatement(
            "INSERT INTO downloads (filename, category, title, description, file_hash) VALUES (?, ?, ?, ?, ?)",
        )?.use { stmt ->
            stmt.setString(1, filename)
            stmt.setString(2, category)
            stmt.setString(3, title)
            stmt.setString(4, description)
            stmt.setString(5, hash)
            stmt.executeUpdate()
        }
    }

    /** Closes the database connection. */
    override fun close() {
        connection?.close()
        connection = null
    }
}
