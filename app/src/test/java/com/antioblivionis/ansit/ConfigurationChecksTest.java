package com.antioblivionis.ansit;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.CancellationException;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class ConfigurationChecksTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private ConfigurationChecks checks(String search) {
        return new ConfigurationChecks(() -> new VisionProbe("728391", "probe-only-image"), search,
            (file, value) -> Files.write(file.toPath(), value.toString().getBytes(StandardCharsets.UTF_8)));
    }
    private File oldConfig() throws Exception {
        File file = temporary.newFile(); Files.write(file.toPath(), "old-settings".getBytes(StandardCharsets.UTF_8)); return file;
    }
    private String contents(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8); }
    private String probeTool() throws Exception {
        return LlmSearchTest.envelope(new JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
            .put("reasoning_content", "probe-metadata")
            .put("tool_calls", new JSONArray().put(new JSONObject().put("id", "probe").put("type", "function")
                .put("function", new JSONObject().put("name", "ansit_confirm_vision")
                    .put("arguments", new JSONObject().put("code", "728391").toString())))));
    }
    @Test public void correctImageChallengeIsPublishedOnlyAfterRealRequest() throws Exception {
        try (MockWebServer model = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(LlmSearchTest.reply("728391"))); model.start();
            File file = oldConfig(); ApiConfig config = new ApiConfig(model.url("/v1").toString(), "candidate-model-key", "vision");
            checks("https://unused.invalid").saveModel(new LlmCall(), config, false, file);
            assertEquals("candidate-model-key", new JSONObject(contents(file)).getString("key"));
            JSONObject request = new JSONObject(model.takeRequest().getBody().readUtf8());
            JSONArray parts = request.getJSONArray("messages").getJSONObject(0).getJSONArray("content");
            assertFalse(parts.getJSONObject(0).getString("text").contains("728391"));
            assertEquals("data:image/png;base64,probe-only-image", parts.getJSONObject(1).getJSONObject("image_url").getString("url"));
        }
    }
    @Test public void wrongVisionAnswerDoesNotReplaceSavedConfiguration() throws Exception {
        try (MockWebServer model = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(LlmSearchTest.reply("000000"))); model.start();
            File file = oldConfig();
            assertThrows(LlmCall.Failure.class, () -> checks("https://unused.invalid").saveModel(new LlmCall(),
                new ApiConfig(model.url("/v1").toString(), "new-key", "vision"), false, file));
            assertEquals("old-settings", contents(file));
        }
    }
    @Test public void authenticationFailureIsSanitizedAndLeavesSavedConfigurationIntact() throws Exception {
        try (MockWebServer model = new MockWebServer()) {
            model.enqueue(new MockResponse().setResponseCode(401).setBody("new-model-secret")); model.start();
            File file = oldConfig();
            LlmCall.Failure error = assertThrows(LlmCall.Failure.class, () -> checks("https://unused.invalid").saveModel(new LlmCall(),
                new ApiConfig(model.url("/v1").toString(), "new-model-secret", "vision"), false, file));
            assertFalse(error.getMessage().contains("new-model-secret")); assertEquals("old-settings", contents(file));
        }
    }
    @Test public void enabledSearchRequiresModelToDemonstrateImageAndFunctionCalling() throws Exception {
        try (MockWebServer model = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(probeTool()));
            model.enqueue(new MockResponse().setBody(LlmSearchTest.reply("728391"))); model.start();
            File file = oldConfig();
            checks("https://unused.invalid").saveModel(new LlmCall(),
                new ApiConfig(model.url("/v1").toString(), "new-key", "vision"), true, file);
            JSONObject request = new JSONObject(model.takeRequest().getBody().readUtf8());
            assertEquals("auto", request.getString("tool_choice"));
            assertEquals("ansit_confirm_vision", request.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name"));
            assertEquals("vision", new JSONObject(contents(file)).getString("model"));
            JSONObject continued = new JSONObject(model.takeRequest().getBody().readUtf8());
            assertEquals("none", continued.getString("tool_choice"));
            assertEquals("probe-metadata", continued.getJSONArray("messages").getJSONObject(1).getString("reasoning_content"));
            assertEquals("probe", continued.getJSONArray("messages").getJSONObject(2).getString("tool_call_id"));
        }
    }
    @Test public void plainTextImageReplyCannotPassRequiredToolCheck() throws Exception {
        try (MockWebServer model = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(LlmSearchTest.reply("728391"))); model.start();
            File file = oldConfig();
            assertThrows(LlmCall.Failure.class, () -> checks("https://unused.invalid").saveModel(new LlmCall(),
                new ApiConfig(model.url("/v1").toString(), "new-key", "vision"), true, file));
            assertEquals("old-settings", contents(file));
        }
    }
    @Test public void successfulSearchAndModelToolProbePublishSeparateSearchKey() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            search.enqueue(LlmSearchTest.results()); model.enqueue(new MockResponse().setBody(probeTool()));
            model.enqueue(new MockResponse().setBody(LlmSearchTest.reply("728391")));
            model.start(); search.start();
            File file = oldConfig();
            checks(search.url("/v1/tools/search").toString()).saveSearch(new LlmCall(), new SearchConfig(true, "new-search-key"),
                new ApiConfig(model.url("/v1").toString(), "model-key", "vision"), file);
            JSONObject saved = new JSONObject(contents(file));
            assertTrue(saved.getBoolean("enabled")); assertEquals("new-search-key", saved.getString("key"));
            var lookup = search.takeRequest();
            assertEquals("Bearer new-search-key", lookup.getHeader("Authorization"));
            assertEquals(1, new JSONObject(lookup.getBody().readUtf8()).getInt("count"));
            assertEquals("Bearer model-key", model.takeRequest().getHeader("Authorization"));
        }
    }
    @Test public void failedModelPairingDoesNotSaveAnOtherwiseValidSearchConfiguration() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            search.enqueue(LlmSearchTest.results()); model.enqueue(new MockResponse().setBody(LlmSearchTest.reply("728391")));
            model.start(); search.start(); File file = oldConfig();
            assertThrows(LlmCall.Failure.class, () -> checks(search.url("/v1/tools/search").toString()).saveSearch(
                new LlmCall(), new SearchConfig(true, "new-search-key"),
                new ApiConfig(model.url("/v1").toString(), "model-key", "vision"), file));
            assertEquals("old-settings", contents(file));
        }
    }
    @Test public void disablingSearchWorksWithoutNetworkOrAValidRemoteKey() throws Exception {
        File file = oldConfig();
        checks("https://unused.invalid").saveSearch(new LlmCall(), new SearchConfig(false, "expired-key"), null, file);
        JSONObject saved = new JSONObject(contents(file));
        assertFalse(saved.getBoolean("enabled")); assertEquals("expired-key", saved.getString("key"));
    }
    @Test public void cancelledValidationNeverPublishesCandidate() throws Exception {
        File file = oldConfig(); LlmCall call = new LlmCall(); call.cancel();
        assertThrows(CancellationException.class, () -> checks("https://unused.invalid").saveSearch(call,
            new SearchConfig(true, "candidate"), null, file));
        assertEquals("old-settings", contents(file));
    }
    @Test public void publishedApiConfigReadDoesNotDeleteAnActiveStagingFile() throws Exception {
        File file = oldConfig();
        JSONObject config = new ApiConfig("https://example.com", "old-key", "vision").json();
        Files.write(file.toPath(), config.toString().getBytes(StandardCharsets.UTF_8));
        File staging = new File(file.getPath() + ".new");
        Files.write(staging.toPath(), "in-progress-new-settings".getBytes(StandardCharsets.UTF_8));
        assertEquals("old-key", ApiConfig.read(file).key);
        assertTrue(staging.isFile()); assertEquals("in-progress-new-settings", contents(staging));
    }
    @Test public void rejectedToolContinuationDoesNotPublishAnApparentlyCapableModel() throws Exception {
        try (MockWebServer model = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(probeTool()));
            model.enqueue(new MockResponse().setResponseCode(400));
            model.start(); File file = oldConfig();
            assertThrows(LlmCall.Failure.class, () -> checks("https://unused.invalid").saveModel(new LlmCall(),
                new ApiConfig(model.url("/v1").toString(), "candidate-key", "vision"), true, file));
            assertEquals("old-settings", contents(file));
            assertEquals(2, model.getRequestCount());
        }
    }
}
