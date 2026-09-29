package dev.tributary.settings;

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.FormBuilder;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.CliLocator;
import dev.tributary.cli.Model.CliConnection;
import dev.tributary.ui.common.TributaryNotifier;
import dev.tributary.ui.common.TributaryTasks;
import dev.tributary.vcs.RtcClients;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public final class TributaryConfigurable implements Configurable {

    private final TextFieldWithBrowseButton cliPath = new TextFieldWithBrowseButton();
    private final JBTextField charset = new JBTextField();
    private final JSpinner refreshMinutes = new JSpinner(new SpinnerNumberModel(5, 1, 120, 1));
    private final DefaultTableModel connections =
            new DefaultTableModel(
                    new Object[] {
                        TributaryBundle.message("connection.nickname.column"),
                        TributaryBundle.message("connection.uri.column"),
                        TributaryBundle.message("connection.user.column")
                    },
                    0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
    private final JBTable table = new JBTable(connections);

    public TributaryConfigurable() {
        cliPath.addBrowseFolderListener(
                null,
                FileChooserDescriptorFactory.singleFile().withTitle(TributaryBundle.message("settings.cli.chooser")));
    }

    @Override
    public @Nls String getDisplayName() {
        return TributaryBundle.message("settings.display");
    }

    @Override
    public @Nullable JComponent createComponent() {
        JComponent tablePanel = ToolbarDecorator.createDecorator(table)
                .setAddAction(button -> addConnection())
                .setRemoveAction(button -> removeSelected())
                .addExtraAction(
                        new com.intellij.openapi.actionSystem.AnAction(
                                TributaryBundle.message("settings.import.text"),
                                TributaryBundle.message("settings.import.description"),
                                com.intellij.icons.AllIcons.Actions.Download) {
                            @Override
                            public void actionPerformed(com.intellij.openapi.actionSystem.AnActionEvent e) {
                                importFromCli();
                            }
                        })
                .createPanel();
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(TributaryBundle.message("settings.cli.label"), cliPath)
                .addComponentToRightColumn(new JBLabel(TributaryBundle.message("settings.cli.hint")))
                .addLabeledComponent(TributaryBundle.message("settings.charset.label"), charset)
                .addLabeledComponent(TributaryBundle.message("settings.refresh.label"), refreshMinutes)
                .addLabeledComponentFillVertically(TributaryBundle.message("settings.connections.label"), tablePanel)
                .getPanel();
    }

    @Override
    public boolean isModified() {
        TributarySettings.Data s = TributarySettings.getInstance().getState();
        return !cliPath.getText().trim().equals(s.cliPath)
                || !charset.getText().trim().equals(s.cliCharset)
                || (int) refreshMinutes.getValue() != s.refreshMinutes
                || !currentConnections().equals(asRows(s.connections));
    }

    @Override
    public void apply() {
        TributarySettings.Data s = TributarySettings.getInstance().getState();
        s.cliPath = cliPath.getText().trim();
        s.cliCharset = charset.getText().trim();
        s.refreshMinutes = (int) refreshMinutes.getValue();
        List<TributarySettings.Connection> updated = new ArrayList<>();
        for (int row = 0; row < connections.getRowCount(); row++) {
            updated.add(new TributarySettings.Connection(
                    (String) connections.getValueAt(row, 1), (String) connections.getValueAt(row, 2), (String)
                            connections.getValueAt(row, 0)));
        }
        s.connections = updated;
        if (s.activeUri.isEmpty() && !updated.isEmpty()) {
            s.activeUri = updated.get(0).uri;
        }
    }

    @Override
    public void reset() {
        TributarySettings.Data s = TributarySettings.getInstance().getState();
        cliPath.setText(
                s.cliPath.isEmpty() ? CliLocator.find("").map(Object::toString).orElse("") : s.cliPath);
        charset.setText(s.cliCharset);
        refreshMinutes.setValue(s.refreshMinutes);
        connections.setRowCount(0);
        for (TributarySettings.Connection connection : s.connections) {
            connections.addRow(new Object[] {connection.nickname, connection.uri, connection.userId});
        }
    }

    private void addConnection() {
        ConnectionDialog dialog =
                new ConnectionDialog(ProjectManager.getInstance().getDefaultProject());
        if (!dialog.showAndGet()) {
            return;
        }
        String uri = dialog.uri();
        String userId = dialog.userId();
        String nickname = dialog.nickname();
        char[] password = dialog.password();
        String passwordCopy = new String(password);
        TributaryTasks.run(
                null,
                TributaryBundle.message("connection.login.task", uri),
                false,
                cancelled -> {
                    RtcBackend backend = RtcClients.global();
                    backend.login(uri, userId, nickname, password);
                    return Boolean.TRUE;
                },
                ok -> {
                    Credentials.save(uri, userId, passwordCopy);
                    connections.addRow(new Object[] {nickname, uri, userId});
                    TributaryNotifier.info(null, TributaryBundle.message("connection.login.success"), uri);
                },
                null);
    }

    private void removeSelected() {
        int row = table.getSelectedRow();
        if (row >= 0) {
            Credentials.forget((String) connections.getValueAt(row, 1), (String) connections.getValueAt(row, 2));
            connections.removeRow(row);
        }
    }

    private void importFromCli() {
        TributaryTasks.run(
                null,
                TributaryBundle.message("settings.import.task"),
                false,
                cancelled -> RtcClients.global().listConnections(),
                this::mergeImported,
                null);
    }

    private void mergeImported(List<CliConnection> imported) {
        List<List<String>> existing = currentConnections();
        for (CliConnection connection : imported) {
            List<String> row = List.of(connection.nickname(), connection.uri(), connection.userName());
            if (!existing.contains(row)) {
                connections.addRow(new Object[] {connection.nickname(), connection.uri(), connection.userName()});
            }
        }
    }

    private List<List<String>> currentConnections() {
        List<List<String>> rows = new ArrayList<>();
        for (int row = 0; row < connections.getRowCount(); row++) {
            rows.add(List.of((String) connections.getValueAt(row, 0), (String) connections.getValueAt(row, 1), (String)
                    connections.getValueAt(row, 2)));
        }
        return rows;
    }

    private static List<List<String>> asRows(List<TributarySettings.Connection> list) {
        List<List<String>> rows = new ArrayList<>();
        for (TributarySettings.Connection c : list) {
            rows.add(List.of(c.nickname, c.uri, c.userId));
        }
        return rows;
    }
}
