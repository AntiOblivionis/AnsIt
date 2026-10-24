package com.antioblivionis.ansit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import java.util.concurrent.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LlmSearchTest {
    static final String MODEL_KEY = "test-model-credential", SEARCH_KEY = "test-search-credential";
    static String reply(String text) throws Exception {
        return envelope(new JSONObject().put("role", "assistant").put("content", text));
    }
    static String envelope(JSONObject message) throws Exception {
        return new JSONObject().put("choices", new JSONArray().put(new JSONObject().put("message", message))).toString();
    }
    static String tool(String id, String query) throws Exception {
        return envelope(new JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
            .put("reasoning_content", "opaque-provider-metadata")
            .put("tool_calls", new JSONArray().put(new JSONObject().put("id", id).put("type", "function")
                .put("function", new JSONObject().put("name", "web_search")
                    .put("arguments", new JSONObject().put("query", query).toString())))));
    }
    static MockResponse results() {
        return new MockResponse().setBody("""
            {"results":[{"title":"Official reference","url":"https://example.com/reference",
            "summary":"Verified reference facts.","content":"large page content must not be forwarded",
            "date":"2026-10-24T00:00:00Z","site_icon":"https://example.com/icon"}]}
            """);
    }
    @Test public void modelCanAnswerWithoutCallingSearch() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(reply("计算结果为 4"))); model.start(); search.start();
            LlmCall call = new LlmCall();
            assertEquals("计算结果为 4", call.answerImage(new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision"),
                "frame", new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString())));
            JSONObject sent = new JSONObject(model.takeRequest().getBody().readUtf8());
            assertEquals("auto", sent.getString("tool_choice")); assertTrue(sent.has("tools"));
            assertEquals(0, search.getRequestCount());
        }
    }
    @Test public void searchRoundTripKeepsCredentialsSeparateAndPreservesToolMetadata() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(tool("call_1", "最新公开资料")));
            model.enqueue(new MockResponse().setBody(reply("已根据资料核对答案")));
            search.enqueue(results()); model.start(); search.start();
            LlmCall call = new LlmCall();
            assertEquals("已根据资料核对答案", call.answerImage(
                new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision"), "current-image-only",
                new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString())));
            var first = model.takeRequest(); var second = model.takeRequest(); var lookup = search.takeRequest();
            assertEquals("Bearer " + MODEL_KEY, first.getHeader("Authorization"));
            assertEquals("Bearer " + MODEL_KEY, second.getHeader("Authorization"));
            assertEquals("Bearer " + SEARCH_KEY, lookup.getHeader("Authorization"));
            assertEquals("/v1/tools/search", lookup.getPath());
            JSONObject query = new JSONObject(lookup.getBody().readUtf8());
            assertEquals("最新公开资料", query.getString("query")); assertEquals(5, query.getInt("count"));
            assertFalse(query.getBoolean("need_content"));
            assertFalse(query.toString().contains("current-image-only"));
            assertFalse(query.toString().contains(MODEL_KEY)); assertFalse(query.toString().contains(SEARCH_KEY));
            JSONObject continued = new JSONObject(second.getBody().readUtf8());
            JSONArray messages = continued.getJSONArray("messages");
            assertEquals(4, messages.length());
            assertEquals("opaque-provider-metadata", messages.getJSONObject(2).getString("reasoning_content"));
            assertEquals("tool", messages.getJSONObject(3).getString("role"));
            assertEquals("call_1", messages.getJSONObject(3).getString("tool_call_id"));
            JSONObject evidence = new JSONObject(messages.getJSONObject(3).getString("content"));
            assertEquals("Verified reference facts.", evidence.getJSONArray("results").getJSONObject(0).getString("summary"));
            assertFalse(evidence.toString().contains("large page content"));
            assertFalse(continued.toString().contains(SEARCH_KEY)); assertFalse(continued.toString().contains(MODEL_KEY));
        }
    }
    @Test public void nextImageHasNoPreviousSearchOrAnswerEvenWhenClientIsReused() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(tool("call_0", "old-task-query")));
            model.enqueue(new MockResponse().setBody(reply("old-task-answer")));
            model.enqueue(new MockResponse().setBody(reply("new-task-answer")));
            search.enqueue(results()); model.start(); search.start();
            LlmCall call = new LlmCall();
            ApiConfig config = new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision");
            DeepSeekSearch client = new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString());
            call.answerImage(config, "old-image", client); call.answerImage(config, "new-image", client);
            model.takeRequest(); model.takeRequest();
            JSONObject next = new JSONObject(model.takeRequest().getBody().readUtf8());
            assertEquals(2, next.getJSONArray("messages").length());
            assertFalse(next.toString().contains("old-task")); assertFalse(next.toString().contains("old-image"));
            assertFalse(next.has("conversation")); assertFalse(next.has("previous_response_id"));
        }
    }
    @Test public void searchIsBoundedAndFinalModelRequestDisablesTools() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            for (int i = 0; i < 3; i++) {
                model.enqueue(new MockResponse().setBody(tool("call_0", "query-" + i)));
                search.enqueue(results());
            }
            model.enqueue(new MockResponse().setBody(reply("最终答案"))); model.start(); search.start();
            LlmCall call = new LlmCall();
            assertEquals("最终答案", call.answerImage(new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision"),
                "image", new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString())));
            assertEquals(3, search.getRequestCount()); assertEquals(4, model.getRequestCount());
            model.takeRequest(); model.takeRequest(); model.takeRequest();
            JSONObject last = new JSONObject(model.takeRequest().getBody().readUtf8());
            assertEquals("none", last.getString("tool_choice"));
        }
    }
    @Test public void failedSearchIsReportedToModelWithoutLeakingResponseBodyOrFollowingRedirect() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer(); MockWebServer other = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(tool("call_1", "query")));
            model.enqueue(new MockResponse().setBody(reply("本次未能取得联网资料")));
            other.start();
            search.enqueue(new MockResponse().setResponseCode(302).setHeader("Location", other.url("/stolen")).setBody(SEARCH_KEY));
            model.start(); search.start();
            LlmCall call = new LlmCall();
            call.answerImage(new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision"), "image",
                new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString()));
            model.takeRequest();
            JSONObject last = new JSONObject(model.takeRequest().getBody().readUtf8());
            String result = last.getJSONArray("messages").getJSONObject(3).getString("content");
            assertTrue(result.contains("302")); assertFalse(result.contains(SEARCH_KEY));
            assertFalse(new JSONObject(result).getBoolean("search_succeeded"));
            assertEquals("none", last.getString("tool_choice"));
            assertEquals(0, other.getRequestCount());
        }
    }
    @Test public void cancellationDuringSearchStopsTheWholeAnswer() throws Exception {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(tool("call_1", "query")));
            search.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));
            model.start(); search.start();
            LlmCall call = new LlmCall();
            ApiConfig config = new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision");
            Future<?> task = worker.submit(() -> assertThrows(CancellationException.class, () ->
                call.answerImage(config, "image", new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString()))));
            assertNotNull(search.takeRequest(3, TimeUnit.SECONDS)); call.cancel(); task.get(3, TimeUnit.SECONDS);
            assertEquals(1, model.getRequestCount());
        } finally { worker.shutdownNow(); }
    }
    @Test public void queryLimitsUseUnicodeCharactersAndRejectInvalidInputBeforeNetwork() throws Exception {
        try (MockWebServer search = new MockWebServer()) {
            search.enqueue(results()); search.start();
            DeepSeekSearch client = new DeepSeekSearch(new LlmCall(), SEARCH_KEY, search.url("/v1/tools/search").toString());
            client.search("𠮷".repeat(128));
            assertThrows(LlmCall.Failure.class, () -> client.search("a".repeat(129)));
            assertThrows(LlmCall.Failure.class, () -> client.search("  "));
            assertThrows(LlmCall.Failure.class, () -> client.search("a\nb"));
            assertEquals(1, search.getRequestCount());
        }
    }
    @Test public void unknownToolCannotExecuteAnythingOrReachSearch() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            JSONObject response = new JSONObject(tool("call_1", "unused"));
            response.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls")
                .getJSONObject(0).getJSONObject("function").put("name", "run_shell");
            model.enqueue(new MockResponse().setBody(response.toString()));
            model.enqueue(new MockResponse().setBody(reply("直接回答")));
            model.start(); search.start();
            LlmCall call = new LlmCall();
            call.answerImage(new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision"), "image",
                new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString()));
            assertEquals(0, search.getRequestCount());
        }
    }
    @Test public void modelCredentialInToolArgumentsNeverReachesSearch() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            model.enqueue(new MockResponse().setBody(tool("call_1", MODEL_KEY)));
            model.enqueue(new MockResponse().setBody(reply("本次不搜索")));
            model.start(); search.start();
            LlmCall call = new LlmCall();
            call.answerImage(new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision"), "image",
                new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString()));
            assertEquals(0, search.getRequestCount());
        }
    }
    @Test public void echoedSearchCredentialIsRedactedFromResults() throws Exception {
        try (MockWebServer search = new MockWebServer()) {
            search.enqueue(new MockResponse().setBody(new JSONObject().put("results", new JSONArray().put(
                new JSONObject().put("title", SEARCH_KEY).put("url", "https://example.com")
                    .put("summary", "echo " + SEARCH_KEY))).toString()));
            search.start();
            JSONObject result = new DeepSeekSearch(new LlmCall(), SEARCH_KEY, search.url("/v1/tools/search").toString()).search("query");
            assertFalse(result.toString().contains(SEARCH_KEY));
            assertTrue(result.toString().contains("[已隐藏]"));
        }
    }
    @Test public void oversizedParallelSearchBatchGetsBudgetResultsAndCanStillFinish() throws Exception {
        try (MockWebServer model = new MockWebServer(); MockWebServer search = new MockWebServer()) {
            JSONObject initial = new JSONObject(tool("call_0", "query-0"));
            JSONArray calls = initial.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getJSONArray("tool_calls");
            for (int i = 1; i < 5; i++) {
                JSONObject call = new JSONObject(calls.getJSONObject(0).toString()).put("id", "call_" + i);
                call.getJSONObject("function").put("arguments", new JSONObject().put("query", "query-" + i).toString());
                calls.put(call);
            }
            model.enqueue(new MockResponse().setBody(initial.toString()));
            model.enqueue(new MockResponse().setBody(reply("基于有限资料的答案")));
            for (int i = 0; i < 3; i++) search.enqueue(results());
            model.start(); search.start(); LlmCall call = new LlmCall();
            assertEquals("基于有限资料的答案", call.answerImage(
                new ApiConfig(model.url("/v1").toString(), MODEL_KEY, "vision"), "image",
                new DeepSeekSearch(call, SEARCH_KEY, search.url("/v1/tools/search").toString())));
            assertEquals(3, search.getRequestCount());
            model.takeRequest();
            JSONObject finalRequest = new JSONObject(model.takeRequest().getBody().readUtf8());
            assertEquals("none", finalRequest.getString("tool_choice"));
            assertEquals(8, finalRequest.getJSONArray("messages").length());
            assertTrue(finalRequest.getJSONArray("messages").getJSONObject(6).getString("content").contains("本次搜索已结束"));
        }
    }
}
