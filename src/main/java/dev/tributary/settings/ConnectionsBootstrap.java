package dev.tributary.settings;

import com.intellij.openapi.application.ApplicationManager;
import dev.tributary.cli.Model.CliConnection;
import dev.tributary.cli.RtcException;
import dev.tributary.vcs.RtcClients;
import java.util.List;

public final class ConnectionsBootstrap {

    private ConnectionsBootstrap() {}

    public static void importIfEmpty() {
        TributarySettings settings = TributarySettings.getInstance();
        if (!settings.getState().connections.isEmpty()) {
            return;
        }
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                List<CliConnection> found = RtcClients.global().listConnections();
                for (CliConnection connection : found) {
                    settings.getState()
                            .connections
                            .add(new TributarySettings.Connection(
                                    connection.uri(), connection.userName(), connection.nickname()));
                }
            } catch (RtcException ignored) {
            }
        });
    }
}
