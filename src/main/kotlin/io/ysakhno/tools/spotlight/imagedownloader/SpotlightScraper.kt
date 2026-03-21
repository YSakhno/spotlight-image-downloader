package io.ysakhno.tools.spotlight.imagedownloader

import org.jsoup.Connection
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** A regex used to extract the URL from CSS `url()` syntax. */
private val CSS_URL_REGEX = """url\((?<url>.*?)\)""".toRegex()

/**
 * Scrapes the Spotlight website to discover and process image categories and individual images.
 *
 * @param processingStats tracks the statistics of the download process.
 * @param httpSession the session to use for HTTP requests.
 * @param downloader handles the downloading of images.
 */
class SpotlightScraper(
    private val processingStats: ProcessingStats,
    private val httpSession: Connection,
    private val downloader: ImageFileDownloader,
) {
    /**
     * Scrapes the Spotlight website starting from the provided URL. It determines if the URL is an initial page
     * or a category page, and proceeds accordingly to find and process all image categories.
     *
     * @param url the URL to start scraping from.
     */
    @Suppress("detekt:potential-bugs:UnreachableCode") // seems to be false positive
    fun processUrl(url: String) {
        var doc = runCatching { httpSession.newRequest().url(url).get() }
            .onFailure { throwable ->
                processingStats.reportError("Could not process initial URL $url: ${throwable.message}")
            }
            .getOrNull() ?: return

        // Language-independent detection: Category page contains image cards; Initial page contains category pills
        val isCategoryPage = doc.select("div.btsl-image-card").isNotEmpty()

        if (isCategoryPage) {
            print("Detected a Category page, navigating to initial page...")
            // The link to the initial page is usually nested in a div with one of these classes
            val initialPageUrl = doc.selectFirst("div.main-pill a, div.show-img-car a, div.cat-nav a")?.absUrl("href")

            if (initialPageUrl == null) {
                println(" ERROR")
                processingStats.reportError("Provided URL does not seem to point to a Spotlight page")
                return
            }

            doc = runCatching { httpSession.newRequest().url(initialPageUrl).get() }
                .onFailure { throwable ->
                    println(" ERROR")
                    processingStats.reportError(
                        @Suppress("detekt:potential-bugs:NullableToStringCall") // seems to be a bug in Detekt
                        "Could not navigate to initial page $initialPageUrl: ${throwable.message}",
                    )
                }
                .onSuccess { println(" DONE") }
                .getOrNull() ?: return
        }

        if (doc.select("div.slide a").isEmpty()) {
            processingStats.reportError("Provided URL does not seem to point to a Spotlight page")
        } else {
            processInitialPage(doc)
        }
    }

    /**
     * Processes the initial page of the website to find all image categories, and then goes on to downloading the
     * category pages, finally moving on to actually downloading individual images.
     *
     * @param doc the document representing the initial page.
     */
    fun processInitialPage(doc: Document) {
        val categoryLinks = doc.select("div.slide a")
        println("Processing images in ${categoryLinks.size} categories")

        for (element in categoryLinks) {
            val categoryUrl = element.absUrl("href")
            val categoryName = element.getTextBy("div.text")

            if (categoryUrl.isNotBlank() && categoryName != null) {
                processCategoryPage(categoryUrl, categoryName)
            }
        }
    }

    /**
     * Processes a category page to find all images within that category, and then goes on to actually downloading
     * the individual images.
     *
     * @param url the URL of the category page.
     * @param categoryName the name of the category being processed.
     */
    fun processCategoryPage(url: String, categoryName: String) {
        if (processingStats.isDownloadsLimitReached) return

        runCatching {
            print("Category $categoryName...")
            httpSession.newRequest().url(url).get()
        }.map { doc ->
            doc.select("div.slide a")
        }.onSuccess { elements ->
            println(" found ${elements.size} images")
        }.onFailure { throwable ->
            println(" ERROR")
            processingStats.reportError("Could not process category page $url: ${throwable.message}")
        }.map { imageLinks ->
            for (element in imageLinks) {
                val imagePageUrl = element.absUrl("href")
                val imageName = element.getTextBy("div.text")

                if (imagePageUrl.isNotBlank() && imageName != null) {
                    processImagePage(imagePageUrl, categoryName, imageName)
                }
            }
        }.getOrDefault(Unit)
    }

    /**
     * Processes an individual image page to extract image metadata and download the corresponding image.
     *
     * @param url the URL of the image page.
     * @param categoryName the name of the category the image belongs to.
     * @param imageName the base name for the image file.
     */
    @Suppress(
        "detekt:potential-bugs:UnreachableCode", // seems to be false positive
        "detekt:style:ThrowsCount", // guard clauses should be perfectly fine, yet Detekt seems to have a bug
    )
    fun processImagePage(url: String, categoryName: String, imageName: String) {
        if (processingStats.isDownloadsLimitReached) return

        runCatching {
            print("  Downloading image $imageName...")
            httpSession.newRequest().url(url).get()
        }.mapCatching { doc ->
            val style = doc.selectFirst("div#bcg-img-url")?.attr("style")
                ?: throw ScrapingException("Could not find image div")
            val relativeImgUrl = CSS_URL_REGEX.find(style)?.groups?.get("url")?.value
                ?: throw ScrapingException("Could not find image URL in style")

            val tempElement = doc.createElement("a").attr("href", relativeImgUrl)
            val imgUrl = tempElement.absUrl("href")

            if (imgUrl.isBlank()) throw ScrapingException("Could not resolve image URL from: $relativeImgUrl")

            val title = doc.getTextBy("div#heading-url") ?: throw ScrapingException("Could not find title div")
            val description = doc.getTextBy("span.btsl-description").orEmpty()

            when (
                val result = downloader.downloadImage(
                    imgUrl,
                    relativeImgUrl,
                    categoryName,
                    imageName,
                    title,
                    description,
                )
            ) {
                is DownloadResult.Saved -> println(" SUCCESS (saved as ${result.info.filename})")

                is DownloadResult.Duplicate ->
                    println(" SKIPPED (duplicate of ${result.info.imageName} in ${result.info.category})")
            }
        }.onFailure { throwable ->
            println(" ERROR")
            processingStats.reportError("Could not process image page $url: ${throwable.message}")
        }
    }
}

/**
 * Retrieves and trims the text content of the first child element that matches the provided CSS selector.
 *
 * @param selector a CSS selector used to locate the desired child element.
 * @return the trimmed textual content of the first matching element, or `null` if no match is found.
 */
private fun Element.getTextBy(selector: String) = selectFirst(selector)?.text()?.trim()
