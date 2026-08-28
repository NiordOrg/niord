/*
 * Copyright 2026 Danish Maritime Authority.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
    // Generous, and retried. A build that is compiling, booting Quarkus and
    // hammering the same database can take longer than a second to answer a
    // connect -- and a probe that gives up then skips the whole database-backed
    // suite while the build still goes GREEN. That is worse than a slow probe.
    private static final int TIMEOUT_MS = 5000;
    private static final int ATTEMPTS = 3;

    private static Boolean cached;

    private DatabaseAvailable() {
    }

    /** Referenced by @EnabledIf on the database-backed test classes. */
    public static synchronized boolean isAvailable() {
        if (cached == null) {
            Exception last = null;
            for (int attempt = 1; attempt <= ATTEMPTS && cached == null; attempt++) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(HOST, PORT), TIMEOUT_MS);
                    cached = true;
                } catch (Exception e) {
                    last = e;
                    if (attempt < ATTEMPTS) {
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }

            if (cached == null) {
                cached = false;
                // Loud on purpose. A silent skip is how a database-backed suite
                // quietly stops running and nobody notices for weeks.
                System.out.println();
                System.out.println("=========================================================================");
                System.out.println(" No MySQL at " + HOST + ":" + PORT + " after " + ATTEMPTS
                        + " attempts -- database-backed tests are SKIPPED.");
                System.out.println(" Last error: " + last);
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
