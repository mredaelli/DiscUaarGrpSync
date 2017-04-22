package io.typish.uaar.disc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Main {
    public static void main(final String[] args) throws Exception {
        final String key;
        final String pFile;
        switch( args.length ) {
            case 0:
                System.out.println("Missing argument api_key");
                return;
            case 1:
                pFile = "conf.properties";
                key = args[0];
                break;
            default:
                pFile = args[1];
                key = args[0];
                if( args.length > 2 )
                    System.out.println("Some ignored parameters");
        }

        final Properties p = new Properties();
        try(final InputStream is = Files.newInputStream(Paths.get(pFile))) { p.load(is); }
        p.put("api_key", key);

        new Sync(p).run();
    }
}
