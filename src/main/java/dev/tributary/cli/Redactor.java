package dev.tributary.cli;

import java.util.regex.Pattern;

public final class Redactor {

    private static final Pattern PASSWORD_ARG = Pattern.compile("(?i)(-P|--password)(\\s+|=)\\S+");
    private static final Pattern PASSWORD_FIELD = Pattern.compile("(?i)(password|token|secret)\\s*[=:]\\s*\\S+");

    private Redactor() {}

    public static String redact(String text) {
        if (text == null) {
            return "";
        }
        String result = PASSWORD_ARG.matcher(text).replaceAll("$1$2***");
        return PASSWORD_FIELD.matcher(result).replaceAll("$1=***");
    }
}
