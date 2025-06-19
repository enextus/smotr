package org.videodownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v136.network.Network;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Класс для извлечения URL видео из веб-страниц.
 * Использует Jsoup для парсинга HTML и Selenium для обработки динамического контента.
 */
public class VideoExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoExtractor.class);
    private static final String CHROMEDRIVER_PATH = "C:/opt/chromedriver/chromedriver.exe";

    /**
     * Извлекает URL видео из указанной страницы.
     *
     * @param pageUrl URL страницы
     * @return URL видео или null, если не найдено
     */
    public static String extractVideoUrl(String pageUrl) {
        System.setProperty("webdriver.chrome.driver", CHROMEDRIVER_PATH);
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        ChromeDriver driver = null;
        DevTools devTools = null;

        try {
            driver = new ChromeDriver(options);
            devTools = driver.getDevTools();
            devTools.createSession();
            LOGGER.info("Selenium session started for URL: {}", pageUrl);

            // Загружаем страницу
            driver.get(pageUrl);
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);

            // Ищем embed URL
            String embedUrl = null;
            for (Element element : doc.select("a[href*=/embed/], iframe[src*=/embed/]")) {
                embedUrl = element.attr(element.tagName().equals("a") ? "href" : "src");
                if (embedUrl != null && !embedUrl.isEmpty()) {
                    LOGGER.info("Found embed URL: {}", embedUrl);
                    break;
                }
            }

            if (embedUrl == null) {
                LOGGER.warn("No embed URL found on page: {}", pageUrl);
                return null;
            }

            // Преобразуем относительный URL в абсолютный
            embedUrl = makeAbsoluteUrl(pageUrl, embedUrl);
            driver.get(embedUrl);

            // Настраиваем перехват сетевых запросов
            AtomicReference<String> videoUrlRef = new AtomicReference<>(null);
            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
            devTools.addListener(Network.requestWillBeSent(), request -> {
                String requestUrl = request.getRequest().getUrl();
                LOGGER.debug("Intercepted request: {}", requestUrl);
                if (requestUrl.contains(".mp4") && requestUrl.contains("?") && !requestUrl.contains("remote_control.php")) {
                    LOGGER.info("Found direct video URL: {}", requestUrl);
                    videoUrlRef.set(requestUrl);
                }
            });

            // Ждём до 30 секунд для получения URL видео
            long startTime = System.currentTimeMillis();
            while (videoUrlRef.get() == null && (System.currentTimeMillis() - startTime) < 30_000) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOGGER.warn("Interrupted while waiting for video URL", e);
                    return null;
                }
            }

            String videoUrl = videoUrlRef.get();
            if (videoUrl != null) {
                LOGGER.info("Successfully extracted video URL: {}", videoUrl);
                return videoUrl;
            } else {
                LOGGER.warn("No video URL found within 30 seconds for: {}", embedUrl);
                return null;
            }

        } catch (Exception e) {
            LOGGER.error("Error during video URL extraction: {}", e.getMessage(), e);
            return null;
        } finally {
            if (devTools != null) {
                try {
                    devTools.send(Network.disable());
                } catch (Exception e) {
                    LOGGER.warn("Error disabling DevTools", e);
                }
            }
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    LOGGER.warn("Error closing WebDriver", e);
                }
            }
            LOGGER.debug("Selenium session closed");
        }
    }

    /**
     * Преобразует относительный URL в абсолютный.
     *
     * @param baseUrl     базовый URL
     * @param relativeUrl относительный URL
     * @return абсолютный URL
     */
    private static String makeAbsoluteUrl(String baseUrl, String relativeUrl) {
        try {
            URI baseUri = new URI(baseUrl);
            URI absoluteUri = baseUri.resolve(relativeUrl);
            LOGGER.debug("Converted URL: {} -> {}", relativeUrl, absoluteUri);
            return absoluteUri.toString();
        } catch (URISyntaxException e) {
            LOGGER.warn("Error converting URL: {}", relativeUrl, e);
            return relativeUrl;
        }
    }

}
