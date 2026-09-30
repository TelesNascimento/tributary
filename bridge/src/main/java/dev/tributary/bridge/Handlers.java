package dev.tributary.bridge;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IProjectArea;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ILoginHandler2;
import com.ibm.team.repository.client.ILoginInfo2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.client.login.UsernameAndPasswordLoginInfo;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.workitem.client.IWorkItemClient;
import com.ibm.team.workitem.common.IAuditableCommon;
import com.ibm.team.workitem.common.expression.AttributeExpression;
import com.ibm.team.workitem.common.expression.IQueryableAttribute;
import com.ibm.team.workitem.common.expression.QueryableAttributes;
import com.ibm.team.workitem.common.expression.Term;
import com.ibm.team.workitem.common.model.AttributeOperation;
import com.ibm.team.workitem.common.model.IWorkItem;
import com.ibm.team.workitem.common.model.ItemProfile;
import com.ibm.team.workitem.common.query.IQueryResult;
import com.ibm.team.workitem.common.query.IResolvedResult;
import com.ibm.team.workitem.common.workflow.IWorkflowInfo;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.NullProgressMonitor;

final class Handlers {

    private static final Gson GSON = new Gson();
    private final ItemProfile<IWorkItem> profile;
    private ITeamRepository repository;
    private String uri;

    Handlers() {
        TeamPlatform.startup();
        profile = IWorkItem.SMALL_PROFILE;
    }

    JsonElement handle(String method, JsonObject params) throws Exception {
        switch (method) {
            case "initialize":
                return initialize();
            case "login":
                return login(
                        params.get("uri").getAsString(),
                        params.get("user").getAsString(),
                        params.get("password").getAsString());
            case "serverInfo":
                return serverInfo();
            case "getWorkItem":
                return workItem(requireRepository(), params.get("id").getAsInt());
            case "searchWorkItems":
                return search(params);
            default:
                throw new BridgeException("FAILED", "Unknown method: " + method);
        }
    }

    void close() {
        if (TeamPlatform.isStarted()) {
            TeamPlatform.shutdown();
        }
    }

    private JsonElement initialize() {
        JsonObject result = new JsonObject();
        result.addProperty("bridgeVersion", "0.1.0");
        result.addProperty("java", System.getProperty("java.version"));
        return result;
    }

    private JsonElement login(String serverUri, String user, final String password) throws Exception {
        uri = serverUri;
        repository = TeamPlatform.getTeamRepositoryService().getTeamRepository(serverUri);
        final String userId = user;
        repository.registerLoginHandler(new ILoginHandler2() {
            @Override
            public ILoginInfo2 challenge(ITeamRepository repo) {
                return new UsernameAndPasswordLoginInfo(userId, password);
            }
        });
        try {
            repository.login(new NullProgressMonitor());
        } catch (TeamRepositoryException e) {
            throw new BridgeException(looksLikeAuthFailure(e) ? "AUTH_REQUIRED" : "FAILED", e.getMessage());
        }
        return serverInfo();
    }

    private static boolean looksLikeAuthFailure(TeamRepositoryException e) {
        String name = e.getClass().getName();
        return name.contains("Auth") || name.contains("Login") || name.contains("Permission");
    }

    private JsonElement serverInfo() throws BridgeException {
        ITeamRepository repo = requireRepository();
        JsonObject result = new JsonObject();
        result.addProperty("uri", uri);
        result.addProperty("userId", repo.getUserId());
        IContributor me = repo.loggedInContributor();
        result.addProperty("userName", me == null ? null : me.getName());
        result.addProperty("serverVersion", repositoryVersion(repo));
        return result;
    }

    private static String repositoryVersion(ITeamRepository repo) {
        try {
            return (String) repo.getClass().getMethod("getRepositoryVersion").invoke(repo);
        } catch (Exception e) {
            return null;
        }
    }

    private ITeamRepository requireRepository() throws BridgeException {
        if (repository == null || !repository.loggedIn()) {
            throw new BridgeException("AUTH_REQUIRED", "Not logged in");
        }
        return repository;
    }

