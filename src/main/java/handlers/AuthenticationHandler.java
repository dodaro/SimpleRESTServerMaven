package handlers;

import org.json.JSONArray;
import org.json.JSONObject;
import persistence.Database;
import persistence.FirebaseAuthentication;

import java.util.Map;

public class AuthenticationHandler extends AbstractRequestsHandler {

    private boolean checkResponse(JSONObject obj, String... toCheck) {
        for (String s : toCheck)
            if (!obj.has(s))
                return false;
        return true;
    }

    private JSONObject logout() throws Exception {
        Map<String, String> parameters = processRequestParameters("id", "token");
        if (Database.getInstance().logout(parameters.get("id"), parameters.get("token")))
            return createSuccessfulResponse();
        else
            return createNotAllowedResponse();
    }

    private JSONObject refresh() throws Exception {
        Map<String, String> parameters = processRequestParameters("token");
        JSONObject ret = FirebaseAuthentication.getInstance().refreshToken(parameters.get("token"));
        if (checkResponse(ret, "refresh_token", "expires_in", "id_token")) {
            String source = """
                    {
                    "refresh_token":"%s",
                    "id_token":"%s",
                    "expires_in":"%s"
                    }""".formatted(ret.getString("refresh_token"), ret.getString("id_token"), ret.getString("expires_in"));
            ret = new JSONObject(source);
            return ret;
        } else
            return createNotAllowedResponse();
    }

    private JSONObject login() throws Exception {
        Map<String, String> parameters = processRequestParameters("username", "password");
        JSONObject value = FirebaseAuthentication.getInstance().login(parameters.get("username"), parameters.get("password"));
        if (checkResponse(value, "refreshToken", "idToken", "localId", "expiresIn")) {
            Integer expiresIn = Integer.parseInt(value.getString("expiresIn"));
            String role = Database.getInstance().setTokenForUser(value.getString("localId"), value.getString("idToken"), value.getString("refreshToken"), expiresIn);
            value.put("role", role);
        }
        return value;
    }

    private JSONObject register() throws Exception {
        Map<String, String> parameters = processRequestParameters("username", "password");
        JSONObject value = FirebaseAuthentication.getInstance().registration(parameters.get("username"), parameters.get("password"));
        if (checkResponse(value, "refreshToken", "email", "idToken", "localId", "expiresIn")) {
            Integer expiresIn = Integer.parseInt(value.getString("expiresIn"));
            Database.getInstance().registerUser(value.getString("localId"), value.getString("email"), value.getString("idToken"), value.getString("refreshToken"), expiresIn);
        }
        return value;
    }

    private JSONObject changeEmail() throws Exception {
        Map<String, String> parameters = processRequestParameters("email", "token");
        JSONObject value = FirebaseAuthentication.getInstance().changeEmail(parameters.get("email"), parameters.get("token"));
        if (checkResponse(value, "email", "localId", "idToken", "refreshToken", "expiresIn")) {
            Integer expiresIn = Integer.parseInt(value.getString("expiresIn"));
            Database.getInstance().changeEmail(value.getString("localId"), value.getString("email"), value.getString("idToken"), value.getString("refreshToken"), expiresIn);
            return value;
        }
        return createErrorResponse("email not changed");
    }

    private JSONObject changePassword() throws Exception {
        Map<String, String> parameters = processRequestParameters("password", "token");
        JSONObject value = FirebaseAuthentication.getInstance().changePassword(parameters.get("password"), parameters.get("token"));
        if (checkResponse(value, "localId", "idToken", "refreshToken", "expiresIn")) {
            Integer expiresIn = Integer.parseInt(value.getString("expiresIn"));
            Database.getInstance().setTokenForUser(value.getString("localId"), value.getString("idToken"), value.getString("refreshToken"), expiresIn);
        }
        return value;
    }

    private JSONObject sendEmailVerification() throws Exception {
        Map<String, String> parameters = processRequestParameters("token");
        JSONObject value = FirebaseAuthentication.getInstance().sendEmailVerification(parameters.get("token"));
        if (checkResponse(value, "email"))
            return value;
        return createErrorResponse("email not sent");
    }

    private JSONObject resetPassword() throws Exception {
        Map<String, String> parameters = processRequestParameters("email");
        JSONObject value = FirebaseAuthentication.getInstance().resetPassword(parameters.get("email"));
        if (checkResponse(value, "email"))
            return value;
        return createErrorResponse("cannot reset password");
    }

    private JSONObject getUserData() throws Exception {
        Map<String, String> parameters = processRequestParameters("token");
        JSONObject value = FirebaseAuthentication.getInstance().getUserData(parameters.get("token"));
        if (checkResponse(value, "users")) {
            JSONArray users = value.getJSONArray("users");
            if (users.length() == 1) {
                value = users.getJSONObject(0);
                if (checkResponse(value, "email", "emailVerified")) {
                    Database.getInstance().setVerified(value.getString("email"));
                    String source = """
                            {
                            "emailVerified":%s,
                            }""".formatted(value.getBoolean("emailVerified"));
                    return new JSONObject(source);
                }
            }
        }
        return createInvalidResponse();
    }

    private JSONObject updateUserRole() throws Exception {
        Map<String, String> parameters = processRequestParameters("id", "token", "userId", "role");
        if (Database.getInstance().updateUserRole(parameters.get("id"), parameters.get("token"), parameters.get("userId"), parameters.get("role")))
            return createSuccessfulResponse();
        return createNotAllowedResponse();
    }

    @Override
    protected JSONObject handleRequest(String operation, String table) {
        if (table != null)
            return createInvalidResponse();
        try {
            return switch (operation) {
                case AccessPoints.LOGOUT -> logout();
                case AccessPoints.LOGIN -> login();
                case AccessPoints.REGISTER -> register();
                case AccessPoints.REFRESH -> refresh();
                case AccessPoints.CHANGE_EMAIL -> changeEmail();
                case AccessPoints.CHANGE_PASSWORD -> changePassword();
                case AccessPoints.RESET_PASSWORD -> resetPassword();
                case AccessPoints.SEND_EMAIL_VERIFICATION -> sendEmailVerification();
                case AccessPoints.GET_USER_DATA -> getUserData();
                case AccessPoints.UPDATE_USER_ROLE -> updateUserRole();
                default -> createInvalidResponse();
            };
        } catch (Exception e) {
            return createInvalidResponseFromException(e);
        }
    }
}
