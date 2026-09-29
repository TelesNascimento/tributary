package dev.tributary.vcs;

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import dev.tributary.backend.RtcBackend;
import dev.tributary.cli.RtcException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RtcBaseRevision implements ContentRevision {

    private final FilePath path;
    private final RtcBackend client;

    public RtcBaseRevision(FilePath path, RtcBackend client) {
        this.path = path;
        this.client = client;
    }

    @Override
    public @Nullable String getContent() throws VcsException {
        try {
            return client.baseContent(path.getIOFile().toPath());
        } catch (RtcException e) {
            throw new VcsException(e.getMessage(), e);
        }
    }

    @Override
    public @NotNull FilePath getFile() {
        return path;
    }

    @Override
    public @NotNull VcsRevisionNumber getRevisionNumber() {
        return new VcsRevisionNumber.Long(0);
    }
}
