package com.antioblivionis.ansit;

import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class AnswerConversationTest {
    @Test public void successfulTurnsAppendOnlyScreenshotAndFinalAnswer() throws Exception {
        AnswerConversation conversation = new AnswerConversation();
        conversation.append("image-one", "答案一");
        conversation.append("image-two", "答案二");
        assertEquals(2, conversation.turns());
        List<JSONObject> messages = conversation.snapshot();
        assertEquals(4, messages.size());
        assertEquals("user", messages.get(0).getString("role"));
        assertEquals("assistant", messages.get(1).getString("role"));
        assertEquals("答案一", messages.get(1).getString("content"));
        assertEquals("user", messages.get(2).getString("role"));
        assertFalse(messages.toString().contains("tool_calls"));
    }
    @Test public void providerFullFreezesUntilReset() {
        AnswerConversation conversation = new AnswerConversation();
        assertFalse(conversation.full());
        conversation.markFull();
        assertTrue(conversation.full());
        conversation.append("image", "answer");
        assertTrue(conversation.full());
        conversation.reset();
        assertFalse(conversation.full());
        assertEquals(0, conversation.turns());
        assertTrue(conversation.snapshot().isEmpty());
    }
    @Test public void localLimitMarksTheSessionFull() {
        AnswerConversation conversation = new AnswerConversation();
        for (int i = 0; i < AnswerConversation.MAX_TURNS; i++) conversation.append("i", "a");
        assertTrue(conversation.limitReached());
        assertTrue(conversation.full());
    }
}
