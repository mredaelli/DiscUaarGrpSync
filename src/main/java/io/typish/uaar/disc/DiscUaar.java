package io.typish.uaar.disc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DiscUaar {

    private final  String base;
    private final  String auth;
    private final String group_prefix;
    private static final JsonParser parser = new JsonParser();
    private final Map<String, String> authFormData = new HashMap<>();
    private final Map<String, Integer> groups;


    DiscUaar(final String base, final String auth, final String group_prefix, final String api_key, final String api_user) throws Exception {
        this.base = base;
        this.auth = auth;
        this.group_prefix = group_prefix;
        authFormData.put("api_key", api_key);
        authFormData.put("api_username", api_user);

        groups = readGroups();
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

     Set<String> getGroupsForUser(final User u) throws Exception {
        final JsonObject discUser = loadUserDetails(u);

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

     private JsonObject loadUserDetails(final User u) throws Exception {
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


      void removeUserFromGroups(final User u, final Set<String> userGroups) throws Exception {
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

     void addUserToGroup(final User u, final String group) throws Exception {
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

    JsonObject loadSomeUsers(final int offset, final int STEP) throws Exception {
        final HttpRequest resUsers = HttpRequest.get(base + "groups/trust_level_0/members.json?limit="+STEP+"&offset="+offset+"&show_emails=true&" + auth)
                .trustAllCerts()
                .accept("application/json");

        if( !resUsers.ok() )
            throw new Exception("Error 1");

        return parser.parse(resUsers.body())
                .getAsJsonObject();
    }
}
