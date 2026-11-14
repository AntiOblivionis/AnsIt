package ltd.tgwgroup.ansit;

import java.io.File;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PackageIdentityTest {
    @Test public void rootServiceUsesTheInstalledApplicationsPrivateDirectory() throws Exception {
        assertEquals("ltd.tgwgroup.ansit", BuildConfig.APPLICATION_ID);
        Field configDirectory = RootMain.class.getDeclaredField("CONFIG_DIR");
        configDirectory.setAccessible(true);
        assertEquals(new File("/data/user/0/ltd.tgwgroup.ansit/files"), configDirectory.get(null));
        assertEquals("ltd.tgwgroup.ansit.RootMain", RootMain.class.getName());
    }

    @Test public void transportNamesStillReachTheExistingServiceAndHook() {
        assertEquals("dev.ansit.control", ServiceProtocol.CONTROL_SOCKET);
        assertEquals("com.antioblivionis.ansit.hook", ServiceProtocol.HOOK_SOCKET);
    }
}
