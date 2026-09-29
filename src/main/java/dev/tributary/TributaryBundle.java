package dev.tributary;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

public final class TributaryBundle extends DynamicBundle {

    private static final @NonNls String BUNDLE = "messages.TributaryBundle";
    private static final TributaryBundle INSTANCE = new TributaryBundle();

    private TributaryBundle() {
        super(BUNDLE);
    }

    public static @Nls String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
        return INSTANCE.getMessage(key, params);
    }
}
