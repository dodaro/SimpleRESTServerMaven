package handlers;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public abstract class AbstractRequestsHandler implements HttpHandler {

    protected HttpExchange httpExchange;

    @Override
    public void handle(HttpExchange httpExchange) throws IOException {
        this.httpExchange = httpExchange;
        Boolean isGET = switch (httpExchange.getRequestMethod()) {
            case "GET" -> true;
            case "POST" -> false;
            default -> null;
        };
        if (isGET == null) {
            handleResponse(createInvalidResponse());
            return;
        }
        String[] res = httpExchange.getRequestURI().toString().split("\\?")[0].split("/");
        if (res.length < 2 || res.length > 3) {
            handleResponse(createInvalidResponse());
            return;
        }
        String operation;
        String table = null;
        if(res.length == 2) {
            operation = res[1];
        }
        else {
            operation = res[2];
            table = res[1];
        }
        handleResponse(handleRequest(operation, table));
    }

    protected Map<String, String> processRequestParameters(String... neededParameters) throws IOException, IllegalArgumentException {
        boolean hasParameters = checkParameters();
        if(neededParameters.length == 0 && !hasParameters)
            return new HashMap<>();
        if(!hasParameters)
            throw new IllegalArgumentException("missing parameters " + neededParameters);
        String value = httpExchange.getRequestURI().toString().replaceAll(".*\\?", "");
        Map<String, String> result = new HashMap<>();
        String[] parameters = value.split("&");
        for (String param : parameters) {
            String[] p = param.split("=");
            if (p.length != 2) throw new IOException("invalid request");
            result.put(p[0], p[1]);
        }
        for (String parameter : neededParameters)
            if (!result.containsKey(parameter))
                throw new IllegalArgumentException("missing parameter " + parameter);
        return result;
    }

    private boolean checkParameters() {
        return httpExchange.getRequestURI().toString().contains("?");
    }

    protected abstract JSONObject handleRequest(String operation, String table);

    protected JSONObject createSuccessfulResponse() {
        return new JSONObject(Map.of("result", "success"));
    }

    protected JSONObject createInvalidResponse() {
        return new JSONObject(Map.of("result", "invalid operation"));
    }

    protected JSONObject createNotAllowedResponse() {
        return new JSONObject(Map.of("result", "operation not allowed"));
    }

    protected JSONObject createInvalidResponseFromException(Exception e) {
        return new JSONObject(Map.of("result", e.getMessage())); //Only for debug, we don't want to show our exceptions
    }

    protected JSONObject createErrorResponse(String message) {
        return new JSONObject(Map.of("result", message));
    }

    private void handleResponse(JSONObject requestParamValue) throws IOException {
        Objects.requireNonNull(requestParamValue);
        Headers h = httpExchange.getResponseHeaders();
        h.set("Content-Type", "application/json; charset=UTF-8");
        Writer outputStream = new OutputStreamWriter(httpExchange.getResponseBody(), "UTF-8");
        String res = requestParamValue.toString();
        res = new String(Base64.getEncoder().encode(res.getBytes(StandardCharsets.UTF_8)));
        httpExchange.sendResponseHeaders(200, res.length());
        outputStream.write(res);
        outputStream.flush();
        outputStream.close();
    }
}
