package io.cpogx.lambdatest.interop;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.JavascriptExecutor;

import java.util.Arrays;
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

    @Test
    @SuppressWarnings("unchecked")
    void interceptResponseSendsNormalizedPayload() {
        RecordingJavascriptExecutor executor = new RecordingJavascriptExecutor();

        Map<String, Object> rule = Map.of(
                "url", "https://example.test/api/todos",
                "method", "GET",
                "response", Map.of(
                        "status", 200,
                        "headers", Map.of("Content-Type", "application/json"),
                        "body", Map.of("items", List.of("a", "b"))
                )
        );

        Object ack = LambdaWebDriverInterop.intercept(executor, rule);
        assertEquals("ack", ack);
        assertEquals("lt:intercept:response", executor.lastScript);
        assertTrue(executor.lastArgs instanceof Object[]);

        Object[] args = (Object[]) executor.lastArgs;
        assertEquals(1, args.length);
        assertTrue(args[0] instanceof Map<?, ?>);

        Map<String, Object> payload = (Map<String, Object>) args[0];
        assertEquals("https://example.test/api/todos", payload.get("url"));
        assertEquals("GET", payload.get("method"));
        assertTrue(payload.get("response") instanceof Map<?, ?>);

        Map<String, Object> response = (Map<String, Object>) payload.get("response");
        assertEquals(200, response.get("status"));
        assertEquals(Map.of("Content-Type", "application/json"), response.get("headers"));
        assertEquals(Map.of("items", List.of("a", "b")), response.get("body"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void interceptResponseLeavesStringPayloadUnchanged() {
        RecordingJavascriptExecutor executor = new RecordingJavascriptExecutor();

        Map<String, Object> rule = Map.of(
                "url", "https://example.test/app.js",
                "response", Map.of(
                        "status", 200,
                        "headers", Map.of("Content-Type", "application/javascript"),
                        "body", "console.log(\"mocked\")"
                )
        );

        LambdaWebDriverInterop.intercept(executor, rule);
        Object[] args = (Object[]) executor.lastArgs;
        Map<String, Object> payload = (Map<String, Object>) args[0];
        Map<String, Object> response = (Map<String, Object>) payload.get("response");
        assertEquals("console.log(\"mocked\")", response.get("body"));
    }

    private static final class RecordingJavascriptExecutor implements JavascriptExecutor {
        private String lastScript;
        private Object lastArgs;

        @Override
        public Object executeScript(String script, Object... args) {
            this.lastScript = script;
            this.lastArgs = Arrays.copyOf(args, args.length);
            return "ack";
        }

        @Override
        public Object executeAsyncScript(String script, Object... args) {
            throw new UnsupportedOperationException("not used");
        }
    }
}
