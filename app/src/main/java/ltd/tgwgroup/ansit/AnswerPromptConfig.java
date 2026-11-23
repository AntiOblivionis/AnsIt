package ltd.tgwgroup.ansit;

import java.io.File;
import org.json.JSONObject;

record AnswerPromptConfig(String text) {
    static final String FILE_NAME = "answer-prompt.json";
    static final int MAX_LENGTH = 1000;
    static final AnswerPromptConfig DEFAULT = new AnswerPromptConfig("");

    AnswerPromptConfig {
        if (text.length() > MAX_LENGTH) throw new IllegalArgumentException("提示词最多 1000 个字符");
        text = text.trim();
    }

    static AnswerPromptConfig read(File file) throws Exception {
        if (!file.isFile()) return DEFAULT;
        Object text = PrivateSettings.read(file, 8192).get("text");
        if (!(text instanceof String value)) throw new IllegalArgumentException("提示词必须是文本");
        return new AnswerPromptConfig(value);
    }

    JSONObject json() throws Exception { return new JSONObject().put("text", text); }
}
