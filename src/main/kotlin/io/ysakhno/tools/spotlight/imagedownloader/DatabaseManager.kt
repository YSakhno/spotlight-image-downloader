package io.ysakhno.tools.spotlight.imagedownloader

import java.io.File
import java.sql.Connection
import java.sql.DriverManager

/**
 * Manages the SQLite database used to store information about downloaded images.
 *
 * @param dbFile the file where the database is stored.
 * @author Yurii Sakhno
 */
class DatabaseManager(private val dbFile: File) : AutoCloseable {
    private var connection: Connection? = null

    /** Initializes the database and creates the necessary tables if they do not exist. */
    fun initDatabase() {
        if (connection != null) throw IllegalStateException("Database connection already established")
        connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")
        connection?.createStatement()?.use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS downloads (
                    id                  INTEGER     PRIMARY KEY AUTOINCREMENT,
                    filename            TEXT        NOT NULL,
                    category            TEXT        NOT NULL,
                    title               TEXT        NOT NULL,
                    description         TEXT        NOT NULL,
                    file_hash           TEXT        NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    /**
     * Checks if an image with the given hash has already been downloaded.
     *
     * @param hash the SHA-256 hash of the image data.
     * @return `true` if the image is a duplicate, `false` otherwise.
     */
    fun isDuplicate(hash: String): Boolean {
        connection?.prepareStatement("SELECT count(*) FROM downloads WHERE file_hash = ?")?.use { stmt ->
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
     * Retrieves the hash of an image by its filename.
     *
     * @param filename the name of the file to search for.
     * @return the SHA-256 hash of the image if found, or `null` otherwise.
     */
    fun getHashByFilename(filename: String): String? {
        connection?.prepareStatement("SELECT file_hash FROM downloads WHERE filename = ?")?.use { stmt ->
            stmt.setString(1, filename)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getString("file_hash")
                }
            }
        }
        return null
    }

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
        connection?.prepareStatement(
            "INSERT INTO downloads (filename, category, title, description, file_hash) VALUES (?, ?, ?, ?, ?)"
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
