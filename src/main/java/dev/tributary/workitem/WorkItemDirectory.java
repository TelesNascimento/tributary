package dev.tributary.workitem;

import dev.tributary.cli.RtcException;
import java.util.List;
import java.util.Optional;

public interface WorkItemDirectory {

    List<WorkItem> search(String text, boolean mineOnly, boolean includeResolved, int max) throws RtcException;

    Optional<WorkItem> get(long id) throws RtcException;
}
