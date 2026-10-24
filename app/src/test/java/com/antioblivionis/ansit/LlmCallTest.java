package com.antioblivionis.ansit;

import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import java.util.concurrent.*;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class LlmCallTest {
    private static final String KEY = "test-only-credential";
    @Test public void normalizesBaseAndFullEndpoint() throws Exception {
        assertEquals("https://example.com/v1/chat/completions",new ApiConfig("https://example.com",KEY,"vision").endpoint);
        assertEquals("https://example.com/v1/chat/completions",new ApiConfig("https://example.com/v1/",KEY,"vision").endpoint);
        assertEquals("https://example.com/custom/chat/completions",new ApiConfig("https://example.com/custom/chat/completions",KEY,"vision").endpoint);
        assertThrows(ApiConfig.Invalid.class,()->new ApiConfig("https://user:pass@example.com",KEY,"vision"));
        assertThrows(ApiConfig.Invalid.class,()->new ApiConfig("https://example.com?token=private",KEY,"vision"));
        assertThrows(ApiConfig.Invalid.class,()->new ApiConfig("https://example.com",KEY,""));
    }
    @Test public void eachImageIsAnIndependentConversation() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"独立答案1\"}}]}"));
            server.enqueue(new MockResponse().setBody("{\"choices\":[{\"message\":{\"content\":\"独立答案2\"}}]}"));
            server.start();
            ApiConfig config=new ApiConfig(server.url("/v1").toString(),KEY,"my-vision-model");
            assertEquals("独立答案1",new LlmCall().answerImage(config,"first-frame"));
            assertEquals("独立答案2",new LlmCall().answerImage(config,"second-frame"));
            for(int i=0;i<2;i++) {
                RecordedRequest recorded=server.takeRequest(2,TimeUnit.SECONDS); assertNotNull(recorded);
                assertEquals("POST",recorded.getMethod());assertEquals("/v1/chat/completions",recorded.getPath());
                assertEquals("Bearer "+KEY,recorded.getHeader("Authorization"));
                JSONObject request=new JSONObject(recorded.getBody().readUtf8());
                assertEquals("my-vision-model",request.getString("model"));assertFalse(request.getBoolean("stream"));
                assertEquals(2,request.getJSONArray("messages").length());
                assertEquals("system",request.getJSONArray("messages").getJSONObject(0).getString("role"));
                assertEquals("user",request.getJSONArray("messages").getJSONObject(1).getString("role"));
                String image=request.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url");
                assertEquals("data:image/png;base64,"+(i==0?"first-frame":"second-frame"),image);
                assertFalse(request.has("conversation"));assertFalse(request.has("previous_response_id"));
                assertFalse(request.toString().contains("独立答案"));
            }
        }
    }
    @Test public void errorsAreSanitizedAndRedirectIsNotFollowed() throws Exception {
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(401).setBody(KEY));
            server.enqueue(new MockResponse().setResponseCode(302).setHeader("Location","/stolen"));
            server.start();
            ApiConfig config=new ApiConfig(server.url("/v1").toString(),KEY,"vision");
            LlmCall.Failure failure=assertThrows(LlmCall.Failure.class,()->new LlmCall().answerImage(config,"frame"));
            assertFalse(failure.getMessage().contains(KEY));assertTrue(failure.getMessage().contains("401"));
            assertThrows(LlmCall.Failure.class,()->new LlmCall().answerImage(config,"frame"));
            assertEquals(2,server.getRequestCount());
        }
    }
    @Test public void cancelledCallCannotReturnAnAnswer() throws Exception {
        ExecutorService executor=Executors.newSingleThreadExecutor();
        try(MockWebServer server=new MockWebServer()) {
            server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));server.start();
            ApiConfig config=new ApiConfig(server.url("/v1").toString(),KEY,"vision");
            LlmCall call=new LlmCall();
            Future<?> task=executor.submit(()->assertThrows(CancellationException.class,()->call.answerImage(config,"frame")));
            assertNotNull(server.takeRequest(2,TimeUnit.SECONDS));call.cancel();task.get(3,TimeUnit.SECONDS);
            assertThrows(CancellationException.class,()->call.answerImage(config,"other"));
        } finally { executor.shutdownNow(); }
    }
}
