package persistence;

import org.json.JSONObject;
import util.Configuration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class Database {

    private static Database instance = null;
    private boolean initialized = false;

    private Database() {
    }

    public void init() {
        if(initialized)
            return;
        initialized = true;
        try {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                PreparedStatement preparedStatement = connection.prepareStatement(
"""
CREATE TABLE IF NOT EXISTS "authentication" (
    "id" TEXT NOT NULL UNIQUE,
    "email" TEXT NOT NULL UNIQUE,
    "token" TEXT NOT NULL,
    "refreshToken" TEXT NOT NULL,
    "expiresIn" REAL NOT NULL,
    "role" TEXT NOT NULL,
    "verified" INTEGER NOT NULL,
    PRIMARY KEY("id")
);
""");
                preparedStatement.execute();
                preparedStatement = connection.prepareStatement(
                """
CREATE TABLE IF NOT EXISTS "files" (
    "id" TEXT NOT NULL,
    "userId" TEXT NOT NULL,
    "fileContent" BLOB NOT NULL,
    "fileFormat" TEXT NOT NULL,
    PRIMARY KEY("id")
);
""");
                preparedStatement.execute();
            }
            List<String> tables = Configuration.getInstance().getTableNames();
            for(String table : tables) {
                createTable(table);
            }
        } catch (SQLException e) {
            System.err.println("Error during the initialization of the database: " + e.getMessage());
        }
    }

    public static Database getInstance() {
        if(instance == null)
            instance = new Database();
        return instance;
    }

    private void createTable(String table) throws SQLException {
        Connection connection = ConnectionPool.getConnection();
        try (connection) {
            Objects.requireNonNull(connection);
            PreparedStatement preparedStatement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS \"" + table + "\" (\"id\" TEXT NOT NULL, \"element_id\" TEXT NOT NULL, \"json\" TEXT NOT NULL, PRIMARY KEY(\"id\",\"element_id\"));");
            preparedStatement.execute();
        }
    }

    private boolean hasPublicAccess(String table) {
        return Configuration.getInstance().isPublicAccess(table, Configuration.AccessType.read) == Configuration.Access.YES;
    }

    private boolean hasAccess(String table, String access, String id, String tokenId) throws SQLException {
        Connection connection = ConnectionPool.getConnection();
        try (connection) {
            Objects.requireNonNull(connection);
            if (Configuration.getInstance().isPublicAccess(table, access) == Configuration.Access.YES)
                return true;
            if(id == null || tokenId == null)
                return false;
            PreparedStatement preparedStatement = connection.prepareStatement("select role, token, expiresIn, verified from authentication where id=?");
            preparedStatement.setString(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (!resultSet.next())
                return false;
            String role = resultSet.getString(1);
            String token = resultSet.getString(2);
            long expiresIn = resultSet.getLong(3);
            int verified = resultSet.getInt(4);
            if(verified == 0 || "invalid".equals(token))
                return false;
            if("admin".equals(role)) //Admins can do everything if they are authenticated
                return token.equals(tokenId) && System.currentTimeMillis() <= expiresIn;
            if("authentication".equals(table)) //Only admins can write in the table authentication
                return false;
            if(!"files".equals(table)) { //For table files we do not need to check the configuration
                Integer accessType = Configuration.getInstance().getAccess(table, role, access);
                if (accessType == Configuration.Access.NO)
                    return false;
                if (accessType == Configuration.Access.YES)
                    return true;
            }
            return token.equals(tokenId) && System.currentTimeMillis() <= expiresIn;
        }
    }

    private String getRole(String id) throws SQLException {
        Connection connection = ConnectionPool.getConnection();
        try (connection) {
            Objects.requireNonNull(connection);
            PreparedStatement preparedStatement = connection.prepareStatement("select role from authentication where id=?");
            preparedStatement.setString(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            if (resultSet.next()) {
                String role = resultSet.getString(1);
                return role;
            }
        }
        return null;
    }

    public synchronized boolean remove(String table, String id, String token, String element_id) throws SQLException {
        if(hasAccess(table, Configuration.AccessType.write, id, token)) {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                String role = getRole(id);
                if(role == null)
                    return false;
                PreparedStatement preparedStatement;
                if("user".equals(role)) {
                    preparedStatement = connection.prepareStatement("delete from " + table + " where id = ? and element_id = ?");
                    preparedStatement.setString(1, id);
                    preparedStatement.setString(2, element_id);
                }
                else {
                    preparedStatement = connection.prepareStatement("delete from " + table + " where element_id = ?");
                    preparedStatement.setString(1, element_id);
                }
                preparedStatement.execute();
            }
            return true;
        }
        return false;
    }

    public synchronized String insert(String table, String id, String token, JSONObject obj) throws SQLException {
        String element_id = null;
        if(hasAccess(table, Configuration.AccessType.write, id, token)) {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                element_id = table + "_" + UUID.randomUUID();
                PreparedStatement preparedStatement = connection.prepareStatement("insert into " + table + " values(?, ?, json(?))");
                preparedStatement.setString(1, id);
                preparedStatement.setString(2, element_id);
                preparedStatement.setString(3, obj.toString());
                preparedStatement.execute();
            }
        }
        return element_id;
    }

    public synchronized boolean update(String table, String id, String token, String element_id, JSONObject obj) throws SQLException {
        if(hasAccess(table, Configuration.AccessType.write, id, token)) {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                String role = getRole(id);
                if(role == null)
                    return false;
                PreparedStatement preparedStatement;
                if("user".equals(role)) {
                    preparedStatement = connection.prepareStatement("update " + table + " set json=json(?) where id=? and element_id=?");
                    preparedStatement.setString(1, obj.toString());
                    preparedStatement.setString(2, id);
                    preparedStatement.setString(3, element_id);
                }
                else {
                    preparedStatement = connection.prepareStatement("update " + table + " set json=json(?) where element_id=?");
                    preparedStatement.setString(1, obj.toString());
                    preparedStatement.setString(2, element_id);
                }
                preparedStatement.execute();
            }
            return true;
        }
        return false;
    }

    public synchronized JSONObject get(String table, String id, String token) throws SQLException {
        if(hasAccess(table, Configuration.AccessType.read, id, token)) {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                PreparedStatement preparedStatement;
                if(id == null || hasPublicAccess(table)) {
                    preparedStatement = connection.prepareStatement("select element_id, json(json) from " + table);
                }
                else {
                    preparedStatement = connection.prepareStatement("select element_id, json(json) from " + table + " where id=?");
                    preparedStatement.setString(1, id);
                }
                ResultSet resultSet = preparedStatement.executeQuery();
                Map<String, List<JSONObject>> res = new HashMap<>();
                List<JSONObject> objects = new ArrayList<>();
                while (resultSet.next()) {
                    JSONObject object = new JSONObject(resultSet.getString(2));
                    if(id != null)
                        object.put("id", id);
                    object.put("element_id", resultSet.getString(1));
                    objects.add(object);
                }
                res.put(table, objects);
                return new JSONObject(res);
            }
        }
        return null;
    }

    public synchronized void registerUser(String id, String email, String tokenId, String refreshToken, Integer expiresIn) throws SQLException {
        Connection connection = ConnectionPool.getConnection();
        try (connection) {
            Objects.requireNonNull(connection);
            PreparedStatement preparedStatement = connection.prepareStatement("insert into authentication values(?, ?, ?, ?, ?, ?, ?)");
            preparedStatement.setString(1, id);
            preparedStatement.setString(2, email);
            preparedStatement.setString(3, tokenId);
            preparedStatement.setString(4, refreshToken);
            preparedStatement.setLong(5, System.currentTimeMillis()+expiresIn*1000);
            preparedStatement.setString(6, "user");
            preparedStatement.setInt(7, 0);
            preparedStatement.execute();
        }
    }

    public synchronized void changeEmail(String id, String email, String tokenId, String refreshToken, Integer expiresIn) throws SQLException {
        Connection connection = ConnectionPool.getConnection();
        try (connection) {
            Objects.requireNonNull(connection);
            PreparedStatement preparedStatement = connection.prepareStatement("update authentication set email=?, token=?, refreshToken=?, expiresIn=?, verified=0 where id=?");
            preparedStatement.setString(1, email);
            preparedStatement.setString(2, tokenId);
            preparedStatement.setString(3, refreshToken);
            preparedStatement.setLong(4, expiresIn);
            preparedStatement.setString(5, id);
            preparedStatement.execute();
        }
    }

    public synchronized String setTokenForUser(String id, String tokenId, String refreshToken, Integer expiresIn) throws SQLException {
        Connection connection = ConnectionPool.getConnection();
        try (connection) {
            Objects.requireNonNull(connection);
            PreparedStatement preparedStatement = connection.prepareStatement("update authentication set token=?, refreshToken=?, expiresIn=? where id=?");
            preparedStatement.setString(1, tokenId);
            preparedStatement.setString(2, refreshToken);
            preparedStatement.setLong(3, System.currentTimeMillis()+expiresIn*1000);
            preparedStatement.setString(4, id);
            preparedStatement.execute();
            preparedStatement = connection.prepareStatement("select role from authentication where id=?");
            preparedStatement.setString(1, id);
            ResultSet resultSet = preparedStatement.executeQuery();
            if(resultSet.next())
                return resultSet.getString("role");
        }
        return null;
    }

    public synchronized boolean updateUserRole(String adminId, String adminToken, String userId, String role) throws SQLException {
        if(hasAccess("authentication", Configuration.AccessType.write, adminId, adminToken)) {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                PreparedStatement preparedStatement = connection.prepareStatement("update authentication set role=? where id=?");
                preparedStatement.setString(1, role);
                preparedStatement.setString(2, userId);
                preparedStatement.execute();
                return true;
            }
        }
        return false;
    }

    public synchronized boolean logout(String id, String tokenId) throws SQLException {
        Connection connection = ConnectionPool.getConnection();
        try (connection) {
            Objects.requireNonNull(connection);
            PreparedStatement preparedStatement = connection.prepareStatement("select token from authentication where id = ?");
            preparedStatement.setString(1, id);
            ResultSet rs = preparedStatement.executeQuery();
            if (!rs.next())
                return false;
            String token = rs.getString("token");
            if (!token.equals(tokenId))
                return false;
        }
        setTokenForUser(id, "invalid", "invalid", 0);
        return true;
    }

    public synchronized void setVerified(String email) throws SQLException {
        Connection connection = ConnectionPool.getConnection();
        try (connection) {
            Objects.requireNonNull(connection);
            PreparedStatement preparedStatement = connection.prepareStatement("update authentication set verified=1 where email=?");
            preparedStatement.setString(1, email);
            preparedStatement.execute();
        }
    }

    public synchronized String uploadFile(String id, String token, byte[] fileContent, String fileFormat) throws SQLException {
        if(hasAccess("files", Configuration.AccessType.write, id, token)) {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                PreparedStatement preparedStatement = connection.prepareStatement("insert into files values(?, ?, ?, ?)");
                String uuid = UUID.randomUUID().toString();
                preparedStatement.setString(1, uuid);
                preparedStatement.setString(2, id);
                preparedStatement.setBytes(3, fileContent);
                preparedStatement.setString(4, fileFormat);
                preparedStatement.execute();
                return uuid;
            }
        }
        return null;
    }

    public synchronized JSONObject retrieveFile(String id, String token, String fileId) throws SQLException {
        if(hasAccess("files", Configuration.AccessType.read, id, token)) {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                PreparedStatement preparedStatement = connection.prepareStatement("select fileContent, fileFormat from files where id=?");
                preparedStatement.setString(1, fileId);
                ResultSet resultSet = preparedStatement.executeQuery();
                if (resultSet.next()) {
                    return new JSONObject(Map.of("fileContent", resultSet.getBytes(1), "fileFormat", resultSet.getString(2)));
                }
            }
        }
        return null;
    }

    public synchronized boolean deleteFile(String id, String token, String fileId) throws SQLException {
        if(hasAccess("files", Configuration.AccessType.write, id, token)) {
            Connection connection = ConnectionPool.getConnection();
            try (connection) {
                Objects.requireNonNull(connection);
                PreparedStatement preparedStatement = connection.prepareStatement("delete from files where id=? and userId=?");
                preparedStatement.setString(1, fileId);
                preparedStatement.setString(2, id);
                preparedStatement.execute();
            }
            return true;
        }
        return false;
    }
}
