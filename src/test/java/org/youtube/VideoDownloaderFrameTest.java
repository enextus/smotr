package org.youtube;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class VideoDownloaderFrameTest {

    @Test
    public void testValidUrl() {
        assertTrue(VideoDownloaderFrame.isValidURL("https://www.google.com"));
        assertTrue(VideoDownloaderFrame.isValidURL("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    }

    @Test
    public void testInvalidUrl() {
        assertFalse(VideoDownloaderFrame.isValidURL("htt://www.google.com")); // missing "p" in "http"
        assertFalse(VideoDownloaderFrame.isValidURL("randomtext"));
    }

    @Test
    public void testDifferentProtocols() {
        assertFalse(VideoDownloaderFrame.isValidURL("ftp://www.example.com")); // FTP is not a supported protocol
    }

    @Test
    public void testDifferentProtocols_others() {
        assertTrue(VideoDownloaderFrame.isValidURL("http://www.example.com"));
        assertFalse(VideoDownloaderFrame.isValidURL("xyz://www.example.com")); // if you expect this to fail
    }

    @Test
    public void testEmptyOrNullUrl() {
        assertFalse(VideoDownloaderFrame.isValidURL(null));
        assertFalse(VideoDownloaderFrame.isValidURL(""));
    }

    @Test
    public void testUrlWithFragmentsAndQueryParameters() {
        assertTrue(VideoDownloaderFrame.isValidURL("https://www.example.com/page?param=value#section"));
    }

    @Test
    public void testUrlWithPort() {
        assertTrue(VideoDownloaderFrame.isValidURL("https://www.example.com:8080/page"));
        assertFalse(VideoDownloaderFrame.isValidURL("http://www.example.com:-1/page")); // invalid port number
    }

    @Test
    public void testUrlWithEscapedCharacters() {
        assertTrue(VideoDownloaderFrame.isValidURL("https://www.example.com/page%20with%20spaces"));
    }

    @Test
    public void testUrlWithPathCharacters() {
        assertTrue(VideoDownloaderFrame.isValidURL("https://www.example.com/valid_path"));
        assertFalse(VideoDownloaderFrame.isValidURL("https://www.example.com/invalid|path")); // invalid character in path
    }

    @Test
    public void testVeryLongUrl() {
        String longUrl = "https://www.example.com/" + "a".repeat(8000);
        assertTrue(VideoDownloaderFrame.isValidURL(longUrl)); // If you expect this to be a valid URL
    }
}
