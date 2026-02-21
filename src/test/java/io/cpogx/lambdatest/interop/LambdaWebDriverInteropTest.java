package io.cpogx.lambdatest.interop;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LambdaWebDriverInteropTest {

    @Test
    void lambdaStatusForErrorMapsExpectedValues() {
        assertEquals("passed", LambdaWebDriverInterop.lambdaStatusForError(null));
        assertEquals("passed", LambdaWebDriverInterop.lambdaStatusForError("   "));
        assertEquals("failed", LambdaWebDriverInterop.lambdaStatusForError("assertion failed"));
    }

    @Test
    void extractVideoUrlFindsNestedVideoField() {
        Map<String, Object> payload = Map.of(
                "data", Map.of(
                        "meta", Map.of("session", "abc"),
                        "artifacts", List.of(
                                Map.of("name", "network-log", "url", "https://example.invalid/network.json"),
                                Map.of("video_url", "https://videos.lambdatest.com/session-1.mp4")
                        )
                )
        );

        assertEquals(
                "https://videos.lambdatest.com/session-1.mp4",
                LambdaWebDriverInterop.extractVideoUrl(payload)
        );
    }

    @Test
    void extractVideoUrlReturnsNullWhenMissing() {
        Map<String, Object> payload = Map.of(
                "data", Map.of("name", "session-1", "status", "passed")
        );
        assertNull(LambdaWebDriverInterop.extractVideoUrl(payload));
    }

    @Test
    void extractVideoDownloadUrlPrefersDirectMp4Url() {
        Map<String, Object> payload = Map.of(
                "status", "success",
                "url", "https://user:key@api.lambdatest.com/automation/api/v1/bundler/T/video.mp4",
                "view_video_url", "https://automation.lambdatest.com/public/video?testID=T"
        );
        assertEquals(
                "https://user:key@api.lambdatest.com/automation/api/v1/bundler/T/video.mp4",
                LambdaWebDriverInterop.extractVideoDownloadUrl(payload)
        );
    }

    @Test
    void looksLikeMp4DetectsContainerSignature() {
        byte[] mp4Like = new byte[]{0x00, 0x00, 0x00, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm'};
        byte[] htmlLike = "<!doctype html><html>".getBytes();
        assertTrue(LambdaWebDriverInterop.looksLikeMp4(mp4Like));
        assertFalse(LambdaWebDriverInterop.looksLikeMp4(htmlLike));
    }
}
