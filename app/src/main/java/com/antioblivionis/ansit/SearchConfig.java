package com.antioblivionis.ansit;

import java.io.File;
import org.json.JSONObject;

final class SearchConfig {
    static final String FILE_NAME = "search-config.json";
    static final String ENDPOINT = "https://api.deepseek.com/v1/tools/search";
    final String key;
    final boolean enabled;
    static final class Invalid extends Exception { Invalid(String message) { super(message); } }
    SearchConfig(boolean enabled, String key) throws Invalid {
        this.key = key.trim(); this.enabled = enabled;
        if ((enabled && this.key.isEmpty()) || this.key.length() > 4096 || this.key.chars().anyMatch(Character::isISOControl))
            throw new Invalid("请填写有效的 DeepSeek API Key");
    }
    static SearchConfig disabled() {
        try { return new SearchConfig(false, ""); } catch (Invalid impossible) { throw new AssertionError(impossible); }
    }
    static SearchConfig read(File file) throws Exception {
        if (!file.isFile()) return disabled();
        JSONObject json = PrivateSettings.read(file, 8192);
        return new SearchConfig(json.getBoolean("enabled"), json.getString("key"));
    }
    JSONObject json() throws Exception { return new JSONObject().put("enabled", enabled).put("key", key); }
}
