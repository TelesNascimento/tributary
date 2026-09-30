package dev.tributary.cli;

public record CliResult(int exitCode, String stdout, String stderr, boolean timedOut, boolean cancelled) {

    public CliResult(int exitCode, String stdout, String stderr, boolean timedOut) {
        this(exitCode, stdout, stderr, timedOut, false);
    }

    public boolean isSuccess() {
        return exitCode == 0 && !timedOut && !cancelled;
    }
}
