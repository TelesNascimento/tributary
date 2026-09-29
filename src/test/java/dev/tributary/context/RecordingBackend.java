package dev.tributary.context;

import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.RtcException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class RecordingBackend {

    private final List<String> calls = new ArrayList<>();
    private final Set<String> failing = new HashSet<>();
    private int changeSets;
    private final RtcBackend proxy;

    RecordingBackend() {
        InvocationHandler handler = (target, method, args) -> {
            String rendered = method.getName()
                    + (args == null
                            ? ""
                            : Arrays.stream(args)
                                    .map(RecordingBackend::render)
                                    .collect(Collectors.joining(",", "(", ")")));
            calls.add(rendered);
            if (failing.contains(method.getName())) {
                throw new RtcException(RtcException.Kind.PROCESS_REJECTED, "rejected");
            }
            return switch (method.getName()) {
                case "createChangeSet" -> "_cs" + (++changeSets);
                case "sandboxRoot" -> Path.of("sandbox");
                default -> defaultFor(method.getReturnType());
            };
        };
        this.proxy = (RtcBackend)
                Proxy.newProxyInstance(RtcBackend.class.getClassLoader(), new Class<?>[] {RtcBackend.class}, handler);
    }

    RtcBackend backend() {
        return proxy;
    }

    RecordingBackend failOn(String method) {
        failing.add(method);
        return this;
    }

    List<String> calls() {
        return calls;
    }

    private static String render(Object argument) {
        return argument == null ? "null" : String.valueOf(argument);
    }

    private static Object defaultFor(Class<?> type) {
        if (type == String.class) {
            return "";
        }
        if (List.class.isAssignableFrom(type)) {
            return List.of();
        }
        if (type == boolean.class) {
            return false;
        }
        return null;
    }
}
