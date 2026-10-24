package com.antioblivionis.ansit;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import static org.junit.Assert.*;

public class StreamCompatTest {
    @Test public void boundedReadLeavesRemainderForNextRead() throws Exception {
        InputStream input = new ByteArrayInputStream("abcdef".getBytes(StandardCharsets.UTF_8));
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), StreamCompat.readNBytes(input, 3));
        assertArrayEquals("def".getBytes(StandardCharsets.UTF_8), StreamCompat.readNBytes(input, 20));
        assertEquals(0, StreamCompat.readNBytes(input, 1).length);
    }
    @Test public void handlesShortAndZeroLengthReadsWithoutLosingBytes() throws Exception {
        InputStream input = new ByteArrayInputStream(new byte[]{0, 1, 2, 3, 4}) {
            boolean zero = true;
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                if (zero) { zero = false; return 0; }
                return super.read(buffer, offset, Math.min(1, length));
            }
        };
        assertArrayEquals(new byte[]{0, 1, 2, 3, 4}, StreamCompat.readNBytes(input, 10));
    }
    @Test public void zeroLimitDoesNotReadInput() throws Exception {
        assertEquals(0, StreamCompat.readNBytes(new InputStream() {
            @Override public int read() { throw new AssertionError("Unexpected read"); }
        }, 0).length);
    }
    @Test(expected = IOException.class) public void preservesReadFailure() throws Exception {
        StreamCompat.readNBytes(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("failed"); }
        }, 1);
    }
}
