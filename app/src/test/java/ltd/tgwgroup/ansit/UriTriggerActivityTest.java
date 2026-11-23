package ltd.tgwgroup.ansit;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class UriTriggerActivityTest {
    @Test public void bareAndNamedLinksResolveToTheWindowlessTrigger() {
        for (String value : new String[]{"ansit://", "ansit://trigger", "ansit://trigger/"}) {
            assertRoute(value, TriggerProtocol.UriAction.TOGGLE);
        }
    }

    @Test public void nextLinksChooseExplicitContextBehavior() {
        for (String value : new String[]{"ansit://next", "ansit://next/"}) {
            assertRoute(value, TriggerProtocol.UriAction.NEXT);
        }
        for (String value : new String[]{"ansit://next-context", "ansit://next-context/"}) {
            assertRoute(value, TriggerProtocol.UriAction.NEXT_CONTEXT);
        }
        assertEquals("TRIGGER_URI_NEXT", TriggerProtocol.UriAction.fromArgument("next").socketCommand);
        assertEquals("TRIGGER_URI_NEXT_CONTEXT", TriggerProtocol.UriAction.fromArgument("next-context").socketCommand);
    }

    @Test public void onlyTheSupportedViewLinksCanTrigger() {
        for (String value : new String[]{"ansit://settings", "ansit://trigger/other", "ansit://trigger?mode=next",
            "ansit://next?context=true", "ansit://next/context", "ansit://trigger#stop", "ansit://role@trigger", "https://trigger"}) {
            assertNull(value, UriTriggerActivity.actionOf(link(value)));
        }
        assertNull(UriTriggerActivity.actionOf(new Intent(Intent.ACTION_VIEW)));
        assertNull(UriTriggerActivity.actionOf(new Intent(Intent.ACTION_SEND, Uri.parse("ansit://trigger"))));
    }

    @Test public void unsupportedLinkFinishesWithoutOpeningSettings() {
        try (var controller = Robolectric.buildActivity(UriTriggerActivity.class, link("ansit://settings"))) {
            controller.create();
            assertTrue(controller.get().isFinishing());
        }
    }

    private static Intent link(String value) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(value)).addCategory(Intent.CATEGORY_BROWSABLE);
    }

    private static void assertRoute(String value, TriggerProtocol.UriAction expected) {
        Intent intent = link(value);
        assertEquals(expected, UriTriggerActivity.actionOf(intent));
        PackageManager packages = RuntimeEnvironment.getApplication().getPackageManager();
        var activity = packages.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        assertNotNull(activity);
        assertEquals(UriTriggerActivity.class.getName(), activity.activityInfo.name);
        assertEquals(android.R.style.Theme_NoDisplay, activity.activityInfo.theme);
    }
}
