package io.typish.uaar.disc;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class Sync {

    Sync(final Properties p) {
        final String api_key = p.getProperty("api_key");
        final String api_user = p.getProperty("api_user");

        base = p.getProperty("baseURL");
        auth = "api_key="+ api_key +"&api_username="+ api_user;
        group_prefix = p.getProperty("group_prefix");
        STEP = Integer.valueOf(p.getProperty("userChunk"));

        query = p.getProperty("dbQuery");
        dbClass = p.getProperty("dbClass");
        dbUrl = p.getProperty("dbUrl");

        authFormData.put("api_key", api_key);
        authFormData.put("api_username", api_user);
    }

    private static class User {
        String email;
        String circolo;
        int id;
        String username;
    }

    private static final JsonParser parser = new JsonParser();
    private final  String base;
    private final  String query, dbUrl, dbClass;
    private final  String auth;
    private final String group_prefix;
    private final Map<String, String> authFormData = new HashMap<>();
    private final int STEP;
    private Set<String> forListacircoli;

    private PreparedStatement stmt;


    private Map<String, Integer> groups;

    void run() {
        Connection connection = null;
        try {
            groups = readGroups();
            forListacircoli = new HashSet<>(Files.readAllLines(Paths.get("listacircoli.txt")));

            Class.forName(dbClass);
            connection = DriverManager.getConnection(dbUrl);
            stmt = connection.prepareStatement(query);

            int offset = 0;
            int read = 0;
            int total;

            do {
                final JsonObject partialRes = getAPieceOfUsers(offset, STEP);
                total = partialRes.get("meta").getAsJsonObject().get("total").getAsInt();

                final JsonArray discUsers = partialRes.get("members")
                        .getAsJsonArray();

                discUsers.forEach(this::processUser);

                read += discUsers.size();
                offset = read;
            } while(total > read);

        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            if( connection != null ) try { connection.close(); } catch(final Exception ex) {}
        }
    }

    private String getCircoloFromTesserateo(final User u) {
        try {

            stmt.setString(1, u.email);
            final ResultSet rs = stmt.executeQuery();
            String res = null;
            if( rs.next() ) {
                res = rs.getString("circolo");
                if( rs.next() ) throw new Exception("More than one " + u.email);
            } else {
                System.out.println("Not found " + u.email);
            }
            return res;
        } catch(final Exception e ){
            e.printStackTrace();
            return null;
        }
    }

    private void processUser(final JsonElement du) {
        try {
            final User u = new User();

            u.username = du.getAsJsonObject().get("username").getAsString();
            System.out.println("Processing "+u.username);

            final Set<String> userGroups = getUsergropsFromDisc(u);
            if( userGroups == null ) return;

            // trova il circolo di appartenenza da tesserateo
            u.circolo = getCircoloFromTesserateo(u);
            if( u.circolo != null ) {
                if( userGroups.contains(u.circolo) ) userGroups.remove(u.circolo);
                else addUserToGroup(u, u.circolo);
            }

            // lista circoli?
            if( forListacircoli.contains(u.email) ) {
                System.out.println("In lista circoli");
                if( userGroups.contains("listacircoli") ) userGroups.remove("listacircoli");
                else addUserToGroup(u, "listacircoli");
            }

            removeUserFromGroups(u, userGroups);

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }


    private Map<String, Integer> readGroups() throws Exception {
        final Map<String, Integer> res = new HashMap<>();

        final HttpRequest resGroups = HttpRequest.get(base + "admin/groups.json?" + auth)
                .trustAllCerts()
                .accept("application/json");

        if( !resGroups.ok() )
            throw new Exception("Error 1");

        parser.parse(resGroups.body()).getAsJsonArray().forEach( g -> {
            final JsonObject group = g.getAsJsonObject();
            final String name = group.get("name")
                    .getAsString();
            if( name.startsWith(group_prefix) )
                res.put(name.substring(group_prefix.length()), group.get("id").getAsInt());
        });

        return res;
    }

    private Set<String> getUsergropsFromDisc(final User u) throws Exception {
        final JsonObject discUser = loadUserDetailsFromDisc(u);

        if( discUser.get("email") == null ) return null;

        u.email = discUser.get("email")
                .getAsString();
        u.id = discUser.get("id")
                .getAsInt();

        final Set<String> userGroups = new HashSet<>();
        discUser.get("groups").getAsJsonArray().forEach( g -> {
            final JsonObject group = g.getAsJsonObject();
            final String name = group.get("name").getAsString();
            if( name.startsWith(group_prefix) )
                userGroups.add(name.substring(group_prefix.length()));
        });
        return userGroups;
    }

    private JsonObject loadUserDetailsFromDisc(final User u) throws Exception {
        final HttpRequest resUser = HttpRequest.get(base + "/users/"+u.username+".json?show_emails=true&" + auth)
                .trustAllCerts()
                .accept("application/json");

        if( !resUser.ok() )
            throw new Exception("Error 2");

        return parser.parse(resUser.body())
                .getAsJsonObject()
                .get("user")
                .getAsJsonObject();
    }

    private  void removeUserFromGroups(final User u, final Set<String> userGroups) throws Exception {
        final Map<String, String> send = new HashMap<>(authFormData);
        for( final String group: userGroups ) {
            send.put("user_id", Integer.toString(u.id));
            final HttpRequest resUsers = HttpRequest.delete(base + "groups/"+group+"/members.json")
                    .trustAllCerts()
                    .accept("application/json")
                    .form(send)
                    ;

            if( !resUsers.ok() ) throw new Exception("Error a");
        }
    }

    private void addUserToGroup(final User u, final String group) throws Exception {
        final Map<String, String> send = new HashMap<>(authFormData);
        send.put("usernames", u.username);
        final HttpRequest resUsers = HttpRequest.put(base + "groups/"+groups.get(group)+"/members.json?")
                .trustAllCerts()
                .accept("application/json")
                .form(send);

        if( !resUsers.ok() ) {
            System.err.println(resUsers.body());
            throw new Exception("Error b");
        }
    }

    private  JsonObject getAPieceOfUsers(final int offset, final int STEP) throws Exception {
        final HttpRequest resUsers = HttpRequest.get(base + "groups/trust_level_0/members.json?limit="+STEP+"&offset="+offset+"&show_emails=true&" + auth)
                .trustAllCerts()
                .accept("application/json");

        if( !resUsers.ok() )
            throw new Exception("Error 1");

        return parser.parse(resUsers.body())
                .getAsJsonObject();
    }
}
