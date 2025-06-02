package org.videodownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v136.network.Network;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class VideoExtractor {
    /**
     * Extracts the direct video URL from a webpage by finding an embed URL and intercepting network requests.
     * @param pageUrl The URL of the page containing the video embed.
     * @return The direct video URL (e.g., ending with .mp4/?...) or null if not found.
     */
    public static String extractVideoUrl(String pageUrl) {
        // Set the path to the ChromeDriver executable
        System.setProperty("webdriver.chrome.driver", "C:/opt/chromedriver/chromedriver.exe");

        // Configure Chrome options for headless browsing
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless"); // Run in headless mode (no GUI)

        // Use ChromeDriver explicitly instead of WebDriver interface to access getDevTools()
        ChromeDriver driver = new ChromeDriver(options);

        // Initialize DevTools for network request interception
        DevTools devTools = driver.getDevTools();
        devTools.createSession();

        try {
            // Step 1: Load the main page and find the embed URL
            driver.get(pageUrl);
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);

            // Search for an anchor or iframe tag containing the /embed/ pattern
            String embedUrl = null;
            for (org.jsoup.nodes.Element element : doc.select("a[href*=/embed/], iframe[src*=/embed/]")) {
                embedUrl = element.attr(element.tagName().equals("a") ? "href" : "src");
                if (embedUrl != null && !embedUrl.isEmpty()) {
                    System.out.println("Found embed URL: " + embedUrl);
                    break;
                }
            }

            if (embedUrl == null) {
                System.out.println("No embed URL with pattern /embed/ found on the page");
                return null;
            }

            // Step 2: Open the embed URL in ChromeDriver to trigger video-related network requests
            driver.get(embedUrl);

            // Variable to store the intercepted video URL matching the *.mp4/?* pattern
            AtomicReference<String> videoUrlRef = new AtomicReference<>(null);

            // Enable network request interception
            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
            devTools.addListener(Network.requestWillBeSent(), request -> {
                String requestUrl = request.getRequest().getUrl();
                System.out.println("Intercepted request: " + requestUrl);
                // Check if the request URL matches the pattern *.mp4/?*
                String decodedUrl = URLDecoder.decode(requestUrl, StandardCharsets.UTF_8);
                if (decodedUrl.matches(".*\\.mp4.*")) {
                    System.out.println("Found direct video URL: " + decodedUrl);
                    videoUrlRef.set(requestUrl); // сохраняем оригинальный URL, не декодированный
                }

            });

            // Wait up to 30 seconds for the video URL to be intercepted
            long startTime = System.currentTimeMillis();
            while (videoUrlRef.get() == null && (System.currentTimeMillis() - startTime) < 30000) {
                try {
                    Thread.sleep(500); // Poll every 500ms to check for intercepted URL
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Return the intercepted video URL or null if not found
            String videoUrl = videoUrlRef.get();
            if (videoUrl != null) {
                System.out.println("Successfully extracted video URL: " + videoUrl);
                return videoUrl;
            } else {
                System.out.println("No video URL matching *.mp4/?* pattern found within 30 seconds");
                return null;
            }

        } catch (Exception e) {
            System.out.println("Error during video URL extraction: " + e.getMessage());
            return null;
        } finally {
            // Clean up: disable network interception and close the driver
            devTools.send(Network.disable());
            driver.quit();
        }
    }

    public static void main(String[] args) {
        // Example usage with a test URL
        String url = "https://pornostudentki.com/s-takoi-lubovnicei-lesbiyankoi-studentku-tochno-vigonyat-iz-univera/";
        String videoUrl = extractVideoUrl(url);
        System.out.println("Final extracted video URL: " + videoUrl);
    }

}