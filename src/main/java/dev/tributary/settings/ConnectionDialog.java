package dev.tributary.settings;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBPasswordField;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import dev.tributary.TributaryBundle;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nullable;

public final class ConnectionDialog extends DialogWrapper {

    private final JBTextField uri = new JBTextField("https://", 40);
    private final JBTextField user = new JBTextField();
    private final JBTextField nickname = new JBTextField();
    private final JBPasswordField password = new JBPasswordField();

    public ConnectionDialog(@Nullable Project project) {
        super(project);
        setTitle(TributaryBundle.message("connection.dialog.title"));
        setOKButtonText(TributaryBundle.message("connection.dialog.ok"));
        init();
    }

    public String uri() {
        String value = uri.getText().trim();
        return value.endsWith("/") ? value : value + "/";
    }

    public String userId() {
        return user.getText().trim();
    }

    public String nickname() {
        String value = nickname.getText().trim();
        return value.isEmpty() ? userId() : value;
    }

    public char[] password() {
        return password.getPassword();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(TributaryBundle.message("connection.uri"), uri)
                .addLabeledComponent(TributaryBundle.message("connection.user"), user)
                .addLabeledComponent(TributaryBundle.message("connection.nickname"), nickname)
                .addLabeledComponent(TributaryBundle.message("connection.password"), password)
                .getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return user;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        if (!uri.getText().trim().startsWith("http")) {
            return new ValidationInfo(TributaryBundle.message("connection.error.uri"), uri);
        }
        if (userId().isEmpty()) {
            return new ValidationInfo(TributaryBundle.message("connection.error.user"), user);
        }
        if (password.getPassword().length == 0) {
            return new ValidationInfo(TributaryBundle.message("connection.error.password"), password);
        }
        return null;
    }
}
