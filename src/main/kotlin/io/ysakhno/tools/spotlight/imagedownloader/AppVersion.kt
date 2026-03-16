package io.ysakhno.tools.spotlight.imagedownloader

/**
 * Utility class to retrieve and display the version of the application, along with some other meta-information.
 *
 * @author Yurii Sakhno
 */
object AppVersion {

    /** Name of the author of the application. */
    const val AUTHOR = "Yurii Sakhno"

    /** Code name of the application used in development. */
    private const val APP_CODE_NAME = "SpotlightDownloader"

    /** Specifies version to output when run not as an uber-JAR. */
    private const val DEVELOPMENT_VERSION = "dev-build"

    /** Stores reflection view of the package of the current class. */
    private val currentPackage = javaClass.`package`!!

    /** Determines whether the application is run as an Uber-JAR (production mode) or from the IDE (development). */
    val isDevelopment = currentPackage.implementationVersion == null

    /** The title (name) of this application. */
    val title get() = currentPackage.implementationTitle ?: APP_CODE_NAME

    /** The version of the application as a textual string. */
    val version get() = if (isDevelopment) DEVELOPMENT_VERSION else "v${currentPackage.implementationVersion}"

    /** Returns name of the application and its version. */
    val nameAndVersion get() = "$title $version"

    /** Returns full name of the application, including version and authorship. */
    val fullName get() = "$nameAndVersion by $AUTHOR"
}
