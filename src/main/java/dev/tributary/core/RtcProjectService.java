package dev.tributary.core;

import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.cli.RtcException;
import dev.tributary.cli.SandboxDetector;
import dev.tributary.vcs.RtcClients;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

@Service(Service.Level.PROJECT)
public final class RtcProjectService {

    public record Snapshot(List<Workspace> workspaces, Instant at, RtcException error) {

        public boolean failed() {
            return error != null;
        }
    }

    private final Project project;
    private final Map<Path, Snapshot> cache = new ConcurrentHashMap<>();

    public RtcProjectService(Project project) {
        this.project = project;
    }

    public static RtcProjectService getInstance(Project project) {
        return project.getService(RtcProjectService.class);
    }

    public Optional<Path> sandbox() {
        String base = project.getBasePath();
        return base == null ? Optional.empty() : SandboxDetector.findRoot(Path.of(base));
    }

    public Optional<Snapshot> snapshot(Path sandbox) {
        return Optional.ofNullable(cache.get(key(sandbox)));
    }

    public Snapshot refresh(Path sandbox, boolean fullRefresh, BooleanSupplier cancelled) {
        Snapshot snapshot;
        try {
            RtcBackend backend = RtcClients.forSandbox(sandbox).withCancel(cancelled);
            snapshot = new Snapshot(backend.status(fullRefresh), Instant.now(), null);
        } catch (RtcException e) {
            snapshot = new Snapshot(List.of(), Instant.now(), e);
        }
        cache.put(key(sandbox), snapshot);
        project.getMessageBus().syncPublisher(TributaryTopics.STATUS_CHANGED).statusChanged(sandbox);
        return snapshot;
    }

    private static Path key(Path sandbox) {
        return sandbox.toAbsolutePath().normalize();
    }
}
