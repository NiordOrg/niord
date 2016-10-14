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

package org.niord.core.user.vo;

import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import java.util.List;

/**
 * A value object for the Group entity
 */
@SuppressWarnings("unused")
public class GroupVo implements ILocalizable<GroupDescVo>, IJsonSerializable {

    String id;
    String path;
    GroupVo parent;
    List<GroupVo> children;

    // The Keycloak groups are not really localizable, but using "descs"
    // allows us to reuse the entity-tree angular directive...
    List<GroupDescVo> descs;


    /** {@inheritDoc} */
    @Override
    public GroupDescVo createDesc(String lang) {
        GroupDescVo desc = new GroupDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }

    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public GroupVo getParent() {
        return parent;
    }

    public void setParent(GroupVo parent) {
        this.parent = parent;
    }

    public List<GroupVo> getChildren() {
        return children;
    }

    public void setChildren(List<GroupVo> children) {
        this.children = children;
    }

    @Override
    public List<GroupDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<GroupDescVo> descs) {
        this.descs = descs;
    }
}
