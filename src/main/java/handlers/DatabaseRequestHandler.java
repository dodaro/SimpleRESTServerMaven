package handlers;

import org.json.JSONObject;
import persistence.Database;

import java.nio.file.AccessDeniedException;
import java.sql.SQLException;
import java.util.Map;

public class DatabaseRequestHandler extends AbstractRequestsHandler {

    @Override
    protected JSONObject handleRequest(String operation, String table) {
        if (table == null)
            return createInvalidResponse();
        try {
            Map<String, String> parameters;
            switch (operation) {
                case AccessPoints.GET -> {
                    parameters = processRequestParameters();
                    JSONObject r;
                    if(parameters.containsKey("id") && parameters.containsKey("token"))
                        r = Database.getInstance().get(table, parameters.get("id"), parameters.get("token"));
                    else
                        r = Database.getInstance().get(table, null, null);
                    return (r != null) ? r : createNotAllowedResponse();
                }
                case AccessPoints.ADD -> {
                    parameters = processRequestParameters("id", "token");
                    String id = parameters.get("id");
                    String token = parameters.get("token");
                    JSONObject obj;
                    if ("GET".equals(httpExchange.getRequestMethod())) {
                        parameters.remove("id");
                        parameters.remove("token");
                        obj = new JSONObject(parameters);
                    } else {
                        //Be careful, here we do not check the size of the request body!
                        obj = new JSONObject(new String(httpExchange.getRequestBody().readAllBytes()));
                    }
                    String element_id = Database.getInstance().insert(table, id, token, obj);
                    if(element_id != null) {
                        JSONObject res = createSuccessfulResponse();
                        res.put("element_id", element_id);
                        return res;
                    }
                    return createNotAllowedResponse();
                }
                case AccessPoints.UPDATE -> {
                    parameters = processRequestParameters("id", "token", "element_id");
                    String id = parameters.get("id");
                    String token = parameters.get("token");
                    String element_id = parameters.get("element_id");
                    JSONObject obj;
                    if ("GET".equals(httpExchange.getRequestMethod())) {
                        parameters.remove("id");
                        parameters.remove("token");
                        parameters.remove("element_id");
                        obj = new JSONObject(parameters);
                    } else {
                        //Be careful, here we do not check the size of the request body!
                        obj = new JSONObject(new String(httpExchange.getRequestBody().readAllBytes()));
                    }
                    return Database.getInstance().update(table, id, token, element_id, obj) ? createSuccessfulResponse() : createNotAllowedResponse();
                }
                case AccessPoints.REMOVE -> {
                    parameters = processRequestParameters("id", "token", "element_id");
                    return Database.getInstance().remove(table, parameters.get("id"), parameters.get("token"), parameters.get("element_id")) ? createSuccessfulResponse() : createNotAllowedResponse();
                }
                default -> {
                    return createInvalidResponse();
                }
            }
        } catch (SQLException | IllegalArgumentException | AccessDeniedException e) {
            return createInvalidResponseFromException(e);
        } catch (Exception ignored) {
        }
        return createInvalidResponse();
    }
}
