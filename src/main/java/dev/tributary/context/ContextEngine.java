package dev.tributary.context;

import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.RtcException;
import dev.tributary.context.ContextState.Entry;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ContextEngine {

    private final ContextState state;
    private final Clock clock;

    public ContextEngine(ContextState state, Clock clock) {
        this.state = state;
        this.clock = clock;
    }

    public ContextEngine(ContextState state) {
        this(state, Clock.systemUTC());
    }

    public Optional<Entry> active() {
        return find(state.activeWorkItem);
    }

    public Optional<Entry> find(long workItemId) {
        return state.contexts.stream().filter(e -> e.workItemId == workItemId).findFirst();
    }

    public List<Entry> recent() {
        List<Entry> sorted = new ArrayList<>(state.contexts);
        sorted.sort(Comparator.comparingLong((Entry e) -> e.lastUsedMillis).reversed());
        return sorted;
    }

    public Entry start(RtcBackend backend, String workspace, long workItemId, String summary) throws RtcException {
        Optional<Entry> existing = find(workItemId);
        if (existing.isPresent()) {
            return reactivate(backend, existing.get());
        }
        Entry entry = new Entry(workItemId, summary, workspace);
        String comment = entry.label();
        String changeSet = backend.createChangeSet(comment, true);
        try {
            backend.addWorkItem(workspace, changeSet, workItemId);
        } catch (RtcException e) {
            entry.changeSets.add(changeSet);
            entry.currentChangeSet = changeSet;
            entry.currentComment = comment;
            state.contexts.add(entry);
            throw e;
        }
        entry.changeSets.add(changeSet);
        entry.currentChangeSet = changeSet;
        entry.currentComment = comment;
        return activate(entry);
    }

    public Entry switchTo(
            RtcBackend backend,
            String workspace,
            long workItemId,
            String summary,
            boolean suspendPrevious,
            Set<String> outgoing)
            throws RtcException {
        Optional<Entry> previous = active();
        if (previous.isPresent() && previous.get().workItemId != workItemId && suspendPrevious) {
            List<String> toSuspend = previous.get().changeSets.stream()
                    .filter(outgoing::contains)
                    .toList();
            if (!toSuspend.isEmpty()) {
                backend.suspend(toSuspend);
                previous.get().suspended = true;
            }
        }
        return start(backend, workspace, workItemId, summary);
    }

    public String checkIn(RtcBackend backend, List<Path> files, String message, boolean includeDeleted)
            throws RtcException {
        Entry entry = active().orElseThrow(() -> new IllegalStateException("No active work item context"));
        String comment = message == null ? "" : message.trim();
        boolean sameChangeSet = comment.isEmpty() || comment.equalsIgnoreCase(entry.currentComment.trim());
        if (sameChangeSet && !entry.currentChangeSet.isEmpty()) {
            backend.checkinTo(entry.currentChangeSet, files, includeDeleted);
            touch(entry);
            return entry.currentChangeSet;
        }
        String changeSet = backend.createChangeSet(comment, true);
        entry.changeSets.add(changeSet);
        entry.currentChangeSet = changeSet;
        entry.currentComment = comment;
        backend.addWorkItem(entry.workspace, changeSet, entry.workItemId);
        backend.checkinTo(changeSet, files, includeDeleted);
        touch(entry);
        return changeSet;
    }

    public void reassociate(RtcBackend backend, String changeSet, long fromWorkItem, long toWorkItem, String toSummary)
            throws RtcException {
        Entry from = find(fromWorkItem).orElseThrow(() -> new IllegalStateException("Unknown work item context"));
        backend.removeWorkItem(from.workspace, changeSet, fromWorkItem);
        backend.addWorkItem(from.workspace, changeSet, toWorkItem);
        from.changeSets.remove(changeSet);
        if (changeSet.equals(from.currentChangeSet)) {
            from.currentChangeSet = "";
        }
        Entry target = find(toWorkItem).orElseGet(() -> {
            Entry created = new Entry(toWorkItem, toSummary, from.workspace);
            state.contexts.add(created);
            return created;
        });
        target.changeSets.add(changeSet);
        touch(target);
    }

    public void reconcile(Set<String> knownChangeSets) {
        for (Entry entry : state.contexts) {
            entry.changeSets.removeIf(id -> !knownChangeSets.contains(id));
            if (!entry.changeSets.contains(entry.currentChangeSet)) {
                entry.currentChangeSet = "";
            }
        }
        state.contexts.removeIf(e -> e.changeSets.isEmpty() && e.workItemId != state.activeWorkItem);
    }

    public void deactivate() {
        state.activeWorkItem = 0;
    }

    private Entry reactivate(RtcBackend backend, Entry entry) throws RtcException {
        if (entry.suspended && !entry.changeSets.isEmpty()) {
            backend.resume(entry.changeSets);
            entry.suspended = false;
        }
        if (!entry.currentChangeSet.isEmpty()) {
            backend.setCurrentChangeSet(entry.currentChangeSet);
        }
        return activate(entry);
    }

    private Entry activate(Entry entry) {
        if (!state.contexts.contains(entry)) {
            state.contexts.add(entry);
        }
        state.activeWorkItem = entry.workItemId;
        touch(entry);
        return entry;
    }

    private void touch(Entry entry) {
        entry.lastUsedMillis = clock.millis();
    }
}
