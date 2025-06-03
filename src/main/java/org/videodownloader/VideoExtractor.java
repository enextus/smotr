package org.videodownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v136.network.Network;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class VideoExtractor {
    public static String extractVideoUrl(String pageUrl) {
        System.setProperty("webdriver.chrome.driver", "C:/opt/chromedriver/chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");

        ChromeDriver driver = new ChromeDriver(options);
        DevTools devTools = driver.getDevTools();
        devTools.createSession();

        try {
            driver.get(pageUrl);
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);

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

            driver.get(embedUrl);

            AtomicReference<String> videoUrlRef = new AtomicReference<>(null);

            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
            devTools.addListener(Network.requestWillBeSent(), request -> {
                String requestUrl = request.getRequest().getUrl();
                System.out.println("Intercepted request: " + requestUrl);
                if (requestUrl.contains(".mp4") && requestUrl.contains("?") && !requestUrl.contains("remote_control.php")) {
                    System.out.println("Found direct video URL: " + requestUrl);
                    videoUrlRef.set(requestUrl);
                }
            });

            long startTime = System.currentTimeMillis();
            while (videoUrlRef.get() == null && (System.currentTimeMillis() - startTime) < 30000) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            String videoUrl = videoUrlRef.get();
            if (videoUrl != null) {
                System.out.println("Successfully extracted video URL: " + videoUrl);
                return videoUrl;
            } else {
                System.out.println("No video URL matching the pattern found within 30 seconds");
                return null;
            }

        } catch (Exception e) {
            System.out.println("Error during video URL extraction: " + e.getMessage());
            return null;
        } finally {
            devTools.send(Network.disable());
            driver.quit();
        }
    }

    public static void main(String[] args) {
        String url = "https://smotretporno.online/julia-rain-in-flagrante-delicto-29-05-2022-720p-mp4/";
        String videoUrl = extractVideoUrl(url);
        System.out.println("Final extracted video URL: " + videoUrl);
    }
}