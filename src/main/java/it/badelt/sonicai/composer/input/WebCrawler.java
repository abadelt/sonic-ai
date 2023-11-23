package it.badelt.sonicai.composer.input;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Service
@Slf4j
public class WebCrawler {

    public interface Callback {
        void visit(String url);
    }

    private Set<String> visitedUrls = new HashSet<>();
    private static final int maxDepth = 5;

    String rootUrl;

    public void crawl(String url, int depth, Callback callback) {
        if (url == null || !url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("url must start with either http:// or https://");
        }
        int hostNameEnd = url.indexOf("/", url.indexOf("://") + 3);
        rootUrl = url.substring(0, hostNameEnd) + "/";
        crawlRecursive(url, depth, callback);
    }

    private void crawlRecursive(String url, int depth, Callback callback) {
        if (depth > maxDepth) {
            return;
        }
        if (url.startsWith(rootUrl) && !visitedUrls.contains(url)) {
            try {
             //   log.debug("Crawling: {}", url);

                callback.visit(url);
                visitedUrls.add(url);

                // Fetch the HTML content from the URL
                Connection connection = Jsoup.connect(url);
                Document document = connection.get();

                // Parse the links in the HTML
                Elements links = document.select("a[href]");

                for (Element link : links) {
                    String nextUrl = link.attr("abs:href");
                    crawlRecursive(nextUrl, depth + 1, callback);
                }

            } catch (IOException e) {
            //    log.error("Error crawling {}: ", url, e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        String startUrl = "https://sonic-pi.mehackit.org/exercises/en/01-introduction/01-introduction.html"; // Replace with your starting URL

        WebCrawler crawler = new WebCrawler();
        Callback cb = new Callback() {
            @Override
            public void visit(String url) {
                System.out.println("Yippieh: " + url);
            }
        };
        crawler.crawl(startUrl, 0, cb);
    }
}

