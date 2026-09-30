package dev.tributary.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.APP)
@State(name = "TributarySettings", storages = @Storage("tributary.xml"))
public final class TributarySettings implements PersistentStateComponent<TributarySettings.Data> {

    public static final class Connection {
        public String uri = "";
        public String userId = "";
        public String nickname = "";

        public Connection() {}

        public Connection(String uri, String userId, String nickname) {
            this.uri = uri;
            this.userId = userId;
            this.nickname = nickname;
        }
    }

    public static final class Data {
        public String cliPath = "";
        public String cliCharset = "windows-1252";
        public String bridgeJdkPath = "";
        public String ibmLibrariesPath = "";
        public String activeUri = "";
        public int refreshMinutes = 5;
        public boolean confirmDeliver = true;
        public boolean confirmDiscard = true;
        public List<Connection> connections = new ArrayList<>();
    }

    private Data state = new Data();

    public static TributarySettings getInstance() {
        return ApplicationManager.getApplication().getService(TributarySettings.class);
    }

    @Override
    public @NotNull Data getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull Data state) {
        this.state = state;
    }

    public Connection active() {
        for (Connection connection : state.connections) {
            if (connection.uri.equals(state.activeUri)) {
                return connection;
            }
        }
        return state.connections.isEmpty() ? null : state.connections.get(0);
    }

    public String serverUri() {
        Connection connection = active();
        return connection == null ? "" : connection.uri;
    }

    public String userId() {
        Connection connection = active();
        return connection == null ? "" : connection.userId;
    }
}
