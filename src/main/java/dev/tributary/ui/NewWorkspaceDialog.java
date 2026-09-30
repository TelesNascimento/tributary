package dev.tributary.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.WorkspaceNames;
import javax.swing.JComponent;
import org.jetbrains.annotations.Nullable;

public final class NewWorkspaceDialog extends DialogWrapper {

    private final JBTextField name = new JBTextField();
    private final JBCheckBox load = new JBCheckBox(TributaryBundle.message("newworkspace.load"), true);
    private final String sourceName;

    public NewWorkspaceDialog(Project project, String sourceName, String suggestedName) {
        super(project);
        this.sourceName = sourceName;
        setTitle(TributaryBundle.message("newworkspace.title"));
        setOKButtonText(TributaryBundle.message("newworkspace.ok"));
        name.setText(suggestedName);
        init();
    }

    public String workspaceName() {
        return name.getText().strip();
    }

    public boolean loadAfterCreate() {
        return load.isSelected();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JBLabel hint = new JBLabel(TributaryBundle.message("newworkspace.hint"));
        hint.setEnabled(false);
        return FormBuilder.createFormBuilder()
                .addLabeledComponent(TributaryBundle.message("newworkspace.source"), new JBLabel(sourceName))
                .addLabeledComponent(TributaryBundle.message("newworkspace.name"), name)
                .addComponent(hint)
                .addComponent(load)
                .getPanel();
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return name;
    }

    @Override
    protected @Nullable ValidationInfo doValidate() {
        String key = switch (WorkspaceNames.check(name.getText())) {
            case EMPTY -> "newworkspace.error.empty";
            case AT_SIGN -> "newworkspace.error.at";
            case MULTILINE -> "newworkspace.error.multiline";
            case NONE -> null;
        };
        return key == null ? null : new ValidationInfo(TributaryBundle.message(key), name);
    }
}
