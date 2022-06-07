import com.sun.net.httpserver.HttpServer;
import handlers.AccessPoints;
import handlers.AuthenticationHandler;
import handlers.DatabaseRequestHandler;
import handlers.FilesHandler;
import persistence.Database;
import util.Configuration;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.Executors;

public class SimpleServer {

    public void start() throws Exception {
        System.out.println(Configuration.SERVER_OUTPUT + "Starting server.");
        Database.getInstance().init();
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 8080), 0);
        System.out.println(Configuration.SERVER_OUTPUT + "Creating connection on localhost, port 8080.");
        List<String> tables = Configuration.getInstance().getTableNames();
        for (String table : tables) {
            for (String s : new String[]{AccessPoints.ADD, AccessPoints.GET, AccessPoints.UPDATE, AccessPoints.REMOVE}) {
                server.createContext("/" + table + "/" + s, new DatabaseRequestHandler());
                System.out.println(Configuration.SERVER_OUTPUT + "Adding access point " + s + " for collection " + table);
            }
        }
        server.createContext("/" + AccessPoints.LOGIN, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.LOGOUT, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.REGISTER, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.REFRESH, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.CHANGE_EMAIL, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.CHANGE_PASSWORD, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.RESET_PASSWORD, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.SEND_EMAIL_VERIFICATION, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.GET_USER_DATA, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.UPDATE_USER_ROLE, new AuthenticationHandler());
        server.createContext("/" + AccessPoints.UPLOAD_FILE, new FilesHandler());
        server.createContext("/" + AccessPoints.RETRIEVE_FILE, new FilesHandler());
        server.createContext("/" + AccessPoints.DELETE_FILE, new FilesHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println(Configuration.SERVER_OUTPUT + "All done. Ready to accept connection");
    }

    public static void main(String[] args) {
        SimpleServer s = new SimpleServer();
        try {
            s.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
