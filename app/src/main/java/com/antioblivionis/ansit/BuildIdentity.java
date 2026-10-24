package com.antioblivionis.ansit;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Fingerprint the loaded APK once, rather than assuming a matching version means matching code. */
final class BuildIdentity {
    static String digest(String path) throws IOException {
        if (path == null || !new File(path).isFile()) throw new IOException("Installed APK unavailable");
        try (FileInputStream input = new FileInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            byte[] bytes = digest.digest();
            char[] hex = new char[bytes.length * 2];
            char[] digits = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                hex[i * 2] = digits[(bytes[i] & 0xff) >>> 4];
                hex[i * 2 + 1] = digits[bytes[i] & 0xf];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
    }
    private BuildIdentity() { }
}
