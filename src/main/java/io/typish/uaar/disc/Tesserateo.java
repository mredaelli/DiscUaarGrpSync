package io.typish.uaar.disc;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

class Tesserateo implements AutoCloseable {

    private final Connection connection;
    private final PreparedStatement stmt;

    Tesserateo(final String dbClass, final String dbUrl, final String query) throws Exception {
        Class.forName(dbClass);
        connection = DriverManager.getConnection(dbUrl);
        stmt = connection.prepareStatement(query);
    }

    String circoloDaUtente(final User u) {
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

    @Override
    public void close() throws Exception {
        if( connection != null) try {
            connection.close();
        } catch(final Exception e) {
            e.printStackTrace();
        }
    }
}