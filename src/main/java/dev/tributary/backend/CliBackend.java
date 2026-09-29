package dev.tributary.backend;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.CliResult;
import dev.tributary.cli.CliRunner;
import dev.tributary.cli.ErrorCodes;
import dev.tributary.cli.Model.ChangeSet;
import dev.tributary.cli.Model.CliConnection;
import dev.tributary.cli.Model.Conflict;
import dev.tributary.cli.Model.ConflictSide;
import dev.tributary.cli.Model.FlowTarget;
import dev.tributary.cli.Model.RemoteWorkspace;
import dev.tributary.cli.Model.Resolution;
import dev.tributary.cli.Model.Workspace;
import dev.tributary.cli.RtcException;
import dev.tributary.cli.StatusParser;
import dev.tributary.cli.UnifiedPatch;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class CliBackend implements RtcBackend {

    private static final Duration TIMEOUT = Duration.ofSeconds(120);
    private static final Duration LONG_TIMEOUT = Duration.ofMinutes(30);
    private static final int LOCK_RETRIES = 4;
    private static final long LOCK_WAIT_MS = 1500;
    private static final ConcurrentHashMap<Path, ReentrantLock> LOCKS = new ConcurrentHashMap<>();

    private final CliRunner runner;
    private final Path sandboxRoot;
    private final BooleanSupplier cancelled;

    public CliBackend(CliRunner runner, Path sandboxRoot) {
        this(runner, sandboxRoot, () -> false);
    }

    private CliBackend(CliRunner runner, Path sandboxRoot, BooleanSupplier cancelled) {
        this.runner = runner;
        this.sandboxRoot = sandboxRoot;
        this.cancelled = cancelled;
    }

    @Override
    public Path sandboxRoot() {
        return sandboxRoot;
    }

    @Override
    public CliBackend withCancel(BooleanSupplier cancel) {
        return new CliBackend(runner, sandboxRoot, cancel);
    }

    @Override
    public List<CliConnection> listConnections() throws RtcException {
        return parse(
                "list connections",
                execute(TIMEOUT, args("list", "connections", "-j")),
                StatusParser::parseConnections);
    }

    @Override
    public void login(String repositoryUri, String userId, String nickname, char[] password) throws RtcException {
        List<String> args = args("--no-mask", "login", "-r", repositoryUri, "-u", userId, "-c");
        if (nickname != null && !nickname.isBlank()) {
            args.add("-n");
            args.add(nickname);
        }
        CliResult result;
        try {
            result = runner.run(new CliRunner.Request(sandboxRoot, TIMEOUT, cancelled, null, true, args)
                    .withStdin(new String(password) + System.lineSeparator()));
        } catch (IOException e) {
            throw new RtcException(
                    RtcException.Kind.CLI_MISSING, TributaryBundle.message("error.cli.start", e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RtcException(RtcException.Kind.CANCELLED, TributaryBundle.message("error.cancelled"));
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
        if (!result.isSuccess()) {
            throw ErrorCodes.classify(result, "scm login -r " + repositoryUri + " -u " + userId);
        }
    }

    @Override
    public List<Workspace> status(boolean refreshLocal) throws RtcException {
        List<String> args = args("status", "-j");
        if (!refreshLocal) {
            args.add("-N");
        }
        args.add("-d");
        args.add(sandboxRoot.toString());
        return parse("status", execute(TIMEOUT, args), StatusParser::parse);
    }

    @Override
    public String baseContent(Path file) throws RtcException {
        String local = "";
        try {
            if (Files.isRegularFile(file)) {
                local = Files.readString(file);
            }
        } catch (IOException e) {
            throw new RtcException(
                    RtcException.Kind.FAILED, TributaryBundle.message("error.read.file", file, e.getMessage()));
        }
        String patch = execute(TIMEOUT, args("diff", "-d", sandboxRoot.toString(), "-p", "file", file.toString()));
        return UnifiedPatch.reverse(local, patch);
    }

    @Override
    public String fileContent(String itemUuid, String stateId) throws RtcException {
        Path target = null;
        try {
            target = Files.createTempFile("tributary-", ".content");
            execute(TIMEOUT, args("get", "file", "-o", itemUuid, stateId, target.toString()));
            return Files.readString(target);
        } catch (java.nio.charset.MalformedInputException e) {
            try {
                return new String(Files.readAllBytes(target), java.nio.charset.StandardCharsets.ISO_8859_1);
            } catch (IOException io) {
                throw readFailure(target, io);
            }
        } catch (IOException e) {
            throw readFailure(target, e);
        } finally {
            if (target != null) {
                try {
                    Files.deleteIfExists(target);
                } catch (IOException ignored) {
                    target.toFile().deleteOnExit();
                }
            }
        }
    }

    private static RtcException readFailure(Path file, IOException e) {
        return new RtcException(
                RtcException.Kind.FAILED, TributaryBundle.message("error.read.file", file, e.getMessage()));
    }

    @Override
    public String checkin(List<Path> files, String comment, String workItem, boolean includeDeleted)
            throws RtcException {
        List<String> args = args("checkin", "-d", sandboxRoot.toString(), "--comment", oneLine(comment));
        if (workItem != null && !workItem.isBlank()) {
            args.add("-W");
            args.add(workItem.trim());
        }
        return execute(LONG_TIMEOUT, withFiles(args, files, includeDeleted));
    }

    @Override
    public String checkinTo(String changeSet, List<Path> files, boolean includeDeleted) throws RtcException {
        List<String> args = args("checkin", "-d", sandboxRoot.toString(), "-c", changeSet);
        return execute(LONG_TIMEOUT, withFiles(args, files, includeDeleted));
    }

    @Override
    public String createChangeSet(String comment, boolean makeCurrent) throws RtcException {
        List<String> args = args("create", "changeset", "-j", "-d", sandboxRoot.toString());
        if (!makeCurrent) {
            args.add("-n");
        }
        args.add(oneLine(comment));
        String output = execute(TIMEOUT, args);
        String uuid = StatusParser.firstUuid(output);
        if (uuid == null) {
            throw new RtcException(
                    RtcException.Kind.FAILED,
                    TributaryBundle.message("error.unexpected.response", "create changeset", output.strip()));
        }
        return uuid;
    }

    @Override
    public void setCurrentChangeSet(String changeSet) throws RtcException {
        execute(TIMEOUT, args("set", "changeset", "--current", "-d", sandboxRoot.toString(), changeSet));
    }

    @Override
    public void setChangeSetComment(String changeSet, String comment) throws RtcException {
        execute(
                TIMEOUT,
                args("set", "changeset", "--comment", oneLine(comment), "-d", sandboxRoot.toString(), changeSet));
    }

    @Override
    public void completeChangeSet(String changeSet) throws RtcException {
        execute(TIMEOUT, args("set", "changeset", "--complete", "-d", sandboxRoot.toString(), changeSet));
    }

    @Override
    public void addWorkItem(String workspace, String changeSet, long workItem) throws RtcException {
        execute(TIMEOUT, args("add", "workitem", "-w", workspace, changeSet, Long.toString(workItem)));
    }

    @Override
    public void removeWorkItem(String workspace, String changeSet, long workItem) throws RtcException {
        execute(TIMEOUT, args("remove", "workitem", "-w", workspace, changeSet, Long.toString(workItem)));
    }

    @Override
    public String suspend(List<String> changeSets) throws RtcException {
        List<String> args = args("suspend", "-d", sandboxRoot.toString());
        args.addAll(changeSets);
        return execute(LONG_TIMEOUT, args);
    }

    @Override
    public String resume(List<String> changeSets) throws RtcException {
        List<String> args = args("resume", "changeset", "-d", sandboxRoot.toString());
        args.addAll(changeSets);
        return execute(LONG_TIMEOUT, args);
    }

    @Override
    public String discard(String workspace, List<String> changeSets) throws RtcException {
        List<String> args = args("discard", "-w", workspace);
        args.addAll(changeSets);
        return execute(LONG_TIMEOUT, args);
    }

    @Override
    public String deliver(List<String> changeSets) throws RtcException {
        List<String> args = args("deliver");
        if (!changeSets.isEmpty()) {
            args.add("-c");
            args.addAll(changeSets);
        }
        return execute(LONG_TIMEOUT, args);
    }

    @Override
    public String accept(List<String> changeSets, String component) throws RtcException {
        List<String> args = args("accept", "-d", sandboxRoot.toString());
        if (!changeSets.isEmpty()) {
            args.add("-c");
            args.addAll(changeSets);
        } else if (component != null && !component.isBlank()) {
            args.add("-C");
            args.add(component);
        }
        return execute(LONG_TIMEOUT, args);
    }

    @Override
    public String undo(List<Path> files) throws RtcException {
        List<String> args = args("undo", "change");
        files.forEach(f -> args.add(f.toString()));
        return execute(LONG_TIMEOUT, args);
    }

    @Override
    public List<Conflict> conflicts() throws RtcException {
        return parse("show conflicts", execute(TIMEOUT, args("show", "conflicts", "-j")), StatusParser::parseConflicts);
    }

    @Override
    public String conflictContent(Path file, ConflictSide side) throws RtcException {
        String output = execute(TIMEOUT, args("show", "conflicts", "--content", side.flag(), file.toString()));
        return StatusParser.parseConflictContent(output);
    }

    @Override
    public void resolveConflict(Path file, Resolution resolution) throws RtcException {
        execute(TIMEOUT, args("resolve", "conflict", resolution.flag(), "-d", sandboxRoot.toString(), file.toString()));
    }

    @Override
    public List<ChangeSet> listChangeSets(String workspace, int max) throws RtcException {
        String json = execute(TIMEOUT, args("list", "changesets", "-j", "-m", String.valueOf(max), "-w", workspace));
        return parse("list changesets", json, StatusParser::parseChangeSets);
    }

    @Override
    public List<ChangeSet> listChanges(String changeSet) throws RtcException {
        return parse(
                "list changes",
                execute(TIMEOUT, args("list", "changes", "-j", changeSet)),
                StatusParser::parseChangeSets);
    }

    @Override
    public List<ChangeSet> history(Path file, int max) throws RtcException {
        String json = execute(
                TIMEOUT,
                args(
                        "show",
                        "history",
                        "-j",
                        "-m",
                        String.valueOf(max),
                        "-d",
                        sandboxRoot.toString(),
                        file.toString()));
        return parse("show history", json, StatusParser::parseChangeSets);
    }

    @Override
    public List<FlowTarget> listFlowTargets(String workspace) throws RtcException {
        return parse(
                "list flowtargets",
                execute(TIMEOUT, args("list", "flowtargets", "-j", workspace)),
                StatusParser::parseFlowTargets);
    }

    @Override
    public List<RemoteWorkspace> listWorkspaces(String repositoryUri, int max) throws RtcException {
        String json =
                execute(TIMEOUT, args("list", "workspaces", "-r", repositoryUri, "-j", "-m", String.valueOf(max)));
        return parse("list workspaces", json, StatusParser::parseWorkspaceList);
    }

    @Override
    public List<RemoteWorkspace> listProjectAreas(String repositoryUri) throws RtcException {
        return parse(
                "list projectareas",
                execute(TIMEOUT, args("list", "projectareas", "-r", repositoryUri, "-j")),
                StatusParser::parseNamedItems);
    }

    @Override
    public List<RemoteWorkspace> listStreams(String repositoryUri, String projectArea, int max) throws RtcException {
        String json = execute(
                TIMEOUT,
                args(
                        "list",
                        "streams",
                        "-r",
                        repositoryUri,
                        "-j",
                        "--projectarea",
                        projectArea,
                        "-m",
                        String.valueOf(max)));
        return parse("list streams", json, StatusParser::parseNamedItems);
    }

    @Override
    public List<String> listComponents(String repositoryUri, String workspaceUuid) throws RtcException {
        String json = execute(TIMEOUT, args("list", "components", "-r", repositoryUri, "-j", workspaceUuid));
        return parse("list components", json, StatusParser::parseComponentNames);
    }

    @Override
    public String load(String repositoryUri, Path targetDir, String workspaceUuid, List<String> components)
            throws RtcException {
        List<String> args = args("load", "-r", repositoryUri, "-d", targetDir.toString(), workspaceUuid);
        args.addAll(components);
        return execute(LONG_TIMEOUT, args);
    }

    @Override
    public String unload(String workspaceUuid, boolean deleteFromDisk) throws RtcException {
        List<String> args = args("unload", "-w", workspaceUuid, "-d", sandboxRoot.toString());
        if (deleteFromDisk) {
            args.add("-D");
        }
        return execute(LONG_TIMEOUT, args);
    }

    private static List<String> args(String... command) {
        return new ArrayList<>(List.of(command));
    }

    private static List<String> withFiles(List<String> args, List<Path> files, boolean includeDeleted) {
        if (includeDeleted) {
            args.add("-D");
        }
        files.forEach(f -> args.add(f.toString()));
        return args;
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ").trim();
    }

    private static <T> T parse(String what, String json, Function<String, T> parser) throws RtcException {
        try {
            return parser.apply(json);
        } catch (RuntimeException e) {
            throw new RtcException(
                    RtcException.Kind.FAILED,
                    TributaryBundle.message("error.unexpected.response", what, e.getMessage()));
        }
    }

    private String execute(Duration timeout, List<String> args) throws RtcException {
        ReentrantLock lock = LOCKS.computeIfAbsent(sandboxRoot.toAbsolutePath().normalize(), k -> new ReentrantLock());
        lock.lock();
        try {
            RtcException last = null;
            for (int attempt = 0; attempt < LOCK_RETRIES; attempt++) {
                try {
                    return executeOnce(timeout, args);
                } catch (RtcException e) {
                    if (e.kind() != RtcException.Kind.LOCKED) {
                        throw e;
                    }
                    last = e;
                    pause();
                }
            }
            throw last;
        } finally {
            lock.unlock();
        }
    }

    private static void assertNotOnEdt() {
        Application application = ApplicationManager.getApplication();
        if (application != null && application.isDispatchThread()) {
            throw new IllegalStateException("RTC backend calls must not run on the UI thread");
        }
    }

    private void pause() throws RtcException {
        long until = System.nanoTime() + LOCK_WAIT_MS * 1_000_000;
        try {
            while (System.nanoTime() < until) {
                if (cancelled.getAsBoolean()) {
                    throw new RtcException(RtcException.Kind.CANCELLED, TributaryBundle.message("error.cancelled"));
                }
                Thread.sleep(100);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RtcException(RtcException.Kind.CANCELLED, TributaryBundle.message("error.cancelled"));
        }
    }

    private String executeOnce(Duration timeout, List<String> args) throws RtcException {
        assertNotOnEdt();
        CliResult result;
        try {
            result = runner.run(new CliRunner.Request(sandboxRoot, timeout, cancelled, null, false, args));
        } catch (IOException e) {
            throw new RtcException(
                    RtcException.Kind.CLI_MISSING, TributaryBundle.message("error.cli.start", e.getMessage()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RtcException(RtcException.Kind.CANCELLED, TributaryBundle.message("error.cancelled"));
        }
        if (result.isSuccess()) {
            return result.stdout();
        }
        throw ErrorCodes.classify(result, "scm " + String.join(" ", args));
    }
}
