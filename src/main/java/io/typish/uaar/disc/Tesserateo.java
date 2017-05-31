package io.typish.uaar.disc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

class Tesserateo implements AutoCloseable {

    private final Connection connection;
    private final PreparedStatement stmt;

    Tesserateo(final String dbClass, final String dbUrl, final String dbUser, final String dbPassword, final String query, final String queryCoord, final String queryRefer) throws Exception {
        Class.forName(dbClass);
        connection = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
        stmt = connection.prepareStatement(query);

        final PreparedStatement stmtCoord = connection.prepareStatement(queryCoord);
        final PreparedStatement stmtRefer = connection.prepareStatement(queryRefer);

        try {
            final ResultSet rs = stmtCoord.executeQuery();
            while( rs.next() ) {
                final String circolo = rs.getString("citta");
                final String coord = rs.getString("coordinatore");
                final String cassiere = rs.getString("cassiere");
                ruoli.put(coord, new Ruolo(circolo, RuoloInCircolo.COORDINATORE));
                ruoli.put(cassiere, new Ruolo(circolo, RuoloInCircolo.CASSIERE));
            }
            final ResultSet rs1 = stmtRefer.executeQuery();
            while( rs1.next() ) {
                final String circolo = rs1.getString("citta");
                final String referente = rs1.getString("referente");
                ruoli.put(referente, new Ruolo(circolo, RuoloInCircolo.REFERENTE));
            }
        } catch(final Exception e) {
            e.printStackTrace();
            throw new Exception("Impossibile leggere i dati dei circoli");
        }
    }

    String circoloDaUtente(final User u) {
        try {
            stmt.setString(1, u.email);
            stmt.setInt(2, 1900 + new Date().getYear());
            final ResultSet rs = stmt.executeQuery();
            String res = null;
            if( rs.next() ) {
                res = rs.getString("circolo");
                if( rs.next() ) throw new Exception("More than one " + u.email);
            } else {
                System.err.println("Not found " + u.email);
            }
            return res;
        } catch(final Exception e ){
            e.printStackTrace();
            return null;
        }
    }


    enum RuoloInCircolo { CASSIERE, COORDINATORE, REFERENTE }
    static class Ruolo {
        RuoloInCircolo ruoloInCircolo;
        String circolo;

        Ruolo(final String circolo, final RuoloInCircolo cassiere) {
            this.circolo = circolo;
            ruoloInCircolo = cassiere;
        }
    }
    private final Map<String, Ruolo> ruoli = new HashMap<>();

    Ruolo ruoloDaEmail(final String email) {
        return ruoli.get(email);
    }

    @Override
    public void close() throws Exception {
        if( connection != null) try {
            connection.close();
        } catch(final Exception e) {
            e.printStackTrace();
        }
    }
}
