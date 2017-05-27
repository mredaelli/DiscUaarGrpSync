package io.typish.uaar.disc;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static java.lang.Thread.sleep;

class Sync {

    private static final Pattern COMPILE = Pattern.compile("[^0-9a-zA-Z_]");

    Sync(final Properties p) throws Exception {

        STEP = Integer.valueOf(p.getProperty("userChunk"));
        gruppoListaCircoli = p.getProperty("gruppo_listacircoli");
        listacircoli_sheet = p.getProperty("listacircoli_sheet");
        listacircoli_sheet_range = p.getProperty("listacircoli_sheet_range");

        final String api_key = p.getProperty("api_key"),
            api_user = p.getProperty("api_user"),
            group_prefix = p.getProperty("group_prefix"),
            base = p.getProperty("baseURL"),
            auth = "api_key="+ api_key +"&api_username="+ api_user;
        discUaar = new DiscUaar(base, auth, group_prefix, gruppoListaCircoli, api_key, api_user);

        props = p;
    }


    private final Properties props;
    private final int STEP;
    private final String gruppoListaCircoli, listacircoli_sheet, listacircoli_sheet_range;
    private Set<String> forListacircoli;

    private Tesserateo tesserateo;
    private final DiscUaar discUaar;

    void run() {
        final String query = props.getProperty("dbQuery"),
                dbClass = props.getProperty("dbClass"),
                dbUrl = props.getProperty("dbUrl"),
                dbUser = props.getProperty("dbUser"),
                dbPassword = props.getProperty("dbPassword");
        final int waitTime = Integer.parseInt(props.getProperty("waitTime"))*1000;

        try( Tesserateo t = new Tesserateo(dbClass, dbUrl, dbUser, dbPassword, query) ) {
            tesserateo = t;

            forListacircoli = Sheet.getCircoliEmails(listacircoli_sheet, listacircoli_sheet_range);

            int offset = 0;
            int read = 0;
            int total;

            do {
                final JsonObject partialRes = discUaar.loadSomeUsers(offset, STEP);
                total = partialRes.get("meta").getAsJsonObject().get("total").getAsInt();

                final JsonArray discUsers = partialRes.get("members")
                        .getAsJsonArray();

                for( final JsonElement u: discUsers ) processUser(u);

                read += discUsers.size();
                offset = read;

                System.out.println("waiting");
                sleep(waitTime);
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

            Set<String> userGroups = discUaar.getUserDetailsAndGroups(u);
            if( u.email == null ) {
                System.err.println("No email for user "+u.username);
                return;
            }

            if( userGroups == null ) userGroups = new HashSet<>();

            // trova il circolo di appartenenza da tesserateo
            u.circolo = tesserateo.circoloDaUtente(u);

            if( u.circolo != null ) {
                String circGroup = props.get("group_prefix") + COMPILE.matcher(u.circolo).replaceAll("")
                        .toLowerCase();
                circGroup = circGroup.substring(0, Math.min(circGroup.length(), 20));
                if( userGroups.contains(circGroup) ) userGroups.remove(circGroup);
                else discUaar.addUserToGroup(u, circGroup);
            }

            // lista circoli?
            if( forListacircoli.contains(u.email) ) {
                if( userGroups.contains(gruppoListaCircoli) ) userGroups.remove(gruppoListaCircoli);
                else discUaar.addUserToGroup(u, gruppoListaCircoli);
            }

            discUaar.removeUserFromGroups(u, userGroups);

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

}
