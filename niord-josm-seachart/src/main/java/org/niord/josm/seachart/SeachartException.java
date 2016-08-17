/*
 * Copyright 2016 Danish Maritime Authority.
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
package org.niord.josm.seachart;

/**
 * The existing JOSM seachart code has been modified to avoid calls to System.exit().
 * Instead, this RuntimeException will be thrown
 */
@SuppressWarnings("unused")
public class SeachartException extends RuntimeException {

    public SeachartException() {
    }

    public SeachartException(String message) {
        super(message);
    }

    public SeachartException(String message, Throwable cause) {
        super(message, cause);
    }

    public SeachartException(Throwable cause) {
        super(cause);
    }
}
