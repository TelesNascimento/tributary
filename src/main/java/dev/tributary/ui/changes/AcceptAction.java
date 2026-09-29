package dev.tributary.ui.changes;

import com.intellij.icons.AllIcons;
import dev.tributary.TributaryBundle;

public final class AcceptAction extends ChangeSetsAction {

    public AcceptAction() {
        super(
                ChangeSetsDialog.Mode.ACCEPT,
                TributaryBundle.message("action.accept.text"),
                TributaryBundle.message("action.accept.description"),
                AllIcons.Actions.CheckOut);
    }
}
