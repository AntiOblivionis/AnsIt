package com.antioblivionis.ansit;

import android.system.Os;
import android.util.AtomicFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

final class PrivateSettings {
    static final class SaveNotConfirmed extends IOException {
        SaveNotConfirmed() { super("未能确认配置已保存，请重试"); }
    }
    static JSONObject read(File file, int maxBytes) throws Exception {
        // Open the published file only; never delete a different process's live AtomicFile .new.
        byte[] bytes;
        try (FileInputStream input = new FileInputStream(file)) { bytes = StreamCompat.readNBytes(input, maxBytes + 1); }
        if (bytes.length > maxBytes) throw new IllegalArgumentException("Settings too large");
        return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    }
    static void write(File file, JSONObject value) throws Exception {
        byte[] data = value.toString().getBytes(StandardCharsets.UTF_8);
        AtomicFile target = new AtomicFile(file);
        var output = target.startWrite();
        try {
            Os.fchmod(output.getFD(), 0600);
            output.write(data);
            target.finishWrite(output);
        } catch (Exception failure) { target.failWrite(output); throw failure; }
        // finishWrite can log a publication failure without throwing; confirm the actual base file.
        try (FileInputStream input = new FileInputStream(file)) {
            if (!Arrays.equals(data, StreamCompat.readNBytes(input, data.length + 1))) throw new SaveNotConfirmed();
        } catch (IOException failure) { throw new SaveNotConfirmed(); }
    }
    private PrivateSettings() { }
}
