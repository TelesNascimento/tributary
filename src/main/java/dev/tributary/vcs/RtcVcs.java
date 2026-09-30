package dev.tributary.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ChangeProvider;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vcs.merge.MergeProvider;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RtcVcs extends AbstractVcs {

    public static final @NonNls String NAME = "RTC";
    public static final VcsKey KEY = createKey(NAME);

    private final RtcChangeProvider changeProvider;
    private final RtcCheckinEnvironment checkinEnvironment;
    private final RtcRollbackEnvironment rollbackEnvironment;
    private final RtcMergeProvider mergeProvider;
    private final Set<Path> scheduledAdds = ConcurrentHashMap.newKeySet();

    public RtcVcs(@NotNull Project project) {
        super(project, NAME);
        this.changeProvider = new RtcChangeProvider(project);
        this.checkinEnvironment = new RtcCheckinEnvironment(this);
        this.rollbackEnvironment = new RtcRollbackEnvironment(this);
        this.mergeProvider = new RtcMergeProvider(project);
    }

    @Override
    public @NotNull String getDisplayName() {
        return NAME;
    }

    @Override
    public @Nullable ChangeProvider getChangeProvider() {
        return changeProvider;
    }

    @Override
    protected @Nullable CheckinEnvironment createCheckinEnvironment() {
        return checkinEnvironment;
    }

    @Override
    protected @Nullable RollbackEnvironment createRollbackEnvironment() {
        return rollbackEnvironment;
    }

    @Override
    public @Nullable MergeProvider getMergeProvider() {
        return mergeProvider;
    }

    public void scheduleAdd(Path path) {
        scheduledAdds.add(path.toAbsolutePath().normalize());
    }

    public void unscheduleAdd(Path path) {
        scheduledAdds.remove(path.toAbsolutePath().normalize());
    }

    public boolean isScheduledAdd(Path path) {
        return scheduledAdds.contains(path.toAbsolutePath().normalize());
    }
}
