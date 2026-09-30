package dev.tributary.ui.common;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import dev.tributary.cli.RtcException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TributaryTasks {

    @FunctionalInterface
    public interface Work<T> {
        T run(BooleanSupplier cancelled) throws RtcException;
    }

    private TributaryTasks() {}

    public static <T> void run(
            @Nullable Project project,
            @Nls String title,
            boolean cancellable,
            Work<T> work,
            @Nullable Consumer<T> onSuccess,
            @Nullable Consumer<RtcException> onError) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, title, cancellable) {
            private T result;
            private RtcException failure;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setIndeterminate(true);
                try {
                    result = work.run(indicator::isCanceled);
                } catch (RtcException e) {
                    failure = e;
                }
            }

            @Override
            public void onFinished() {
                if (failure != null) {
                    if (failure.kind() != RtcException.Kind.CANCELLED && onError != null) {
                        onError.accept(failure);
                    } else if (failure.kind() != RtcException.Kind.CANCELLED) {
                        TributaryNotifier.error(project, title, failure);
                    }
                } else if (onSuccess != null) {
                    onSuccess.accept(result);
                }
            }
        });
    }

    public static <T> void background(Work<T> work, Consumer<T> onSuccess, Consumer<RtcException> onError) {
        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            try {
                T value = work.run(() -> false);
                ApplicationManager.getApplication().invokeLater(() -> onSuccess.accept(value), ModalityState.any());
            } catch (RtcException e) {
                ApplicationManager.getApplication().invokeLater(() -> onError.accept(e), ModalityState.any());
            }
        });
    }
}
