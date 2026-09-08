/*
 * Copyright 2026 Danish Emergency Management Agency.
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

package org.niord.core.publication;

/**
 * A message on a publication screen, named the one way they are all named.
 *
 * For the lists that carry NOTHING ELSE about the message -- the probe's sample
 * of what a criteria document would select, and any list like it. A row that
 * carries its own facts (a member, an omission, a standing decision) grows the
 * same three fields on its own VO instead, so the row keeps one shape rather
 * than nesting an identity inside itself.
 *
 * All three fields together, and each for its own reason: {@code messageUid} is
 * the key and addresses the message, {@code messageId} is the short id it is
 * written under and is null until somebody numbers it, {@code title} is what it
 * is called today in the language the list was asked for. A list that sent only
 * the uid would be a column of uuids -- unreadable, unquotable, and useless to
 * the admin judging what the document selects.
 *
 * @see MessageNaming for how the two names are filled, in one query per list
 */
public record NamedMessageVo(String messageUid, String messageId, String title) {
}
