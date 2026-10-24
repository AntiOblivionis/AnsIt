package com.antioblivionis.ansit;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class BuildIdentityTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();
    @Test public void fingerprintKeepsStandardLowercaseSha256OnOlderAndroid() throws Exception {
        File file = folder.newFile();
        Files.write(file.toPath(), "abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", BuildIdentity.digest(file.getPath()));
    }
    @Test public void emptyFileKeepsLeadingZeroNibbles() throws Exception {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", BuildIdentity.digest(folder.newFile().getPath()));
    }
    @Test(expected = IOException.class) public void unavailableApkCannotLookCurrent() throws Exception {
        BuildIdentity.digest(new File(folder.getRoot(), "absent.apk").getPath());
    }
}