    private JsonElement search(JsonObject params) throws Exception {
        ITeamRepository repo = requireRepository();
        String text = params.has("text") ? params.get("text").getAsString().trim() : "";
        boolean mineOnly = params.has("mineOnly") && params.get("mineOnly").getAsBoolean();
        boolean includeResolved =
                params.has("includeResolved") && params.get("includeResolved").getAsBoolean();
        int max = params.has("max") ? params.get("max").getAsInt() : 30;
        JsonArray found = new JsonArray();
        if (text.matches("\\d{1,9}")) {
            try {
                found.add(workItem(repo, Integer.parseInt(text)));
            } catch (BridgeException notFound) {
            }
        }
        NullProgressMonitor monitor = new NullProgressMonitor();
        IWorkItemClient client = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
        IAuditableCommon common = (IAuditableCommon) repo.getClientLibrary(IAuditableCommon.class);
        IProcessItemService items = (IProcessItemService) repo.getClientLibrary(IProcessItemService.class);
        @SuppressWarnings("unchecked")
        List<IProjectArea> areas = items.findAllProjectAreas(IProcessClientService.ALL_PROPERTIES, monitor);
        for (IProjectArea area : areas) {
            if (found.size() >= max) {
                break;
            }
            Term term = new Term(Term.Operator.AND);
            if (!text.isEmpty()) {
                IQueryableAttribute summary = QueryableAttributes.getFactory(IWorkItem.ITEM_TYPE)
                        .findAttribute(area, IWorkItem.SUMMARY_PROPERTY, common, monitor);
                term.add(new AttributeExpression(summary, AttributeOperation.CONTAINS, text));
            }
            if (mineOnly) {
                IQueryableAttribute owner = QueryableAttributes.getFactory(IWorkItem.ITEM_TYPE)
                        .findAttribute(area, IWorkItem.OWNER_PROPERTY, common, monitor);
                term.add(new AttributeExpression(owner, AttributeOperation.EQUALS, repo.loggedInContributor()));
            }
            IQueryResult<IResolvedResult<IWorkItem>> results =
                    client.getQueryClient().getResolvedExpressionResults(area, term, profile);
            while (results.hasNext(monitor) && found.size() < max) {
                IWorkItem item = results.next(monitor).getItem();
                JsonObject dto = toJson(repo, client, item, monitor);
                if (includeResolved || !dto.get("resolved").getAsBoolean()) {
                    found.add(dto);
                }
            }
        }
        return found;
    }

    private JsonObject workItem(ITeamRepository repo, int id) throws Exception {
        NullProgressMonitor monitor = new NullProgressMonitor();
        IWorkItemClient client = (IWorkItemClient) repo.getClientLibrary(IWorkItemClient.class);
        IWorkItem item = client.findWorkItemById(id, profile, monitor);
        if (item == null) {
            throw new BridgeException("NOT_FOUND", "Work item " + id + " not found");
        }
        return toJson(repo, client, item, monitor);
    }

    private JsonObject toJson(ITeamRepository repo, IWorkItemClient client, IWorkItem item, NullProgressMonitor monitor)
            throws TeamRepositoryException {
        JsonObject dto = new JsonObject();
        dto.addProperty("id", item.getId());
        dto.addProperty("summary", item.getHTMLSummary().getPlainText());
        IWorkflowInfo workflow = client.getWorkflow(item.getWorkItemType(), item.getProjectArea(), monitor);
        dto.addProperty("state", workflow.getStateName(item.getState2()));
        dto.addProperty("resolved", workflow.getStateGroup(item.getState2()) == IWorkflowInfo.CLOSED_STATES);
        dto.addProperty("type", item.getWorkItemType());
        dto.addProperty("owner", contributorName(repo, item.getOwner(), monitor));
        return dto;
    }

    private static String contributorName(ITeamRepository repo, IContributorHandle handle, NullProgressMonitor monitor)
            throws TeamRepositoryException {
        if (handle == null) {
            return null;
        }
        IContributor contributor =
                (IContributor) repo.itemManager().fetchCompleteItem(handle, IItemManager.DEFAULT, monitor);
        return contributor.getName();
    }

    static List<String> knownMethods() {
        List<String> names = new ArrayList<String>();
        names.add("initialize");
        names.add("login");
        names.add("serverInfo");
        names.add("getWorkItem");
        names.add("searchWorkItems");
        names.add("shutdown");
        return names;
    }
}
