/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.data.elasticsearch.index.type;

import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.UserGroup;

/**
 * Implementation of User Type.
 */
public class UserType extends BaseType<User> {

    @SuppressWarnings("unchecked")
    @Override
    public HttpEntity createDocument(User user) {

        JSONObject userObject = new JSONObject();
        userObject.put("name", user.getName());
        userObject.put("surname", user.getSurname());
        userObject.put("login", user.getLogin());
        userObject.put("ldapLogin", user.getLdapLogin());
        userObject.put("active", String.valueOf(user.isActive()));
        userObject.put("location", user.getLocation());
        userObject.put("metadataLanguage", user.getMetadataLanguage());

        JSONArray userGroups = new JSONArray();
        List<UserGroup> userUserGroups = user.getUserGroups();
        for (UserGroup userGroup : userUserGroups) {
            userGroups.add(addIdForRelation(userGroup.getId()));
        }
        userObject.put("userGroups", userGroups);

        userObject.put("properties", addPropertyRelation(user.getProperties()));

        return new NStringEntity(userObject.toJSONString(), ContentType.APPLICATION_JSON);
    }
}
