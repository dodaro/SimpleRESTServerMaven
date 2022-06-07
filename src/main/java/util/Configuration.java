package util;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Configuration {

    public final static String VERSION = "1.0.1";
    public final static String SERVER_OUTPUT = "[SRS "+ VERSION +"] ";

    public record AccessRole(String type, String role) {
    }

    public interface Access {
        int NO = 0;
        int YES = 1;
        int AUTH = 2;
    }

    public interface AccessType {
        String read = "read";
        String write = "write";
    }

    private ArrayList<AccessRole> getAccess(String type, JSONArray access) {
        ArrayList<AccessRole> roles = new ArrayList<>();
        for(int i = 0; i < access.length(); i++) {
            String acc = access.getString(i);
            roles.add(new AccessRole(type, acc));
        }
        return roles;
    }

    private final List<String> tableNames = new ArrayList<>();
    private final Map<String, ArrayList<AccessRole>> tableReadAccess = new HashMap<>();
    private final Map<String, ArrayList<AccessRole>> tableWriteAccess = new HashMap<>();
    private Configuration() {
        BufferedReader br = new BufferedReader(new InputStreamReader(Objects.requireNonNull(getClass().getResourceAsStream("/configurations/config.json"))));
        JSONObject obj = new JSONObject(br.lines().collect(Collectors.joining()));
        JSONArray tables = obj.getJSONArray("collections");
        for(int i = 0; i < tables.length(); i++) {
            JSONObject table = tables.getJSONObject(i);
            String name = table.getString("name");
            tableNames.add(name);
            tableReadAccess.put(name, getAccess(AccessType.read, table.getJSONArray(AccessType.read)));
            tableWriteAccess.put(name, getAccess(AccessType.write, table.getJSONArray(AccessType.write)));
        }
    }

    private static Configuration instance = null;

    public static Configuration getInstance() {
        if(instance == null)
            instance = new Configuration();
        return instance;
    }

    public List<String> getTableNames() {
        return tableNames;
    }

    public Integer getAccess(String table, String role, String type) {
        try {
            ArrayList<AccessRole> accesses;
            if(AccessType.read.equals(type))
                accesses = tableReadAccess.get(table);
            else if (AccessType.write.equals(type))
                accesses = tableWriteAccess.get(table);
            else
                return Access.NO;
            for (AccessRole access : accesses)
                if (access.role.equals(role))
                    return Access.YES;
                else if(access.role.equals("auth"))
                    return Access.AUTH;
        }
        catch(Exception e) {
        }
        return Access.NO;
    }

    public Integer isPublicAccess(String table, String type) {
        try {
            ArrayList<AccessRole> accesses;
            if(AccessType.read.equals(type)) {
                accesses = tableReadAccess.get(table);
                for (AccessRole access : accesses)
                    if (access.role.equals("public"))
                        return Access.YES;
            }
        }
        catch(Exception e) {
        }
        return Access.NO;
    }
}
