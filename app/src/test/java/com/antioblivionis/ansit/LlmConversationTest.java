package com.antioblivionis.ansit;

import java.util.ArrayList;
import java.util.List;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LlmConversationTest {
    private static final String KEY = "test-only-credential";
    private static List<JSONObject> history() throws Exception {
        List<JSONObject> history = new ArrayList<>();
        history.add(LlmCall.imageMessage("previous-image", "上一题"));
        history.add(LlmCall.assistantMessage("上一题答案"));
        return history;
    }
    @Test public void continuousTurnCarriesPriorScreenshotsAndAnswers() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"下一题答案\"}}]}"));
            server.start();
            ApiConfig config = new ApiConfig(server.url("/v1").toString(), KEY, "vision");
            assertEquals("下一题答案", new LlmCall().answerImage(config, "current-image", null, history()));
            JSONObject request = new JSONObject(server.takeRequest().getBody().readUtf8());
            JSONArray messages = request.getJSONArray("messages");
            assertEquals(4, messages.length());
            assertEquals("system", messages.getJSONObject(0).getString("role"));
            assertTrue(messages.getJSONObject(0).getString("content").contains("连续答题会话"));
            assertEquals("user", messages.getJSONObject(1).getString("role"));
            assertEquals("上一题答案", messages.getJSONObject(2).getString("content"));
            assertEquals("user", messages.getJSONObject(3).getString("role"));
            String image = messages.getJSONObject(3).getJSONArray("content").getJSONObject(1)
                .getJSONObject("image_url").getString("url");
            assertEquals("data:image/png;base64,current-image", image);
            assertTrue(messages.getJSONObject(3).getJSONArray("content").getJSONObject(0)
                .getString("text").contains("下一题"));
        }
    }
    @Test public void emptyHistoryKeepsTheIndependentConversationShape() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"新答案\"}}]}"));
            server.start();
            ApiConfig config = new ApiConfig(server.url("/v1").toString(), KEY, "vision");
            assertEquals("新答案", new LlmCall().answerImage(config, "frame", null, java.util.Collections.emptyList()));
            JSONArray messages = new JSONObject(server.takeRequest().getBody().readUtf8()).getJSONArray("messages");
            assertEquals(2, messages.length());
            assertFalse(messages.getJSONObject(0).getString("content").contains("连续答题会话"));
        }
    }
    @Test public void providerContextLimitBecomesContextFull() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(400).setBody(
                "{\"error\":{\"message\":\"This model's maximum context length is 8192 tokens\"}}"));
            server.start();
            ApiConfig config = new ApiConfig(server.url("/v1").toString(), KEY, "vision");
            LlmCall.ContextFull full = assertThrows(LlmCall.ContextFull.class,
                () -> new LlmCall().answerImage(config, "current", null, history()));
            assertEquals("上下文已满", full.getMessage());
        }
    }
    @Test public void payloadTooLargeAlsoBecomesContextFull() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(413).setBody("payload too large"));
            server.start();
            ApiConfig config = new ApiConfig(server.url("/v1").toString(), KEY, "vision");
            assertThrows(LlmCall.ContextFull.class,
                () -> new LlmCall().answerImage(config, "current", null, history()));
        }
    }
    @Test public void unrelatedModelErrorStaysAGenericFailure() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\":{\"message\":\"invalid image format\"}}"));
            server.start();
            ApiConfig config = new ApiConfig(server.url("/v1").toString(), KEY, "vision");
            LlmCall.Failure failure = assertThrows(LlmCall.Failure.class,
                () -> new LlmCall().answerImage(config, "current", null, history()));
            assertFalse(failure instanceof LlmCall.ContextFull);
        }
    }
}
