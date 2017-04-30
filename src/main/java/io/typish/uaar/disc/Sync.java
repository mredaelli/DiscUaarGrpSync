package io.typish.uaar.disc;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

class Sync {

    Sync(final Properties p) throws Exception {

        STEP = Integer.valueOf(p.getProperty("userChunk"));
        gruppoListaCircoli = p.getProperty("gruppo_listacircoli");
        file_listacircoli = p.getProperty("file_listacircoli");

        final String api_key = p.getProperty("api_key"),
            api_user = p.getProperty("api_user"),
            group_prefix = p.getProperty("group_prefix"),
            base = p.getProperty("baseURL"),
            auth = "api_key="+ api_key +"&api_username="+ api_user;
        discUaar = new DiscUaar(base, auth, group_prefix, api_key, api_user);

        props = p;
    }


    private final Properties props;
    private final int STEP;
    private final String gruppoListaCircoli, file_listacircoli;
    private Set<String> forListacircoli;

    private Tesserateo tesserateo;
    private final DiscUaar discUaar;

    void run() {
        final String query = props.getProperty("dbQuery"),
                dbClass = props.getProperty("dbClass"),
                dbUrl = props.getProperty("dbUrl");

        try( Tesserateo t = new Tesserateo(dbClass, dbUrl, query) ) {
            tesserateo = t;

            forListacircoli = new HashSet<>(Files.readAllLines(Paths.get(file_listacircoli)));

            int offset = 0;
            int read = 0;
            int total;

            do {
                final JsonObject partialRes = discUaar.loadSomeUsers(offset, STEP);
                total = partialRes.get("meta").getAsJsonObject().get("total").getAsInt();

                final JsonArray discUsers = partialRes.get("members")
                        .getAsJsonArray();

                discUsers.forEach(this::processUser);

                read += discUsers.size();
                offset = read;
            } while(total > read);

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }



    private void processUser(final JsonElement du) {
        try {
            final User u = new User();

            u.username = du.getAsJsonObject().get("username").getAsString();
            System.out.println("Processing "+u.username);

            final Set<String> userGroups = discUaar.getGroupsForUser(u);
            if( userGroups == null ) return;

            // trova il circolo di appartenenza da tesserateo
            u.circolo = tesserateo.circoloDaUtente(u);

            if( u.circolo != null ) {
                if( userGroups.contains(u.circolo) ) userGroups.remove(u.circolo);
                else System.out.println(u.circolo + 1);;//discUaar.addUserToGroup(u, u.circolo);
            }

            // lista circoli?
            if( forListacircoli.contains(u.email) ) {
                System.out.println("In lista circoli");
                if( userGroups.contains(gruppoListaCircoli) ) userGroups.remove(gruppoListaCircoli);
                else System.out.println(2); //discUaar.addUserToGroup(u, gruppoListaCircoli);
            }

            //discUaar.removeUserFromGroups(u, userGroups);

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

}
