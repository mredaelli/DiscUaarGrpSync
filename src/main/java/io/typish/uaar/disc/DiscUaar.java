package io.typish.uaar.disc;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.github.kevinsawicki.http.HttpRequest;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

class DiscUaar {

    private final  String base;
    private final  String auth;
    private final String group_prefix;
    private final String circoli_grp;
    private static final JsonParser parser = new JsonParser();
    private final Map<String, String> authFormData = new HashMap<>();
    private final Map<String, Integer> groups;


    DiscUaar(final String base, final String auth, final String group_prefix, final String circoliGrp, final String api_key, final String api_user) throws Exception {
        this.base = base;
        this.auth = auth;
        this.group_prefix = group_prefix;
        this.circoli_grp = circoliGrp;
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
            throw new Exception("Error 1 "+resGroups.message()+ ' ' +resGroups.code());

        for( JsonElement g: parser.parse(resGroups.body()).getAsJsonArray() ) {
            final JsonObject group = g.getAsJsonObject();
            final String name = group.get("name")
                    .getAsString();
            if( name.startsWith(group_prefix) || name.equals(circoli_grp) )
                res.put(name, group.get("id").getAsInt());
        }

        return res;
    }

     Set<String> getUserDetailsAndGroups(final User u) throws Exception {
        u.email = loadUserEmail(u);
         if( u.email == null ) return null;

        final JsonObject discUser = loadUserDetails(u);
        u.id = discUser.get("id")
                .getAsInt();

        final Set<String> userGroups = new HashSet<>();
        for( JsonElement g: discUser.get("groups").getAsJsonArray() ) {
            final JsonObject group = g.getAsJsonObject();
            final String name = group.get("name").getAsString();
            if( name.startsWith(group_prefix) || circoli_grp.equals(name) )
                userGroups.add(name);
        }
        return userGroups;
    }

     private String loadUserEmail(final User u) throws Exception {
        final HttpRequest resUser = HttpRequest.get(base + "/users/"+u.username+"/emails.json?show_emails=true&" + auth)
                .trustAllCerts()
                .accept("application/json");

        if( !resUser.ok() )
            throw new Exception("Error 2 "+resUser.message()+ ' ' +resUser.code());

        return parser.parse(resUser.body())
                .getAsJsonObject()
                .get("email")
                .getAsString();
    }
 private JsonObject loadUserDetails(final User u) throws Exception {
        final HttpRequest resUser = HttpRequest.get(base + "/users/"+u.username+".json?show_emails=true&" + auth)
                .trustAllCerts()
                .accept("application/json");

        if( !resUser.ok() )
            throw new Exception("Error 4 "+resUser.message()+ ' ' +resUser.code());

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
         Integer groupId = groups.get(group);
         if( groupId == null ) {
             groupId = createGroup(group);
             if( groupId == null )
                 return;
             else {
                 groups.put(group, groupId);
                 System.out.println("Created " + group);
             }
         }
         final HttpRequest resUsers = HttpRequest.put(base + "groups/"+ groupId +"/members.json?")
                .trustAllCerts()
                .accept("application/json")
                .form(send);

        if( !resUsers.ok() ) {
            System.err.println(resUsers.body());
            throw new Exception("Error b");
        }
    }

    Integer createGroup(final String group) throws Exception {
        final Map<String, String> send = new HashMap<>(authFormData);
        send.put("group[name]", group);
        final HttpRequest resUsers = HttpRequest.post(base + "admin/groups")
                .trustAllCerts()
                .accept("application/json")
                .form(send);

        if( !resUsers.ok() ) {
            System.err.println(resUsers.body());
            throw new Exception("Error b");
        }
        return parser.parse(resUsers.body())
                .getAsJsonObject()
                .getAsJsonObject("basic_group")
                .get("id")
                .getAsInt();
    }
    JsonObject loadSomeUsers(final int offset, final int STEP) throws Exception {
        final HttpRequest resUsers = HttpRequest.get(base + "groups/trust_level_0/members.json?limit="+STEP+"&offset="+offset+"&show_emails=true&" + auth)
                .trustAllCerts()
                .accept("application/json");

        if( !resUsers.ok() )
            throw new Exception("Error 3 "+resUsers.message()+ ' ' +resUsers.code());

        return parser.parse(resUsers.body())
                .getAsJsonObject();
    }
}
