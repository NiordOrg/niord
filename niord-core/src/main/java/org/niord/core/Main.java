package org.niord.core;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * The Main Quarkus Runner.
 *
 * @author Nikolaos Vastardis (email: Nikolaos.Vastardis@gla-rad.org)
 */
@QuarkusMain
public class Main {

    /**
     * The main function for the Quarkus Niord Application.
     * @param args The input arguments
     */
    public static void main(String... args) {
        Quarkus.run(NiordApp.class, args);
    }

}
