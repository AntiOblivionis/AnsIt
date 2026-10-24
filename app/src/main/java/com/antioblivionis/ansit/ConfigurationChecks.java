package com.antioblivionis.ansit;

import java.io.File;
import org.json.JSONObject;

/** Real requests are checked before a caller may publish new settings. */
final class ConfigurationChecks {
    interface Probes { VisionProbe create(); }
    interface Publisher { void write(File file, JSONObject value) throws Exception; }
    private final Probes probes;
    private final String searchEndpoint;
    private final Publisher publisher;
    ConfigurationChecks() { this(VisionProbe::create, SearchConfig.ENDPOINT); }
    ConfigurationChecks(Probes probes, String searchEndpoint) { this(probes, searchEndpoint, PrivateSettings::write); }
    ConfigurationChecks(Probes probes, String searchEndpoint, Publisher publisher) {
        this.probes = probes; this.searchEndpoint = searchEndpoint; this.publisher = publisher;
    }

    String saveModel(LlmCall call, ApiConfig config, boolean tools, File target) throws Exception {
        call.limitSeconds(120);
        String result = model(call, config, tools);
        call.check();
        publisher.write(target, config.json());
        return result;
    }
    String saveSearch(LlmCall call, SearchConfig search, ApiConfig savedModel, File target) throws Exception {
        call.limitSeconds(120);
        String result = search(call, search, savedModel);
        call.check();
        publisher.write(target, search.json());
        return result;
    }

    String model(LlmCall call, ApiConfig config, boolean tools) throws Exception {
        call.verifyModel(config, probes.create(), tools);
        return tools ? "图片输入与工具调用检测通过" : "图片输入检测通过";
    }
    String search(LlmCall call, SearchConfig search, ApiConfig savedModel) throws Exception {
        if (!search.enabled) return "搜索已关闭（未发起网络检测）";
        int count = new DeepSeekSearch(call, search.key, searchEndpoint).search("DeepSeek 官方 API 文档", 1).getJSONArray("results").length();
        String result = count > 0 ? "搜索接口检测通过" : "搜索接口响应正常（测试查询暂无匹配结果）";
        if (search.enabled && savedModel != null) {
            call.verifyModel(savedModel, probes.create(), true);
            result += "，主模型图片与工具调用检测通过";
        } else if (search.enabled) result += "；请再检测保存模型配置以确认工具能力";
        return result;
    }
}
