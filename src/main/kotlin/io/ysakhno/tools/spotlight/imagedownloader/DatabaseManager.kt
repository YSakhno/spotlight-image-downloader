package io.ysakhno.tools.spotlight.imagedownloader

import io.ysakhno.tools.spotlight.imagedownloader.data.DownloadedFileInfo
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
            .mixed(true)
            .load()

        Logger.getLogger("org.flywaydb.core.FlywayExecutor").level = Level.WARNING
        // Silence Flyway logging if no migrations are pending to avoid noise during application startup
        if (flyway.info().pending().isEmpty()) {
            Logger.getLogger("org.flywaydb").level = Level.OFF
        }

        flyway.migrate()
    }

    /** Retrieves information about a downloaded image by its SHA-256 [hash], or `null` if the record is not found. */
    fun getDownloadInfoByHash(hash: String) = validConnection.prepareStatement(
        """
            SELECT file_hash,
                   original_url,
                   filename,
                   category,
                   image_name,
                   title,
                   description,
                   download_time,
                   last_modified_time
              FROM downloads
             WHERE file_hash = ?
        """.trimIndent(),
    )?.use { stmt ->
        stmt.setString(1, hash)
        stmt.executeQuery().use(::mapResultSetToInfo)
    }

    /** Retrieves information about a downloaded image by its [originalUrl], or `null` if the record is not found. */
    fun getDownloadInfoByOriginalUrl(originalUrl: String) = validConnection.prepareStatement(
        """
            SELECT file_hash,
                   original_url,
                   filename,
                   category,
                   image_name,
                   title,
                   description,
                   download_time,
                   last_modified_time
              FROM downloads
             WHERE original_url = ?
        """.trimIndent(),
    )?.use { stmt ->
        stmt.setString(1, originalUrl)
        stmt.executeQuery().use(::mapResultSetToInfo)
    }

    /** Maps the current row of the provided [ResultSet] to a [DownloadedFileInfo] instance. */
    private fun mapResultSetToInfo(rs: ResultSet) = if (rs.next()) DownloadedFileInfo(
        hash = rs.getString("file_hash"),
        originalUrl = rs.getString("original_url"),
        filename = rs.getString("filename"),
        category = rs.getString("category"),
        imageName = rs.getString("image_name"),
        title = rs.getString("title"),
        description = rs.getString("description"),
        downloadTime = rs.getString("download_time"),
        lastModifiedTime = rs.getString("last_modified_time"),
    ) else null

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

    /** Saves information about a downloaded image file to the database. */
    fun saveToDatabase(info: DownloadedFileInfo) {
        @Suppress("detekt:style:MagicNumber") // SQL parameter indexes do not need constants
        validConnection.prepareStatement(
            """
                INSERT INTO downloads (
                    file_hash, original_url, filename, category, image_name, title, description,
                    download_time, last_modified_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        )?.use { stmt ->
            stmt.setString(1, info.hash)
            stmt.setString(2, info.originalUrl)
            stmt.setString(3, info.filename)
            stmt.setString(4, info.category)
            stmt.setString(5, info.imageName)
            stmt.setString(6, info.title)
            stmt.setString(7, info.description)
            stmt.setString(8, info.downloadTime)
            stmt.setString(9, info.lastModifiedTime)
            stmt.executeUpdate()
        }
    }

    /** Closes the database connection. */
    override fun close() {
        connection?.close()
        connection = null
    }
}
