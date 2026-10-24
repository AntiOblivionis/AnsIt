package com.antioblivionis.ansit;

import java.io.File;
import org.json.JSONObject;

final class TriggerConfig {
    static final String FILE_NAME = "trigger-config.json";
    static final int DEFAULT_MS = 650, MIN_MS = 200, MAX_MS = 3000;
    final int holdMillis;
    static final class Invalid extends Exception { Invalid() { super("触发阈值须为 200–3000 的整数毫秒"); } }
    TriggerConfig(int holdMillis) throws Invalid {
        if (holdMillis < MIN_MS || holdMillis > MAX_MS) throw new Invalid();
        this.holdMillis = holdMillis;
    }
    static TriggerConfig read(File file) throws Exception {
        if (!file.isFile()) return new TriggerConfig(DEFAULT_MS);
        JSONObject json = PrivateSettings.read(file, 1024);
        Object value = json.get("holdMillis");
        if (!(value instanceof Number number) || number.doubleValue() != number.intValue()) throw new Invalid();
        return new TriggerConfig(number.intValue());
    }
    JSONObject json() throws Exception { return new JSONObject().put("holdMillis", holdMillis); }
}
