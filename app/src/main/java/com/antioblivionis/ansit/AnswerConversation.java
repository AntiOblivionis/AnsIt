package com.antioblivionis.ansit;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * In-memory history for one continuous answering session. Only the screenshot and final answer of
 * each successful turn are retained; search/tool messages belong to their own turn only.
 */
final class AnswerConversation {
    // A local bound avoids re-uploading an ever-growing screenshot history; the provider's own
    // context limit is the primary "full" signal and is reported the same way.
    static final int MAX_TURNS = 32;
    static final long MAX_CHARS = 20L * 1024 * 1024;
    static final String HISTORY_PROMPT = "请解答当前截图中的主要问题，直接给出答案和必要说明。";
    private final List<JSONObject> messages = new ArrayList<>();
    private int turns;
    private long chars;
    private boolean full;

    synchronized List<JSONObject> snapshot() { return new ArrayList<>(messages); }
    synchronized boolean full() { return full; }
    synchronized void markFull() { full = true; }
    synchronized void reset() { messages.clear(); turns = 0; chars = 0; full = false; }
    synchronized boolean limitReached() {
        if (turns >= MAX_TURNS || chars >= MAX_CHARS) { full = true; return true; }
        return false;
    }
    synchronized void append(String base64Png, String answer) {
        try {
            messages.add(LlmCall.imageMessage(base64Png, HISTORY_PROMPT));
            messages.add(LlmCall.assistantMessage(answer));
        } catch (Exception impossible) { throw new IllegalStateException("Invalid conversation message", impossible); }
        turns++;
        chars += base64Png.length() + answer.length() + 64L;
    }
    synchronized int turns() { return turns; }
}
