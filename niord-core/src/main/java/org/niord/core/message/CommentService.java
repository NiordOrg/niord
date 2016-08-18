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

package org.niord.core.message;

import org.niord.core.service.BaseService;
import org.niord.core.user.User;
import org.slf4j.Logger;

import javax.ejb.Stateless;
import javax.inject.Inject;
import java.util.Date;
import java.util.List;

/**
 * Business interface for managing message comments
 */
@Stateless
@SuppressWarnings("unused")
public class CommentService extends BaseService {

    @Inject
    private Logger log;

    /**
     * Returns the message comments for the given message UID
     *
     * @param uid the message UID
     * @return the message comments
     */
    public List<Comment> getComments(String uid) {
        return em.createNamedQuery("Comment.findByMessageUid", Comment.class)
                .setParameter("uid", uid)
                .getResultList();
    }


    /**
     * Creates a new message comment
     *
     * @param comment the comment template
     * @return the persisted message comment
     */
    public Comment createComment(Comment comment) {
        if (comment.getMessage() == null || comment.getUser() == null) {
            throw new IllegalArgumentException("Message or user not defined");
        }
        comment.getMessage().getComments().add(comment);
        saveEntity(comment);
        return comment;
    }


    /**
     * Updates the message comment. The only thing that can be updated using this method is
     * the actual comment of the entity.
     * The comment will subsequently be flagged as unacknowledged.
     *
     * @param comment the comment to update
     * @return the updated message comment
     */
    public Comment updateComment(Comment comment) {
        Comment original = getByPrimaryKey(Comment.class, comment.getId());
        if (original == null) {
            throw new IllegalArgumentException("No comment with id " + comment.getId());
        }

        original.setComment(comment.getComment());
        original.setAcknowledgeDate(null);
        original.setAcknowledgedBy(null);
        saveEntity(original);

        return original;
    }


    /**
     * Acknowledges the message comment.
     *
     * @param user the user
     * @param commentId the ID of the comment to acknowledge
     * @return the acknowledged message comment
     */
    public Comment acknowledgeComment(User user, Integer commentId) {
        if (user == null) {
            throw new IllegalArgumentException("User must be defined");
        }
        Comment original = getByPrimaryKey(Comment.class,commentId);
        if (original == null) {
            throw new IllegalArgumentException("No comment with id " + commentId);
        }

        original.setAcknowledgedBy(user);
        original.setAcknowledgeDate(new Date());
        saveEntity(original);

        return original;
    }

}
