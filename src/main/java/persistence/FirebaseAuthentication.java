package persistence;

import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class FirebaseAuthentication {

    private static final String GOOGLE_URL = "https://identitytoolkit.googleapis.com/v1/";

    private final String apiKey;

    private static FirebaseAuthentication instance = null;

    protected FirebaseAuthentication() {
        apiKey = "INSERT HERE YOUR API KEY";
    }

    public static FirebaseAuthentication getInstance() {
        if(instance == null) {
            instance = new FirebaseAuthentication();
        }
        return instance;
    }

    private JSONObject doAction(String params, String operation) throws Exception {
        HttpsURLConnection urlRequest = null;
        JSONObject object;
        try {
            URL url = new URL(GOOGLE_URL + "accounts:" + operation + "?key="+ apiKey);
            urlRequest = (HttpsURLConnection) url.openConnection();
            urlRequest.setDoOutput(true);
            urlRequest.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            OutputStream os = urlRequest.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            osw.write(params);
            osw.flush();
            osw.close();
            urlRequest.connect();
            BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream) urlRequest.getContent()));
            object = new JSONObject(reader.lines().collect(Collectors.joining()));
            return object;
        } finally {
            if(urlRequest != null)
                urlRequest.disconnect();
        }
    }

    public JSONObject registration(String username, String password) throws Exception {
        return doAction("{\"email\":\""+username+"\",\"password\":\""+password+"\",\"returnSecureToken\":true}", "signUp");
    }

    public JSONObject login(String username, String password) throws Exception {
        return doAction("{\"email\":\""+username+"\",\"password\":\""+password+"\",\"returnSecureToken\":true}", "signInWithPassword");
    }

    public JSONObject changeEmail(String email, String token) throws Exception {
        return doAction("{\"email\":\""+email+"\",\"idToken\":\""+token+"\",\"returnSecureToken\":true}", "update");
    }

    public JSONObject changePassword(String password, String token) throws Exception {
        return doAction("{\"password\":\""+password+"\",\"idToken\":\""+token+"\",\"returnSecureToken\":true}", "update");
    }

    public JSONObject sendEmailVerification(String token) throws Exception {
        return doAction("{\"requestType\":\"VERIFY_EMAIL\",\"idToken\":\""+ token +"\"}", "sendOobCode");
    }

    public JSONObject resetPassword(String email) throws Exception {
        return doAction("{\"requestType\":\"PASSWORD_RESET\",\"email\":\""+ email +"\"}", "sendOobCode");
    }

    public JSONObject getUserData(String token) throws Exception {
        return doAction("{\"idToken\":\""+ token +"\"}", "lookup");
    }

    public JSONObject refreshToken(String token) throws Exception {
        HttpsURLConnection urlRequest = null;
        JSONObject object;
        try {
            URL url = new URL("https://securetoken.googleapis.com/v1/token?key="+ apiKey);
            urlRequest = (HttpsURLConnection) url.openConnection();
            urlRequest.setDoOutput(true);
            urlRequest.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            OutputStream os = urlRequest.getOutputStream();
            OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
            osw.write("grant_type=refresh_token&refresh_token=" + token);
            osw.flush();
            osw.close();
            urlRequest.connect();
            BufferedReader reader = new BufferedReader(new InputStreamReader((InputStream) urlRequest.getContent()));
            object = new JSONObject(reader.lines().collect(Collectors.joining()));
            return object;
        } finally {
            if(urlRequest != null)
                urlRequest.disconnect();
        }
    }

}


