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

package org.kitodo.data.index.elasticsearch.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Test;

import org.kitodo.data.database.beans.LdapGroup;
import org.kitodo.data.database.beans.User;
import org.kitodo.data.database.beans.UserGroup;
import org.kitodo.data.database.beans.UserProperty;

import static org.junit.Assert.*;

/**
 * Test class for UserType.
 */
public class UserTypeTest {

    private static List<User> prepareData() {

        List<User> users = new ArrayList<>();
        List<UserGroup> userGroups = new ArrayList<>();
        List<UserProperty> userProperties = new ArrayList<>();

        LdapGroup ldapGroup = new LdapGroup();
        ldapGroup.setId(1);

        UserGroup firstUserGroup = new UserGroup();
        firstUserGroup.setId(1);
        userGroups.add(firstUserGroup);

        UserGroup secondUserGroup = new UserGroup();
        secondUserGroup.setId(2);
        userGroups.add(secondUserGroup);

        UserProperty firstUserProperty = new UserProperty();
        firstUserProperty.setTitle("first");
        firstUserProperty.setValue("1");
        userProperties.add(firstUserProperty);

        UserProperty secondUserProperty = new UserProperty();
        secondUserProperty.setTitle("second");
        secondUserProperty.setValue("2");
        userProperties.add(secondUserProperty);

        User firstUser = new User();
        firstUser.setId(1);
        firstUser.setName("Jan");
        firstUser.setSurname("Kowalski");
        firstUser.setLogin("jkowalski");
        firstUser.setActive(true);
        firstUser.setLocation("Dresden");
        firstUser.setLdapGroup(ldapGroup);
        users.add(firstUser);

        User secondUser = new User();
        secondUser.setId(2);
        secondUser.setName("Anna");
        secondUser.setSurname("Nowak");
        secondUser.setLogin("anowak");
        secondUser.setActive(true);
        secondUser.setLocation("Berlin");
        secondUser.setUserGroups(userGroups);
        secondUser.setProperties(userProperties);
        users.add(secondUser);

        User thirdUser = new User();
        thirdUser.setId(3);
        thirdUser.setName("Peter");
        thirdUser.setSurname("Müller");
        thirdUser.setLogin("pmueller");
        thirdUser.setProperties(userProperties);
        users.add(thirdUser);

        return users;
    }

    @Test
    public void shouldCreateDocument() throws Exception {
        UserType userType = new UserType();
        JSONParser parser = new JSONParser();

        User user = prepareData().get(0);
        HttpEntity document = userType.createDocument(user);
        JSONObject userObject = (JSONObject) parser.parse(EntityUtils.toString(document));
        String actual = String.valueOf(userObject.get("name"));
        String excepted = "Jan";
        assertEquals("User value for name key doesn't match to given plain text!", excepted, actual);

        actual = String.valueOf(userObject.get("surname"));
        excepted = "Kowalski";
        assertEquals("User value for surname key doesn't match to given plain text!", excepted, actual);

        actual = String.valueOf(userObject.get("login"));
        excepted = "jkowalski";
        assertEquals("User value for login key doesn't match to given plain text!", excepted, actual);

        actual = String.valueOf(userObject.get("ldapLogin"));
        excepted = "null";
        assertEquals("User value for ldapLogin key doesn't match to given plain text!", excepted, actual);

        actual = String.valueOf(userObject.get("location"));
        excepted = "Dresden";
        assertEquals("User value for location key doesn't match to given plain text!", excepted, actual);

        actual = String.valueOf(userObject.get("active"));
        excepted = "true";
        assertEquals("User JSON string doesn't match to given plain text!", excepted, actual);

        user = prepareData().get(1);
        document = userType.createDocument(user);
        actual = EntityUtils.toString(document);
        excepted = "{\"ldapLogin\":null,\"userGroups\":[{\"id\":\"1\"},{\"id\":\"2\"}],\"ldapGroup\":\"null\","
                + "\"surname\":\"Nowak\",\"name\":\"Anna\",\"metadataLanguage\":null,\"active\":\"true\","
                + "\"location\":\"Berlin\",\"login\":\"anowak\",\"properties\":[{\"title\":\"first\",\"value\":\"1\"},"
                + "{\"title\":\"second\",\"value\":\"2\"}]}";
        assertEquals("User JSON string doesn't match to given plain text!", excepted, actual);

        user = prepareData().get(2);
        document = userType.createDocument(user);
        actual = EntityUtils.toString(document);
        excepted = "{\"ldapLogin\":null,\"userGroups\":[],\"ldapGroup\":\"null\",\"surname\":\"Müller\",\"name\":\"Peter\","
                + "\"metadataLanguage\":null,\"active\":\"true\",\"location\":null,\"login\":\"pmueller\","
                + "\"properties\":[{\"title\":\"first\",\"value\":\"1\"},{\"title\":\"second\",\"value\":\"2\"}]}";
        assertEquals("User JSON string doesn't match to given plain text!", excepted, actual);
    }

    @Test
    public void shouldCreateDocuments() throws Exception {
        UserType userType = new UserType();

        List<User> users = prepareData();
        HashMap<Integer, HttpEntity> documents = userType.createDocuments(users);
        assertEquals("HashMap of documents doesn't contain given amount of elements!", 3, documents.size());
    }
}