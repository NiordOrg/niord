package org.niord.core;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Whether the test database is reachable.
 *
 * Database-backed tests are skipped when it is not, so that a build agent with
 * no MySQL runs the pure suite instead of failing outright. Without this the
 * whole reactor fails on a machine that was never expected to have a database
 * -- which is exactly what happened the first time these tests reached CI.
 *
 * The skip is deliberately NARROW. Only the classes that genuinely need a
 * database carry the annotation; the pure tests, which are most of them, always
 * run. A blanket "skip if anything is missing" would recreate the failure mode
 * the suite guard exists to prevent: a green build that ran nothing.
 */
public final class DatabaseAvailable {

    private static final String HOST = System.getProperty("niord.test.db.host", "localhost");
    private static final int PORT = Integer.getInteger("niord.test.db.port", 13306);
    private static final int TIMEOUT_MS = 1500;

    private static Boolean cached;

    private DatabaseAvailable() {
    }

    /** Referenced by @EnabledIf on the database-backed test classes. */
    public static synchronized boolean isAvailable() {
        if (cached == null) {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(HOST, PORT), TIMEOUT_MS);
                cached = true;
            } catch (Exception e) {
                cached = false;
                // Loud on purpose. A silent skip is how a database-backed suite
                // quietly stops running and nobody notices for weeks.
                System.out.println();
                System.out.println("=========================================================================");
                System.out.println(" No MySQL at " + HOST + ":" + PORT + " -- database-backed tests are SKIPPED.");
                System.out.println(" The pure suite still runs. To run everything, start the container:");
                System.out.println("   docker run -d --name niord-test-db -p 13306:3306 \\");
                System.out.println("     -e MYSQL_ROOT_PASSWORD=mysql -e MYSQL_DATABASE=niord \\");
                System.out.println("     -e MYSQL_USER=niord -e MYSQL_PASSWORD=niord mysql:8.0.35");
                System.out.println(" then seed it with schema/baseline-MaDaMe.sql -- see the root README.");
                System.out.println("=========================================================================");
                System.out.println();
            }
        }
        return cached;
    }
}
