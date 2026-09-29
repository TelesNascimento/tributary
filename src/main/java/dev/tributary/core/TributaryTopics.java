package dev.tributary.core;

import com.intellij.util.messages.Topic;
import java.nio.file.Path;

public final class TributaryTopics {

    public interface StatusListener {
        void statusChanged(Path sandbox);
    }

    public static final Topic<StatusListener> STATUS_CHANGED =
            Topic.create("Tributary status changed", StatusListener.class);

    public interface ContextListener {
        void contextChanged();
    }

    public static final Topic<ContextListener> CONTEXT_CHANGED =
            Topic.create("Tributary context changed", ContextListener.class);

    private TributaryTopics() {}
}
