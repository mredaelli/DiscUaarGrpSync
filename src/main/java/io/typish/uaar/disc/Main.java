package io.typish.uaar.disc;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public class Main {

       public static void main(final String[] args) throws Exception {

        final String pFile;
        switch( args.length ) {
            case 0:
                pFile = "conf.properties";
                break;
            default:
                pFile = args[0];
                if( args.length > 1 )
                    System.out.println("Some ignored parameters");
        }

        final Properties p = new Properties();
        try(final InputStream is = Files.newInputStream(Paths.get(pFile))) { p.load(is); }

        new Sync(p).run();
    }
}
