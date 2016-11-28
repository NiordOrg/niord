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

package org.niord.core.repo;

/**
 * Can be implemented by value objects that are backed by a repository folder.
 * <p>
 * The value object will be associated with a repository folder in the repoPath field.
 * When the value object is being edited, it should be assigned an editRepoPath, and upon saving
 * the object, the files added to the editRepoPath will should be copied back to the editPath folder.
 * <p>
 * To preserve the audit log, files are never deleted from the repoPath folder, only added.
 * To avoid file name collisions, all files should be placed in a revision sub-folder of the editRepoPath folder.
 */
public interface IRepoBackedVo {

    /** Returns the current revision of the value object **/
    int getRevision();

    /** Returns the permanent repository folder associated with the value object **/
    String getRepoPath();

    /** Returns the temporary edit repository folder associated with the value object **/
    String getEditRepoPath();

    /** Sets the temporary edit repository folder associated with the value object **/
    void setEditRepoPath(String editRepoPath);

    /** Rewrite nested links and file paths from one repo path to another **/
    void rewriteRepoPath(String repoPath1, String repoPath2);

}
