package dev.tributary.workitem;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.RtcException;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

public final class BridgeProcess implements AutoCloseable {

    private final Process process;
    private final BufferedWriter stdin;
    private final Map<Integer, CompletableFuture<JsonObject>> pending = new ConcurrentHashMap<>();
    private final AtomicInteger ids = new AtomicInteger();
    private final StringBuffer stderrTail = new StringBuffer();
    private volatile boolean alive = true;

    private BridgeProcess(Process process) {
        this.process = process;
        this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        startReader(process);
        startErrorDrain(process);
    }

    public static BridgeProcess start(List<String> command) throws RtcException {
        try {
            return new BridgeProcess(new ProcessBuilder(command).start());
        } catch (IOException e) {
            throw new RtcException(
                    RtcException.Kind.CLI_MISSING, TributaryBundle.message("error.bridge.start", e.getMessage()));
        }
    }

    public boolean isAlive() {
        return alive && process.isAlive();
    }

    public JsonElement call(String method, JsonObject params, Duration timeout) throws RtcException {
        int id = ids.incrementAndGet();
        JsonObject request = new JsonObject();
        request.addProperty("jsonrpc", "2.0");
        request.addProperty("id", id);
        request.addProperty("method", method);
        request.add("params", params == null ? new JsonObject() : params);
        CompletableFuture<JsonObject> future = new CompletableFuture<>();
        pending.put(id, future);
        try {
            synchronized (stdin) {
                stdin.write(request.toString());
                stdin.newLine();
                stdin.flush();
            }
            JsonObject response = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response.has("error")) {
                throw remoteError(response.getAsJsonObject("error"));
            }
            return response.get("result");
        } catch (IOException e) {
            alive = false;
            throw new RtcException(RtcException.Kind.FAILED, TributaryBundle.message("error.bridge.died"), tail());
        } catch (TimeoutException e) {
            throw new RtcException(RtcException.Kind.TIMEOUT, TributaryBundle.message("error.timeout"), tail());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RtcException(RtcException.Kind.CANCELLED, TributaryBundle.message("error.cancelled"));
        } catch (ExecutionException e) {
            throw new RtcException(RtcException.Kind.FAILED, TributaryBundle.message("error.bridge.died"), tail());
        } finally {
            pending.remove(id);
        }
    }

    @Override
    public void close() {
        alive = false;
        try {
            call("shutdown", null, Duration.ofSeconds(2));
        } catch (RtcException ignored) {
            process.destroyForcibly();
        }
        process.destroy();
    }

    private static RtcException remoteError(JsonObject error) {
        String message = error.has("message") ? error.get("message").getAsString() : "";
        RtcException.Kind kind = RtcException.Kind.FAILED;
        if (error.has("data")
                && error.get("data").isJsonObject()
                && error.getAsJsonObject("data").has("kind")) {
            try {
                kind = RtcException.Kind.valueOf(
                        error.getAsJsonObject("data").get("kind").getAsString());
            } catch (IllegalArgumentException ignored) {
                kind = RtcException.Kind.FAILED;
            }
        }
        return new RtcException(kind, message);
    }

    private String tail() {
        return stderrTail.toString();
    }

    private void startReader(Process started) {
        Thread reader = new Thread(
                () -> {
                    try (BufferedReader out = new BufferedReader(
                            new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = out.readLine()) != null) {
                            deliver(line);
                        }
                    } catch (IOException ignored) {
                        alive = false;
                    } finally {
                        alive = false;
                        pending.values().forEach(f -> f.completeExceptionally(new IOException("bridge closed")));
                    }
                },
                "tributary-bridge-reader");
        reader.setDaemon(true);
        reader.start();
    }

    private void deliver(String line) {
        try {
            JsonObject response = JsonParser.parseString(line).getAsJsonObject();
            JsonElement id = response.get("id");
            if (id != null && !id.isJsonNull()) {
                CompletableFuture<JsonObject> future = pending.get(id.getAsInt());
                if (future != null) {
                    future.complete(response);
                }
            }
        } catch (RuntimeException ignored) {
            stderrTail.append(line).append('\n');
        }
    }

    private void startErrorDrain(Process started) {
        Thread drain = new Thread(
                () -> {
                    try (BufferedReader err = new BufferedReader(
                            new InputStreamReader(started.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = err.readLine()) != null) {
                            stderrTail.append(line).append('\n');
                            if (stderrTail.length() > 8000) {
                                stderrTail.delete(0, stderrTail.length() - 4000);
                            }
                        }
                    } catch (IOException ignored) {
                        alive = false;
                    }
                },
                "tributary-bridge-stderr");
        drain.setDaemon(true);
        drain.start();
    }
}
