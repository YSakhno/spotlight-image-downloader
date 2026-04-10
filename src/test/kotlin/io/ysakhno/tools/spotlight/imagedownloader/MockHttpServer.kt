package io.ysakhno.tools.spotlight.imagedownloader

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.fileSize
import kotlin.io.path.readBytes
import org.mockserver.configuration.Configuration
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.Header
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.HttpResponse.response
import org.mockserver.verify.VerificationTimes

/**
 * A mock HTTP server that serves files from a local directory for testing purposes.
 *
 * **Security Considerations:** This class is designed to be used for testing purposes only, where the requests come
 * from a known and trusted source. It does not perform any security checks on the incoming requests (in particular, no
 * path sanitization).
 *
 * @param testFilesDir the directory containing files to be served by the mock server.
 * @author Yurii Sakhno
 */
class MockHttpServer(private val testFilesDir: Path) : AutoCloseable {
    /** The underlying MockServer instance. */
    private val server: ClientAndServer

    init {
        val config = Configuration.configuration().logLevel("WARN")
        server = ClientAndServer.startClientAndServer(config, "127.0.0.1", 0)

        server.`when`(request().withPath("/")).respond { _ ->
            getFileResponse("index.html")
        }

        server.`when`(request()).respond { request ->
            getFileResponse(request.path.value.removePrefix("/").ifEmpty { "index.html" })
        }
    }

    /** The port on which the mock server is listening. */
    val port get() = requireNotNull(server.port) { "Server port is not initialized" }

    /** Reads the file from [testFilesDir] and returns it as an HTTP response. */
    private fun getFileResponse(path: String): HttpResponse {
        val file = testFilesDir.resolve(path)
        return if (file.exists()) {
            response()
                .withStatusCode(200)
                .withHeader(Header("Content-Type", file.mimeType))
                .withHeader(Header("Content-Length", file.fileSize().toString()))
                .withBody(file.readBytes())
        } else {
            response()
                .withStatusCode(404)
                .withHeader(Header("Content-Type", "text/plain"))
                .withBody("Not Found")
        }
    }

    /** Verifies that a request with the specified [path] was made to the mock server the expected number of [times]. */
    fun verify(path: String, times: VerificationTimes) {
        server.verify(request().withPath(path), times)
    }

    /** Stops the mock server and releases all associated resources. */
    override fun close() = server.stop()
}

/** A mapping of some file extensions to their corresponding MIME types. */
private val MIME_TYPES_MAPPING = mapOf(
    "css" to "text/css",
    "htm" to "text/html",
    "html" to "text/html",
    "js" to "application/javascript",
    "gif" to "image/gif",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "png" to "image/png",
    "svg" to "image/svg+xml",
    "txt" to "text/plain",
    "json" to "application/json",
    "xml" to "application/xml",
    "pdf" to "application/pdf",
    "zip" to "application/zip",
)

/**
 * Returns the MIME type for the file extension of this path.
 * If the extension is not recognized, returns `"application/octet-stream"`.
 */
private val Path.mimeType get() = MIME_TYPES_MAPPING[extension] ?: "application/octet-stream"
