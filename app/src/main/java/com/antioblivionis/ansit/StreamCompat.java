package com.antioblivionis.ansit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/** Bounded reads for both Android 12 app code and the root app_process. */
final class StreamCompat {
    static byte[] readNBytes(InputStream input, int limit) throws IOException {
        if (limit < 0) throw new IllegalArgumentException("Negative read limit");
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(limit, 4096));
        byte[] buffer = new byte[Math.min(limit, 4096)];
        while (output.size() < limit) {
            int count = input.read(buffer, 0, Math.min(buffer.length, limit - output.size()));
            if (count < 0) break;
            if (count == 0) {
                int value = input.read();
                if (value < 0) break;
                output.write(value);
            } else output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
    private StreamCompat() { }
}
