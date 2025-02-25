import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import utils.ConcurrentHashSet;
import utils.Options;

import java.util.List;

/**
 * This class counts the number of images in a recursively-defined
 * folder structure using basic Java object-oriented features.  The
 * root folder can either reside locally (filesystem -based) or
 * remotely (web-based).
 */
class ImageCounter {
    /**
     * Debugging tag.
     */
    private final String TAG = this.getClass().getName();

    /**
     * A cache of unique URIs that have already been processed.
     */
    private final ConcurrentHashSet<String> mUniqueUris =
        new ConcurrentHashSet<>();

    /**
     * Constructor counts all the images reachable from the root URI.
     */
    ImageCounter() {
        // Get the URI to the root of the page/folder being traversed.
        var rootUri = Options.instance().getRootUri();

        // Perform the image counting starting at the root Uri, which
        // is given an initial depth count of 1.
        int totalImages = countImages(rootUri, 1);

        print(TAG + ": " + totalImages
              + " total image(s) are reachable from "
              + rootUri);
    }

    /**
     * Main entry point into the logic for counting images
     * synchronously.
     *
     * @param pageUri The URL that we're counting at this point
     * @param depth The current depth of the recursive processing
     * @return The number of images counted at this {@code depth}
     */
    private int countImages(String pageUri,
                            int depth) {
        // Return 0 if we've reached the depth limit of the crawling.
        if (depth > Options.instance().maxDepth()) {
            print(TAG 
                  + "[Depth"
                  + depth
                  + "]: Exceeded max depth of "
                  + Options.instance().maxDepth());

            return 0;
        }

        // Atomically check to see if we've already visited this URL
        // and add the new url to the hashset, so we don't try to
        // revisit it again unnecessarily.
        else if (!mUniqueUris.putIfAbsent(pageUri)) {
            print(TAG 
                  + "[Depth"
                  + depth
                  + "]: Already processed "
                  + pageUri);

            // Return 0 if we've already examined this url.
            return 0;
        }

        // Synchronously (1) count the number of images on this page
        // and (2) crawl other hyperlinks accessible via this page and
        // count their images.
        else { 
            int count = countImagesImpl(pageUri,
                                       depth);
            print(TAG
                  + "[Depth"
                  + depth
                  + "]: found "
                  + count
                  + " images for "
                  + pageUri
                  + " in thread " 
                  + Thread.currentThread().getId());
            return count;
        }
    }

    /**
     * Helper method that performs image counting synchronously.
     *
     * @param pageUri The URL that we're counting at this point
     * @param depth The current depth of the recursive processing
     * @return The number of images counted
     */
    private int countImagesImpl(String pageUri,
                                 int depth) {
        try {
            // Get the page at the root URI.
            Document page = getStartPage(pageUri);

            // Synchronously count the # of images on this page.
            int imagesInPage = getImagesInPage(page).size();

            // Synchronously count the # of images in link on this
            // page and returns this count.
            int imagesInLinks = crawlLinksInPage(page, depth);

            // Return a count of the # of images on this page plus the
            // # of images on hyperlinks accessible via this page.
            return imagesInPage + imagesInLinks;
        } catch (Exception e) {
            print("For '" 
                  + pageUri 
                  + "': " 
                  + e.getMessage());
            // Return 0 if an exception happens.
            return 0;
        }
    }

    /**
     * @return The page at the root {@code pageUri}
     */
    private Document getStartPage(String pageUri) {
        // Synchronously get the contents of the page.
        return Options
            .instance()
            .getJSuper()
            .getPage(pageUri);
    }

    /**
     * @return A collection of IMG SRC URLs in this page
     */
    private Elements getImagesInPage(Document page) {
        // Return a collection of IMG SRC URLs in this page.
        return page.select("img");
    }

    /**
     * Recursively crawl through hyperlinks that are in {@code page}.
     *
     * @param page The page containing HTML
     * @param depth The depth of the level of web page traversal
     * @return A count of how many images were in each hyperlink on
     *         the page
     */
    private int crawlLinksInPage(Document page,
                                 int depth) {
        int imageCount = 0;

        // Return a count of the # of nested hyperlinks in the page.
        for (var hyperLink : page // Find all the hyperlinks on this page.
                 .select("a[href]"))
            // Count of the number of images found at that hyperlink
            // by recursively visiting all hyperlinks on this page.
            imageCount += countImages(Options
                                      .instance()
                                      .getJSuper()
                                      .getHyperLink(hyperLink),
                                      depth + 1);
        // Return a count of the number of images reachable
        // from this page.
        return imageCount;
    }

    /**
     * Conditionally prints the {@link String} depending on the current
     * setting of the Options singleton.
     */
    private void print(String string) {
        if (Options.instance().getDiagnosticsEnabled())
            System.out.println(string);
    }
}
