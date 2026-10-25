package dev.stormvisuals.dev;

import dev.stormvisuals.StormVisualsClient;
import dev.stormvisuals.gui.ClickGuiScreen;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.LoggerFactory;

/** Registration runs on the client thread; the external JDI tool only requests it. */
public final class LiveReload {
    private static final Map<Class<?>, Boolean> INITIALIZED = new LinkedHashMap<>();
    public static volatile boolean requested;
    public static volatile String result;
    private static boolean applying;

    private LiveReload() {}

    public static void initializeOnce(Class<?> type) {
        Boolean previous = INITIALIZED.putIfAbsent(type, false);
        if (Boolean.TRUE.equals(previous)) return;
        if (previous != null) throw new IllegalStateException("Previous initialization failed: " + type.getName() + "; restart required");
        try {
            type.getMethod("initialize").invoke(null);
            INITIALIZED.put(type, true);
        } catch (ReflectiveOperationException error) {
            Throwable cause = error instanceof InvocationTargetException invocation ? invocation.getCause() : error;
            throw new IllegalStateException("Initialization failed: " + type.getName() + "; restart required", cause);
        }
    }

    public static boolean isApplying() { return applying; }

    public static void initialize() {
        if (!FabricLoader.getInstance().isDevelopmentEnvironment() || !Boolean.getBoolean("stormvisuals.hotSwap")) return;
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!requested) return;
            applying = true;
            try {
                int before = INITIALIZED.size();
                StormVisualsClient.initializeModules();
                int added = ClickGuiScreen.refreshModules();
                result = "Initialized " + (INITIALIZED.size() - before) + " new component(s), added " + added + " module card(s).";
                LoggerFactory.getLogger("StormVisuals").info("[HotSwap] {}", result);
            } catch (RuntimeException | LinkageError error) {
                result = "ERROR: " + error.getMessage() + ". Restart the development client.";
                LoggerFactory.getLogger("StormVisuals").error("[HotSwap] Registration failed", error);
            } finally {
                applying = false;
                requested = false;
            }
        });
    }
}
