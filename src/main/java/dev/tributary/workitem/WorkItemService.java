package dev.tributary.workitem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.PluginId;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.RtcException;
import dev.tributary.settings.Credentials;
import dev.tributary.settings.TributarySettings;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Service(Service.Level.APP)
public final class WorkItemService implements Disposable, BridgeWorkItems.Channel {

    private static final String PLUGIN_ID = "dev.tributary";
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(60);

    private BridgeProcess process;
    private String loggedInAs = "";

    public static WorkItemService getInstance() {
        return ApplicationManager.getApplication().getService(WorkItemService.class);
    }

    public WorkItemDirectory directory() {
        return new BridgeWorkItems(this);
    }

    @Override
    public synchronized JsonElement call(String method, JsonObject params) throws RtcException {
        ensureStarted();
        ensureLoggedIn();
        return process.call(method, params, CALL_TIMEOUT);
    }

    private void ensureStarted() throws RtcException {
        if (process != null && process.isAlive()) {
            return;
        }
        loggedInAs = "";
        TributarySettings.Data settings = TributarySettings.getInstance().getState();
        Path jar = bridgeJar()
                .filter(java.nio.file.Files::isRegularFile)
                .orElseThrow(() -> unavailable("error.bridge.no.jar"));
        Path java = BridgeLocator.findJava8(settings.bridgeJdkPath, List.of())
                .orElseThrow(() -> unavailable("error.bridge.no.java"));
        Path ibm = BridgeLocator.findIbmPlugins(settings.ibmLibrariesPath, List.of())
                .orElseThrow(() -> unavailable("error.bridge.no.ibm"));
        try {
            BridgeLocator.Launch launch =
                    BridgeLocator.launch(java, jar, ibm).orElseThrow(() -> unavailable("error.bridge.no.ibm"));
            process = BridgeProcess.start(launch.command());
        } catch (IOException e) {
            throw new RtcException(
                    RtcException.Kind.CLI_MISSING, TributaryBundle.message("error.bridge.start", e.getMessage()));
        }
    }

    private void ensureLoggedIn() throws RtcException {
        TributarySettings.Connection connection =
                TributarySettings.getInstance().active();
        if (connection == null) {
            throw new RtcException(RtcException.Kind.AUTH_REQUIRED, TributaryBundle.message("error.no.connection"));
        }
        String key = connection.uri + "|" + connection.userId;
        if (key.equals(loggedInAs)) {
            return;
        }
        String password = Credentials.password(connection.uri, connection.userId);
        if (password == null || password.isEmpty()) {
            throw new RtcException(
                    RtcException.Kind.AUTH_REQUIRED, TributaryBundle.message("error.bridge.no.password"));
        }
        JsonObject params = new JsonObject();
        params.addProperty("uri", connection.uri);
        params.addProperty("user", connection.userId);
        params.addProperty("password", password);
        process.call("login", params, CALL_TIMEOUT);
        loggedInAs = key;
    }

    private static Optional<Path> bridgeJar() {
        var descriptor = PluginManagerCore.getPlugin(PluginId.getId(PLUGIN_ID));
        if (descriptor == null) {
            return Optional.empty();
        }
        return Optional.of(descriptor.getPluginPath().resolve("bridge").resolve("tributary-bridge.jar"));
    }

    private static RtcException unavailable(String key) {
        return new RtcException(RtcException.Kind.CLI_MISSING, TributaryBundle.message(key));
    }

    @Override
    public synchronized void dispose() {
        if (process != null) {
            process.close();
            process = null;
        }
    }
}
