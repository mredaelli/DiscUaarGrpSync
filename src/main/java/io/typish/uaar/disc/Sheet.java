package io.typish.uaar.disc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow.Builder;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;

class Sheet {
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS_READONLY);

    private static NetHttpTransport HTTP_TRANSPORT;
    private static FileDataStoreFactory DATA_STORE_FACTORY;
    private static final JsonFactory JSON_FACTORY =
            JacksonFactory.getDefaultInstance();
    private static final java.io.File DATA_STORE_DIR = new java.io.File(
            System.getProperty("user.home"), ".credentials/sheets.googleapis.com-java-quickstart");


    static {
        try {
            HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
        } catch (final Throwable t) {
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static Credential authorize() throws IOException {
        // Load client secrets.
        final InputStream in =
                Main.class.getResourceAsStream("/client_secret.json");
        final GoogleClientSecrets clientSecrets =
                GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        final GoogleAuthorizationCodeFlow flow =
                new Builder(
                        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                        .setDataStoreFactory(DATA_STORE_FACTORY)
                        .setAccessType("offline")
                        .build();
        final Credential credential = new AuthorizationCodeInstalledApp(
                flow, new LocalServerReceiver()).authorize("user");
        System.out.println(
                "Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
        return credential;
    }

     private static Sheets getSheetsService() throws IOException {
        final Credential credential = authorize();
        return new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName("DiscuaarSync")
                .build();
    }

    static Set<String> getCircoliEmails(final String spreadsheetId, final String range) throws Exception {

        final Sheets service = getSheetsService();

        final ValueRange response = service.spreadsheets().values()
                .get(spreadsheetId, range)
                .execute();
        final List<List<Object>> values = response.getValues();
        if (values == null || values.isEmpty() ) {
            System.err.println("No data found.");
        } else {
            final Set<String> res = new HashSet<>();
            for ( final List<Object> row : values) {
                res.add(row.get(0).toString().trim().toLowerCase());
            }
            return res;
        }
        return Collections.emptySet();
    }

}
