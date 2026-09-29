package dev.tributary.backend;

import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.CliConnection;
import dev.tributary.cli.Model.Conflict;
import dev.tributary.cli.Model.ConflictSide;
import dev.tributary.cli.Model.FlowTarget;
import dev.tributary.cli.Model.RemoteWorkspace;
import dev.tributary.cli.Model.Resolution;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.cli.RtcException;
import java.nio.file.Path;
import java.util.List;

public interface RtcBackend {

    Path sandboxRoot();

    RtcBackend withCancel(java.util.function.BooleanSupplier cancelled);

    List<CliConnection> listConnections() throws RtcException;

    void login(String repositoryUri, String userId, String nickname, char[] password) throws RtcException;

    List<Workspace> status(boolean refreshLocal) throws RtcException;

    String baseContent(Path file) throws RtcException;

    String fileContent(String itemUuid, String stateId) throws RtcException;

    String checkin(List<Path> files, String comment, String workItem, boolean includeDeleted) throws RtcException;

    String checkinTo(String changeSet, List<Path> files, boolean includeDeleted) throws RtcException;

    String createChangeSet(String comment, boolean makeCurrent) throws RtcException;

    void setCurrentChangeSet(String changeSet) throws RtcException;

    void setChangeSetComment(String changeSet, String comment) throws RtcException;

    void completeChangeSet(String changeSet) throws RtcException;

    void addWorkItem(String workspace, String changeSet, long workItem) throws RtcException;

    void removeWorkItem(String workspace, String changeSet, long workItem) throws RtcException;

    String suspend(List<String> changeSets) throws RtcException;

    String resume(List<String> changeSets) throws RtcException;

    String discard(String workspace, List<String> changeSets) throws RtcException;

    String deliver(List<String> changeSets) throws RtcException;

    String accept(List<String> changeSets, String component) throws RtcException;

    String undo(List<Path> files) throws RtcException;

    List<Conflict> conflicts() throws RtcException;

    String conflictContent(Path file, ConflictSide side) throws RtcException;

    void resolveConflict(Path file, Resolution resolution) throws RtcException;

    List<ChangeSet> listChangeSets(String workspace, int max) throws RtcException;

    List<ChangeSet> listChanges(String changeSet) throws RtcException;

    List<ChangeSet> history(Path file, int max) throws RtcException;

    List<FlowTarget> listFlowTargets(String workspace) throws RtcException;

    List<RemoteWorkspace> listWorkspaces(String repositoryUri, int max) throws RtcException;

    List<RemoteWorkspace> listProjectAreas(String repositoryUri) throws RtcException;

    List<RemoteWorkspace> listStreams(String repositoryUri, String projectArea, int max) throws RtcException;

    List<String> listComponents(String repositoryUri, String workspaceUuid) throws RtcException;

    String load(String repositoryUri, Path targetDir, String workspaceUuid, List<String> components)
            throws RtcException;

    String unload(String workspaceUuid, boolean deleteFromDisk) throws RtcException;
}
