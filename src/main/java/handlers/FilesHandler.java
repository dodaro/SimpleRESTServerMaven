package handlers;

import org.json.JSONObject;
import persistence.Database;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

public class FilesHandler extends AbstractRequestsHandler {

    private JSONObject uploadFile() throws IOException, SQLException {
        Map<String, String> parameters = processRequestParameters("id", "token", "fileFormat");
        if (parameters.size() != 3)
            throw new IllegalArgumentException("expected id, token and fileFormat");
        //Be careful, here we do not check the size of the request body!
        String uuid = Database.getInstance().uploadFile(parameters.get("id"), parameters.get("token"), httpExchange.getRequestBody().readAllBytes(), parameters.get("fileFormat"));
        return (uuid == null) ? createNotAllowedResponse() : new JSONObject(Map.of("fileId", uuid));
    }

    private JSONObject retrieveFile() throws IOException, SQLException {
        Map<String, String> parameters = processRequestParameters("id", "token", "fileId");
        if (parameters.size() != 3)
            throw new IllegalArgumentException("expected id, token and fileId");
        JSONObject ret = Database.getInstance().retrieveFile(parameters.get("id"), parameters.get("token"), parameters.get("fileId"));
        return (ret == null) ? createNotAllowedResponse() : ret;
    }

    private JSONObject deleteFile() throws IOException, SQLException {
        Map<String, String> parameters = processRequestParameters("id", "token", "fileId");
        if (parameters.size() != 3)
            throw new IllegalArgumentException("expected id, token and fileId");
        boolean ret = Database.getInstance().deleteFile(parameters.get("id"), parameters.get("token"), parameters.get("fileId"));
        return ret ? createSuccessfulResponse() : createNotAllowedResponse();
    }

    @Override
    protected JSONObject handleRequest(String operation, String table) {
        if (table == null) {
            try {
                if ("GET".equals(httpExchange.getRequestMethod()) && AccessPoints.RETRIEVE_FILE.equals(operation))
                    return retrieveFile();
                if ("GET".equals(httpExchange.getRequestMethod()) && AccessPoints.DELETE_FILE.equals(operation))
                    return deleteFile();
                if ("POST".equals(httpExchange.getRequestMethod()) && AccessPoints.UPLOAD_FILE.equals(operation))
                    return uploadFile();
            } catch (SQLException | IOException | IllegalArgumentException e) {
                return createInvalidResponseFromException(e);
            }
        }
        return createInvalidResponse();
    }
}
