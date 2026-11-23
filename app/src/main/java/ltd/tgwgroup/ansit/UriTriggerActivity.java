package ltd.tgwgroup.ansit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The launcher or automation returns to its current screen before the Root service captures it. */
public final class UriTriggerActivity extends Activity {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TriggerProtocol.UriAction action = savedInstanceState == null ? actionOf(getIntent()) : null;
        Context app = getApplicationContext();
        finish();
        if (action == null) return;

        // Give the preceding activity time to regain the foreground after this windowless hop.
        MAIN.postDelayed(() -> WORKER.execute(() -> {
            try { RootServiceStarter.triggerUri(app.getApplicationInfo().sourceDir, action); }
            catch (IOException failure) {
                MAIN.post(() -> Toast.makeText(app, failure.getMessage(), Toast.LENGTH_SHORT).show());
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }), 150);
    }

    static TriggerProtocol.UriAction actionOf(Intent intent) {
        if (intent == null || !Intent.ACTION_VIEW.equals(intent.getAction())) return null;
        String uri = intent.getDataString();
        if (uri == null) return null;
        return switch (uri) {
            case "ansit://", "ansit://trigger", "ansit://trigger/" -> TriggerProtocol.UriAction.TOGGLE;
            case "ansit://next", "ansit://next/" -> TriggerProtocol.UriAction.NEXT;
            case "ansit://next-context", "ansit://next-context/" -> TriggerProtocol.UriAction.NEXT_CONTEXT;
            default -> null;
        };
    }
}
