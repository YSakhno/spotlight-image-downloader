package io.ysakhno.tools.spotlight.imagedownloader.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.versionOption
import com.github.ajalt.clikt.parameters.types.file
import io.ysakhno.tools.spotlight.imagedownloader.AppVersion
import io.ysakhno.tools.spotlight.imagedownloader.DatabaseManager
import java.io.File

/**
 * The root command for the Spotlight image downloader application.
 *
 * @property isSkipRun A flag to skip the actual application logic execution when the flag is `true`.
 * @constructor Constructs a new instance of the [Root] command class, while allowing to set the flag to skip the run
 * logic in subcommands.
 * @author Yurii Sakhno
 */
class Root internal constructor(internal val isSkipRun: Boolean) : CliktCommand(name = "spotlight-image-downloader") {

    /**
     * The default file name used for the SQLite database.
     *
     * The file name dynamically changes based on the application's runtime environment:
     * - normally, the default file name is `spotlight_downloader.db`;
     * - but if the application is running in development mode, the file name will include the suffix `-dev` (e.g.,
     * `spotlight_downloader-dev.db`).
     *
     * This value serves as the fallback for the database file path if none is provided via command-line options.
     */
    @Suppress("ktlint:standard:property-naming", "detekt:naming:VariableNaming") // this is effectively a constant
    private val DEFAULT_DB_FILE_NAME = "spotlight_downloader${if (AppVersion.isDevelopment) "-dev" else ""}.db"

    /** The command line option to specify the path to the SQLite database file. */
    internal val dbFile by option()
        .file(mustExist = false, canBeDir = false, canBeFile = true, canBeSymlink = true)
        .default(File(DEFAULT_DB_FILE_NAME))
        .help("The path to the SQLite database file (default: $DEFAULT_DB_FILE_NAME)")

    /** Accessor property for the 'download' sub-command to be used in tests. */
    internal val downloadSubCommand = Download()

    init {
        versionOption(version = AppVersion.version, message = { AppVersion.nameAndVersion })
        subcommands(downloadSubCommand)
    }

    /** Constructs and initializes a new instance of the [Root] command class. */
    constructor() : this(isSkipRun = false)

    /** Defines aliases for the application's commands. */
    override fun aliases() = mapOf("dl" to listOf("download"))

    /** Provides a short description of the entire application. */
    override fun help(context: Context) = "A tool to download Spotlight images (semi-)automatically."

    /** Executes the application's main logic. */
    override fun run() {
        echo(AppVersion.fullName)
        echo()

        currentContext.findOrSetObject(DatabaseManager::class.java.name) { DatabaseManager(dbFile.absolutePath) }
    }
}
