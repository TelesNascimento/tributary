package dev.tributary.ui.changes;

import com.intellij.icons.AllIcons;
import dev.tributary.TributaryBundle;

public final class DeliverAction extends ChangeSetsAction {

    public DeliverAction() {
        super(
                ChangeSetsDialog.Mode.DELIVER,
                TributaryBundle.message("action.deliver.text"),
                TributaryBundle.message("action.deliver.description"),
                AllIcons.Vcs.Push);
    }
}
