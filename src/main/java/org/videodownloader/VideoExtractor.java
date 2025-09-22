package org.videodownloader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v136.network.Network; // версия CDP может быть 135/136/137 — оставьте одну
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public class VideoExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(VideoExtractor.class);

    private static ChromeDriver createDriver() {
        ChromeOptions options = new ChromeOptions();
        // современный headless на новых Chrome
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.setAcceptInsecureCerts(true);
        // НИЧЕГО не указываем про webdriver.chrome.driver — Selenium Manager сам подтянет верный драйвер
        ChromeDriver driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(45));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
        return driver;
    }

    public static String extractVideoUrl(String pageUrl) {
        ChromeDriver driver = null;
        DevTools devTools = null;

        try {
            driver = createDriver();
            devTools = driver.getDevTools();
            devTools.createSession();

            // Включаем перехват сети ДО загрузки страниц
            devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));

            final AtomicReference<String> videoUrlRef = new AtomicReference<>(null);

            // Слушаем ответ (надёжнее, чем только запросы). Ловим и mp4, и m3u8
            devTools.addListener(Network.responseReceived(), resp -> {
                String url = resp.getResponse().getUrl();
                if ((url.contains(".mp4") || url.contains(".m3u8")) && !url.contains("remote_control.php")) {
                    LOGGER.info("Captured media URL: {}", url);
                    videoUrlRef.compareAndSet(null, url);
                }
            });

            LOGGER.info("Selenium session started for URL: {}", pageUrl);
            driver.get(pageUrl);

            // Быстрый жадный поиск embed до DevTools-эвентов
            String pageSource = driver.getPageSource();
            Document doc = Jsoup.parse(pageSource);

            String embedUrl = null;
            for (Element element : doc.select("a[href*=/embed/], iframe[src*=/embed/]")) {
                embedUrl = element.hasAttr("href") ? element.attr("href") : element.attr("src");
                if (embedUrl != null && !embedUrl.isEmpty()) {
                    break;
                }
            }

            if (embedUrl == null) {
                LOGGER.warn("No embed URL found on page: {}", pageUrl);
                // Даже без embed попробуем подождать сеть главной страницы (автозапуск плеера)
            } else {
                embedUrl = makeAbsoluteUrl(pageUrl, embedUrl);
                LOGGER.info("Found embed URL: {}", embedUrl);
                driver.get(embedUrl);
            }

            // Страховка: толкнуть видео (если плеер ленится без юзер-жеста)
            try {
                driver.executeScript("""
                  (() => {
                    const v = document.querySelector('video');
                    if (v) { v.muted = true; v.play().catch(()=>{}); }
                  })();
                """);
            } catch (Exception ignore) {}

            // Ожидаем сетевой медиа-URL до 30 сек
            long until = System.currentTimeMillis() + 30_000;
            while (videoUrlRef.get() == null && System.currentTimeMillis() < until) {
                try { Thread.sleep(300); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }

            String media = videoUrlRef.get();
            if (media != null) {
                LOGGER.info("Successfully extracted media URL: {}", media);
                return media;
            } else {
                LOGGER.warn("No media URL captured within timeout for: {}", embedUrl != null ? embedUrl : pageUrl);
                return null;
            }

        } catch (Exception e) {
            LOGGER.error("Error during video URL extraction: {}", e.getMessage(), e);
            return null;
        } finally {
            if (devTools != null) {
                try { devTools.send(Network.disable()); } catch (Exception ignore) {}
            }
            if (driver != null) {
                try { driver.quit(); } catch (Exception ignore) {}
            }
            LOGGER.debug("Selenium session closed");
        }
    }

    private static String makeAbsoluteUrl(String baseUrl, String relativeUrl) {
        try {
            URI base = new URI(baseUrl);
            URI abs = base.resolve(relativeUrl);
            return abs.toString();
        } catch (URISyntaxException e) {
            LOGGER.warn("URL resolve failed: {}", relativeUrl, e);
            return relativeUrl;
        }
    }

}
