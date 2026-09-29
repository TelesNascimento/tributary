package dev.tributary.context;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import dev.tributary.TributaryBundle;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.Component;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.cli.RtcException;
import dev.tributary.context.ContextState.Entry;
import dev.tributary.core.RtcProjectService;
import dev.tributary.core.TributaryTopics;
import dev.tributary.vcs.RtcClients;
import dev.tributary.workitem.WorkItem;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

@Service(Service.Level.PROJECT)
@State(name = "TributaryContexts", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class ContextService implements PersistentStateComponent<ContextState> {

    private final Project project;
    private ContextState state = new ContextState();

    public ContextService(Project project) {
        this.project = project;
    }

    public static ContextService getInstance(Project project) {
        return project.getService(ContextService.class);
    }

    @Override
    public @NotNull ContextState getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull ContextState loaded) {
        this.state = loaded;
    }

    public synchronized Optional<Entry> active() {
        return engine().active();
    }

    public synchronized List<Entry> recent() {
        return engine().recent();
    }

    public Entry start(WorkItem workItem, boolean suspendPrevious) throws RtcException {
        Path sandbox = sandbox();
        RtcBackend backend = RtcClients.forSandbox(sandbox);
        Workspace workspace = workspace(sandbox);
        Entry entry;
        synchronized (this) {
            entry = engine().switchTo(
                            backend,
                            workspace.uuid(),
                            workItem.id(),
                            workItem.summary(),
                            suspendPrevious,
                            outgoing(workspace));
        }
        syncChangeList(entry);
        publish();
        return entry;
    }

    public String checkIn(List<Path> files, String message, boolean includeDeleted) throws RtcException {
        Path sandbox = sandbox();
        RtcBackend backend = RtcClients.forSandbox(sandbox);
        String changeSet;
        synchronized (this) {
            changeSet = engine().checkIn(backend, files, message, includeDeleted);
        }
        publish();
        return changeSet;
    }

    public void reassociate(String changeSet, long fromWorkItem, WorkItem target) throws RtcException {
        RtcBackend backend = RtcClients.forSandbox(sandbox());
        synchronized (this) {
            engine().reassociate(backend, changeSet, fromWorkItem, target.id(), target.summary());
        }
        publish();
    }

    public synchronized void reconcile(Workspace workspace) {
        Set<String> known = new HashSet<>(outgoing(workspace));
        engine().reconcile(known);
    }

    public synchronized void clearActive() {
        engine().deactivate();
        publish();
    }

    private ContextEngine engine() {
        return new ContextEngine(state);
    }

    private Path sandbox() throws RtcException {
        return RtcProjectService.getInstance(project)
                .sandbox()
                .orElseThrow(() ->
                        new RtcException(RtcException.Kind.NOT_A_SANDBOX, TributaryBundle.message("error.no.sandbox")));
    }

    private Workspace workspace(Path sandbox) throws RtcException {
        RtcProjectService service = RtcProjectService.getInstance(project);
        RtcProjectService.Snapshot snapshot = service.snapshot(sandbox)
                .filter(s -> !s.failed() && !s.workspaces().isEmpty())
                .orElseGet(() -> service.refresh(sandbox, true, () -> false));
        if (snapshot.failed()) {
            throw snapshot.error();
        }
        if (snapshot.workspaces().isEmpty()) {
            throw new RtcException(RtcException.Kind.NOT_A_SANDBOX, TributaryBundle.message("error.no.workspace"));
        }
        return snapshot.workspaces().get(0);
    }

    private static Set<String> outgoing(Workspace workspace) {
        Set<String> ids = new HashSet<>();
        for (Component component : workspace.components()) {
            for (ChangeSet changeSet : component.outgoing()) {
                ids.add(changeSet.uuid());
            }
        }
        return ids;
    }

    private void syncChangeList(Entry entry) {
        ChangeListManager manager = ChangeListManager.getInstance(project);
        LocalChangeList list = manager.findChangeList(entry.label());
        if (list == null) {
            list = manager.addChangeList(entry.label(), null);
        }
        manager.setDefaultChangeList(list);
    }

    private void publish() {
        project.getMessageBus().syncPublisher(TributaryTopics.CONTEXT_CHANGED).contextChanged();
    }
}
