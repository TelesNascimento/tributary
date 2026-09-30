package dev.tributary.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;

public final class RtcVcsHolder {

    private RtcVcsHolder() {}

    public static AbstractVcs get(Project project) {
        return ProjectLevelVcsManager.getInstance(project).findVcsByName(RtcVcs.NAME);
    }

    public static VcsKey key() {
        return RtcVcs.KEY;
    }
}
