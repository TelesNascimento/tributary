package dev.tributary.vcs;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsDirectoryMapping;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.SandboxDetector;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;

public final class RtcSandboxActivator implements StartupActivity.DumbAware {

    @Override
    public void runActivity(@NotNull Project project) {
        dev.tributary.settings.ConnectionsBootstrap.importIfEmpty();
        String base = project.getBasePath();
        if (base == null) {
            return;
        }
        Optional<Path> sandbox = SandboxDetector.findRoot(Path.of(base));
        if (sandbox.isEmpty()) {
            return;
        }
        String root = sandbox.get().toString().replace('\\', '/');
        ProjectLevelVcsManager manager = ProjectLevelVcsManager.getInstance(project);
        boolean mapped = manager.getDirectoryMappings().stream()
                .anyMatch(m -> RtcVcs.NAME.equals(m.getVcs())
                        && root.equals(m.getDirectory().replace('\\', '/')));
        if (mapped) {
            return;
        }
        List<VcsDirectoryMapping> mappings = new ArrayList<>(manager.getDirectoryMappings());
        mappings.removeIf(m -> m.isDefaultMapping() && m.getVcs().isEmpty());
        mappings.add(new VcsDirectoryMapping(root, RtcVcs.NAME));
        manager.setDirectoryMappings(mappings);
        NotificationGroupManager.getInstance()
                .getNotificationGroup("Tributary")
                .createNotification(
                        TributaryBundle.message("sandbox.detected.title"),
                        TributaryBundle.message("sandbox.detected.body", root),
                        NotificationType.INFORMATION)
                .notify(project);
    }
}
