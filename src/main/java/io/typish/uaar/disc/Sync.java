package io.typish.uaar.disc;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.typish.uaar.disc.Tesserateo.Ruolo;

import static java.lang.Thread.sleep;

class Sync {

    Sync(final Properties p) throws Exception {

        STEP = Integer.valueOf(p.getProperty("userChunk"));
        gruppoListaCircoli = p.getProperty("gruppo_listacircoli");
        gruppoCoordinatori = p.getProperty("gruppo_coordinatori");
        gruppoCassieri = p.getProperty("gruppo_cassieri");
        gruppoReferenti = p.getProperty("gruppo_referenti");
        listacircoli_sheet = p.getProperty("listacircoli_sheet");
        listacircoli_sheet_range = p.getProperty("listacircoli_sheet_range");

        final String api_key = p.getProperty("api_key"),
            api_user = p.getProperty("api_user"),
            group_prefix = p.getProperty("group_prefix"),
            base = p.getProperty("baseURL"),
            auth = "api_key="+ api_key +"&api_username="+ api_user;
        final boolean debug = "true".equals(p.getProperty("debug"));
        discUaar = new DiscUaar(base, auth, group_prefix, gruppoListaCircoli, gruppoCoordinatori, gruppoReferenti, gruppoCassieri, api_key, api_user, debug);

        props = p;
    }


    private final Properties props;
    private final int STEP;
    private final String gruppoListaCircoli, gruppoReferenti, gruppoCoordinatori, gruppoCassieri, listacircoli_sheet, listacircoli_sheet_range;
    private Set<String> forListacircoli;

    private Tesserateo tesserateo;
    private final DiscUaar discUaar;

    void run() {
        final String query = props.getProperty("dbQuery"),
                queryCoord = props.getProperty("dbQueryCoord"),
                queryRefer = props.getProperty("dbQueryRefer"),
                dbClass = props.getProperty("dbClass"),
                dbUrl = props.getProperty("dbUrl"),
                dbUser = props.getProperty("dbUser"),
                dbPassword = props.getProperty("dbPassword");
        final int waitTime = Integer.parseInt(props.getProperty("waitTime"))*1000;

        try( Tesserateo t = new Tesserateo(dbClass, dbUrl, dbUser, dbPassword, query, queryCoord, queryRefer) ) {
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
            final String circGroup = encodeCircolo(u.circolo);
            if( u.circolo != null ) {
                if( userGroups.contains(circGroup) ) userGroups.remove(circGroup);
                else discUaar.addUserToGroup(u, circGroup);
            }

            // lista circoli?
            if( forListacircoli.contains(u.email) ) {
                if( userGroups.contains(gruppoListaCircoli) ) userGroups.remove(gruppoListaCircoli);
                else discUaar.addUserToGroup(u, gruppoListaCircoli);
            }

            // ruoli
            final Ruolo ruolo = tesserateo.ruoloDaEmail(u.email);
            if( ruolo != null ) switch( ruolo.ruoloInCircolo ) {
                case COORDINATORE:
                    if( userGroups.contains(gruppoCoordinatori) ) userGroups.remove(gruppoCoordinatori);
                    else discUaar.addUserToGroup(u, gruppoCoordinatori);
                    break;
                case CASSIERE:
                    if( userGroups.contains(gruppoCassieri) ) userGroups.remove(gruppoCassieri);
                    else discUaar.addUserToGroup(u, gruppoCassieri);
                    break;
                case REFERENTE:
                    if( userGroups.contains(gruppoReferenti) ) userGroups.remove(gruppoReferenti);
                    else discUaar.addUserToGroup(u, gruppoReferenti);
                    break;
            }

            discUaar.removeUserFromGroups(u, userGroups);

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    private static final Pattern COMPILE = Pattern.compile("[^0-9a-zA-Z_]");

    private String encodeCircolo(final String citta) {
        String circGroup = props.get("group_prefix") + COMPILE.matcher(citta).replaceAll("")
                .toLowerCase();
        circGroup = circGroup.substring(0, Math.min(circGroup.length(), 20));
        return circGroup;

    }


}
