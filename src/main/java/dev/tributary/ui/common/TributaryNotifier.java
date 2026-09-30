package dev.tributary.ui.common;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import dev.tributary.TributaryBundle;
import dev.tributary.cli.RtcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class TributaryNotifier {

    private static final String GROUP = "Tributary";

    private TributaryNotifier() {}

    public static void info(@Nullable Project project, String title, String content) {
        create(title, content, NotificationType.INFORMATION).notify(project);
    }

    public static void warn(@Nullable Project project, String title, String content) {
        create(title, content, NotificationType.WARNING).notify(project);
    }

    public static void error(@Nullable Project project, String title, RtcException failure) {
        Notification notification = create(title, failure.getMessage(), NotificationType.ERROR);
        if (!failure.details().isBlank()) {
            notification.addAction(new AnAction(TributaryBundle.message("notification.show.details")) {
                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                    Messages.showErrorDialog(project, failure.details(), title);
                }
            });
        }
        notification.notify(project);
    }

    private static Notification create(String title, String content, NotificationType type) {
        return NotificationGroupManager.getInstance()
                .getNotificationGroup(GROUP)
                .createNotification(title, content, type);
    }
}
