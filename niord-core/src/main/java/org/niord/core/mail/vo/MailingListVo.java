/*
 * Copyright 2017 Danish Maritime Authority.
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

package org.niord.core.mail.vo;

import org.niord.core.user.vo.ContactVo;
import org.niord.core.user.vo.UserVo;
import org.niord.model.IJsonSerializable;
import org.niord.model.ILocalizable;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines the value object of a mailing list.
 */
@SuppressWarnings("unused")
public class MailingListVo implements ILocalizable<MailingListDescVo>, IJsonSerializable {

    String mailingListId;
    Boolean active;
    List<UserVo> users;
    List<ContactVo> contacts;
    List<MailingListDescVo> descs;
    Integer recipientNo;


    /** {@inheritDoc} */
    @Override
    public MailingListDescVo createDesc(String lang) {
        MailingListDescVo desc = new MailingListDescVo();
        desc.setLang(lang);
        checkCreateDescs().add(desc);
        return desc;
    }


    public List<UserVo> checkCreateUsers() {
        if (users == null) {
            users = new ArrayList<>();
        }
        return users;
    }


    public List<ContactVo> checkCreateContacts() {
        if (contacts == null) {
            contacts = new ArrayList<>();
        }
        return contacts;
    }


    /*************************/
    /** Getters and Setters **/
    /*************************/

    public String getMailingListId() {
        return mailingListId;
    }

    public void setMailingListId(String mailingListId) {
        this.mailingListId = mailingListId;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public List<UserVo> getUsers() {
        return users;
    }

    public void setUsers(List<UserVo> users) {
        this.users = users;
    }

    public List<ContactVo> getContacts() {
        return contacts;
    }

    public void setContacts(List<ContactVo> contacts) {
        this.contacts = contacts;
    }

    public Integer getRecipientNo() {
        return recipientNo;
    }

    public void setRecipientNo(Integer recipientNo) {
        this.recipientNo = recipientNo;
    }

    @Override
    public List<MailingListDescVo> getDescs() {
        return descs;
    }

    @Override
    public void setDescs(List<MailingListDescVo> descs) {
        this.descs = descs;
    }
}
