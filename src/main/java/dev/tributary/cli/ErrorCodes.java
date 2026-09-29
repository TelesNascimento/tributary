package dev.tributary.cli;

import dev.tributary.TributaryBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ErrorCodes {

    public static final int NOT_A_SANDBOX = 5;
    public static final int PASSWORD_REQUIRED = 3;
    public static final int NOT_FOUND = 25;
    public static final int LOCKED = 27;
    public static final int CONFLICT = 11;
    public static final int UNCOMMITTED_CHANGES = 34;

    private static final Pattern PROBLEM_LINE = Pattern.compile("^Problem running '[^']*':\\s*$");
    private static final Pattern PASSWORD_PROMPT = Pattern.compile("^Password \\(.*\\):\\s*$");

    private ErrorCodes() {}

    public static RtcException classify(CliResult result, String command) {
        String output = (result.stderr() + "\n" + result.stdout()).trim();
        String details = Redactor.redact(command + " -> exit " + result.exitCode() + "\n" + output);
        if (result.cancelled()) {
            return new RtcException(RtcException.Kind.CANCELLED, TributaryBundle.message("error.cancelled"), details);
        }
        if (result.timedOut()) {
            return new RtcException(RtcException.Kind.TIMEOUT, TributaryBundle.message("error.timeout"), details);
        }
        String reason = reason(output);
        return switch (result.exitCode()) {
            case LOCKED -> new RtcException(RtcException.Kind.LOCKED, TributaryBundle.message("error.locked"), details);
            case NOT_A_SANDBOX ->
                new RtcException(
                        RtcException.Kind.NOT_A_SANDBOX, TributaryBundle.message("error.not.sandbox", reason), details);
            case NOT_FOUND -> new RtcException(RtcException.Kind.NOT_FOUND, reason, details);
            case CONFLICT ->
                new RtcException(RtcException.Kind.CONFLICT, TributaryBundle.message("error.conflict"), details);
            case UNCOMMITTED_CHANGES ->
                new RtcException(
                        RtcException.Kind.UNCOMMITTED_CHANGES, TributaryBundle.message("error.uncommitted"), details);
            case PASSWORD_REQUIRED ->
                new RtcException(RtcException.Kind.AUTH_REQUIRED, TributaryBundle.message("error.auth"), details);
            default ->
                isPasswordPrompt(output)
                        ? new RtcException(
                                RtcException.Kind.AUTH_REQUIRED, TributaryBundle.message("error.auth"), details)
                        : new RtcException(
                                RtcException.Kind.FAILED,
                                TributaryBundle.message("error.failed", result.exitCode(), reason),
                                details);
        };
    }

    static String reason(String output) {
        String[] lines = output.split("\\R");
        boolean afterProblem = false;
        for (String line : lines) {
            if (PROBLEM_LINE.matcher(line.stripTrailing()).matches()) {
                afterProblem = true;
                continue;
            }
            if (afterProblem && !line.isBlank()) {
                return line.strip();
            }
        }
        for (String line : lines) {
            if (!line.isBlank() && !line.startsWith("scm.exe :") && !line.startsWith("At ")) {
                return line.strip();
            }
        }
        return "";
    }

    private static boolean isPasswordPrompt(String output) {
        for (String line : output.split("\\R")) {
            Matcher m = PASSWORD_PROMPT.matcher(line.strip());
            if (m.matches()) {
                return true;
            }
        }
        return false;
    }
}
