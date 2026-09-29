package dev.tributary.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

public final class CliRunner {

    public record Request(
            Path workingDir,
            Duration timeout,
            BooleanSupplier cancelled,
            String stdin,
            boolean interactive,
            List<String> args) {

        public static Request of(Path workingDir, Duration timeout, String... args) {
            return new Request(workingDir, timeout, () -> false, null, false, List.of(args));
        }

        public Request withCancel(BooleanSupplier cancel) {
            return new Request(workingDir, timeout, cancel, stdin, interactive, args);
        }

        public Request withStdin(String input) {
            return new Request(workingDir, timeout, cancelled, input, true, args);
        }
    }

    private static final long POLL_MS = 100;

    private final Path executable;
    private final Charset charset;

    public CliRunner(Path executable, Charset charset) {
        this.executable = executable;
        this.charset = charset;
    }

    public CliResult run(Path workingDir, Duration timeout, String... args) throws IOException, InterruptedException {
        return run(Request.of(workingDir, timeout, args));
    }

    public CliResult run(Request request) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(executable.toString());
        command.add("-nl");
        command.add("en");
        if (!request.interactive()) {
            command.add("--non-interactive");
        }
        command.addAll(request.args());
        ProcessBuilder builder = new ProcessBuilder(command);
        if (request.workingDir() != null) {
            builder.directory(request.workingDir().toFile());
        }
        Process process = builder.start();
        writeStdin(process, request.stdin());
        CompletableFuture<String> out = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
        CompletableFuture<String> err = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));

        long deadline = System.nanoTime() + request.timeout().toNanos();
        boolean finished = false;
        boolean cancelled = false;
        while (!finished) {
            finished = process.waitFor(POLL_MS, TimeUnit.MILLISECONDS);
            if (finished) {
                break;
            }
            if (request.cancelled().getAsBoolean()) {
                cancelled = true;
                break;
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            process.waitFor();
        }
        return new CliResult(
                finished ? process.exitValue() : -1,
                join(out, finished),
                join(err, finished),
                !finished && !cancelled,
                cancelled);
    }

    private static String join(CompletableFuture<String> stream, boolean finished) {
        if (finished) {
            return stream.join();
        }
        try {
            return stream.get(2, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "";
        }
    }

    private static void writeStdin(Process process, String stdin) throws IOException {
        try (OutputStream stream = process.getOutputStream()) {
            if (stdin != null) {
                stream.write(stdin.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }
        }
    }

    private String read(InputStream stream) {
        try {
            return new String(stream.readAllBytes(), charset);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
