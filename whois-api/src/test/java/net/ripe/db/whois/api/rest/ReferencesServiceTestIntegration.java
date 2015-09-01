package net.ripe.db.whois.api.rest;

import net.ripe.db.whois.api.AbstractIntegrationTest;
import net.ripe.db.whois.api.RestTest;
import net.ripe.db.whois.api.rest.domain.Attribute;
import net.ripe.db.whois.api.rest.domain.ErrorMessage;
import net.ripe.db.whois.api.rest.domain.WhoisObject;
import net.ripe.db.whois.api.rest.domain.WhoisResources;
import net.ripe.db.whois.api.rest.mapper.FormattedClientAttributeMapper;
import net.ripe.db.whois.api.rest.mapper.WhoisObjectMapper;
import net.ripe.db.whois.common.IntegrationTest;
import net.ripe.db.whois.common.Message;
import net.ripe.db.whois.common.Messages;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.springframework.beans.factory.annotation.Autowired;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Category(IntegrationTest.class)
public class ReferencesServiceTestIntegration extends AbstractIntegrationTest {

    @Autowired
    private WhoisObjectMapper whoisObjectMapper;

    @Before
    public void setup() {
        databaseHelper.addObject(
                "role:          dummy role\n" +
                "nic-hdl:       DR1-TEST");
        databaseHelper.addObject(
                "person:        Test Person\n" +
                "nic-hdl:       TP1-TEST");
        databaseHelper.addObject(
                "role:          Test Role\n" +
                "nic-hdl:       TR1-TEST");
        databaseHelper.addObject(
                "mntner:        OWNER-MNT\n" +
                "descr:         Owner Maintainer\n" +
                "admin-c:       TP1-TEST\n" +
                "upd-to:        noreply@ripe.net\n" +
                "auth:          MD5-PW $1$d9fKeTr2$Si7YudNf4rUGmR71n/cqk/ #test\n" +
                "mnt-by:        OWNER-MNT\n" +
                "source:        TEST");
        databaseHelper.updateObject(
                "person:        Test Person\n" +
                "address:       Singel 258\n" +
                "phone:         +31 6 12345678\n" +
                "nic-hdl:       TP1-TEST\n" +
                "mnt-by:        OWNER-MNT\n" +
                "source:        TEST");
    }

    // CREATE

    @Test
    public void create_person_mntner_pair_success() {
        final WhoisResources whoisResources =
            createWhoisResources(
                RpslObject.parse(
                    "person:    Some Person\n" +
                    "address:   Amsterdam\n" +
                    "phone:     +3161234\n" +
                    "nic-hdl:   AUTO-1\n" +
                    "mnt-by:    NEW-UHUUU9998-MNT\n" +            // TODO: fix reference on server side (create object)
                    "source:    TEST"),
                RpslObject.parse(
                    "mntner:    NEW-UHUUU9998-MNT\n" +
                    "descr:     Maintainer\n" +
                    "admin-c:   AUTO-1\n" +             // TODO: fix reference (or try AUTO-1)
                    "upd-to:    person@net.net\n" +       // TODO: sso credential
                    "auth:      SSO person@net.net\n" +       // TODO: sso credential
                    "mnt-by:    NEW-UHUUU9998-MNT\n" +
                    "source:    TEST"));

        final WhoisResources response = RestTest.target(getPort(), "whois/references/TEST")
            .request()
            .cookie("crowd.token_key", "valid-token")
            .post(Entity.entity(whoisResources, MediaType.APPLICATION_JSON_TYPE), WhoisResources.class);

        assertThat(response.getErrorMessages(), hasSize(2));

        List<WhoisObject> whoisObjects = response.getWhoisObjects();
        assertThat(whoisObjects, hasSize(2));

        final WhoisObject personObject = getWhoisObject("person", whoisObjects);
        assertThat("NEW-UHUUU9998-MNT", equalToIgnoringCase(getAttr("mnt-by", personObject)));
        String nicHdl = getAttr("nic-hdl", personObject);
        assertNotEquals("AUTO-1", nicHdl.toUpperCase());

        final WhoisObject mntnerObject = getWhoisObject("mntner", whoisObjects);
        assertThat(nicHdl, equalToIgnoringCase(getAttr("admin-c", mntnerObject)));



    }

    private String getAttr(String attrName, WhoisObject personObject) {
        for(Attribute attr: personObject.getAttributes()){
            if(attrName.equalsIgnoreCase(attr.getName())){
                 return attr.getValue();
            }
        }

        return null;
    }

    private WhoisObject getWhoisObject(String type, List<WhoisObject> whoisObjects) {
        for(WhoisObject object: whoisObjects) {
            if (type.equalsIgnoreCase(object.getType())) {
                return object;
            }
        }

        return null;
    }

    // READ

    @Test
    public void lookup_mntner_references_success() {
        final ReferencesService.Reference response = RestTest.target(getPort(), "whois/references/TEST/mntner/OWNER-MNT")
            .request()
            .get(ReferencesService.Reference.class);

        assertThat(response.getPrimaryKey(), is("OWNER-MNT"));
        assertThat(response.getObjectType(), is("mntner"));

        // TODO: owner-mnt is not listed as a self-reference
        final List<ReferencesService.Reference> incomingReferences = response.getIncoming();
        assertThat(incomingReferences, hasSize(1));
        assertThat(incomingReferences.get(0).getPrimaryKey(), is("TP1-TEST"));
        assertThat(incomingReferences.get(0).getObjectType(), is("person"));
    }

    @Test
    public void lookup_mntner_references_success_xml() {
        final String response = RestTest.target(getPort(), "whois/references/TEST/mntner/OWNER-MNT.xml")
            .request()
            .get(String.class);

        assertThat(response, is(
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
            "<references>" +
            "<primaryKey>OWNER-MNT</primaryKey>" +
            "<objectType>mntner</objectType>" +
            "<incoming>" +
            "<references>" +
            "<primaryKey>TP1-TEST</primaryKey>" +
            "<objectType>person</objectType>" +
            "<incoming>" +
            "<references>" +
            "<primaryKey>OWNER-MNT</primaryKey>" +
            "<objectType>mntner</objectType>" +
            "<incoming/>" +
            "<outgoing/>" +
            "</references>" +
            "</incoming>" +
            "<outgoing/>" +
            "</references>" +
            "</incoming>" +
            "<outgoing/>" +
            "</references>"));
    }

    @Test
    public void lookup_mntner_references_success_json() {
        final String response = RestTest.target(getPort(), "whois/references/TEST/mntner/OWNER-MNT.json")
            .request()
            .get(String.class);

        assertThat(response, is(
                "{\n" +
                "  \"primaryKey\" : \"OWNER-MNT\",\n" +
                "  \"objectType\" : \"mntner\",\n" +
                "  \"incoming\" : [ {\n" +
                "    \"primaryKey\" : \"TP1-TEST\",\n" +
                "    \"objectType\" : \"person\",\n" +
                "    \"incoming\" : [ {\n" +
                "      \"primaryKey\" : \"OWNER-MNT\",\n" +
                "      \"objectType\" : \"mntner\"\n" +
                "    } ]\n" +
                "  } ]\n" +
                "}"));
    }

    @Test
    public void lookup_mntner_references_invalid_object_type() {
        try {
            RestTest.target(getPort(), "whois/references/TEST/invalid/OWNER-MNT")
                .request()
                .get(String.class);
            fail();
        } catch (BadRequestException e) {
            final WhoisResources response = e.getResponse().readEntity(WhoisResources.class);
            assertThat(response.getErrorMessages(), contains(new ErrorMessage(new Message(Messages.Type.ERROR, "Invalid object type: invalid"))));
        }
    }

    @Test
    public void lookup_mntner_references_invalid_primary_key() {
        try {
            RestTest.target(getPort(), "whois/references/TEST/mntner/invalid")
                .request()
                .get(String.class);
            fail();
        } catch (NotFoundException e) {
            final WhoisResources response = e.getResponse().readEntity(WhoisResources.class);
            assertThat(response.getErrorMessages(), contains(new ErrorMessage(new Message(Messages.Type.ERROR, "Not Found"))));
        }
    }

    // DELETE


    // OWNER-MNT <- TP1-TEST
    @Test
    public void delete_mntner_success() {
        RestTest.target(getPort(), "whois/references/TEST/mntner/OWNER-MNT?password=test")
            .request()
            .delete();

        assertThat(objectExists(ObjectType.MNTNER, "OWNER-MNT"), is(false));
        assertThat(objectExists(ObjectType.PERSON, "TP1-TEST"), is(false));
    }

    // TP1-TEST <- OWNER-MNT
    @Test
    public void delete_person_success() {
        RestTest.target(getPort(), "whois/references/TEST/person/TP1-TEST?password=test")
            .request()
            .delete();

        assertThat(objectExists(ObjectType.MNTNER, "OWNER-MNT"), is(false));
        assertThat(objectExists(ObjectType.PERSON, "TP1-TEST"), is(false));
    }

    @Test
    public void delete_object_multiple_references_succeeds() {
        databaseHelper.addObject(
                "person:        Test Person2\n" +
                "address:       Singel 258\n" +
                "phone:         +31 6 12345678\n" +
                "nic-hdl:       TP2-TEST\n" +
                "mnt-by:        OWNER-MNT\n" +
                "source:        TEST");

        databaseHelper.addObject(
                "role:        Test Role\n" +
                "address:       Singel 258\n" +
                "phone:         +31 6 12345678\n" +
                "nic-hdl:       TR2-TEST\n" +
                "mnt-by:        OWNER-MNT\n" +
                "source:        TEST");

        RestTest.target(getPort(), "whois/references/TEST/mntner/OWNER-MNT?password=test")
                .request()
                .delete();

        assertThat(objectExists(ObjectType.MNTNER, "OWNER-MNT"), is(false));
        assertThat(objectExists(ObjectType.PERSON, "TP1-TEST"), is(false));
        assertThat(objectExists(ObjectType.PERSON, "TP2-TEST"), is(false));
        assertThat(objectExists(ObjectType.ROLE, "TR2-TEST"), is(false));
    }

    @Test
    public void delete_object_with_outgoing_references_only() {
        databaseHelper.addObject(
                "role:        Test Role\n" +
                "address:       Singel 258\n" +
                "phone:         +31 6 12345678\n" +
                "nic-hdl:       TR2-TEST\n" +
                "mnt-by:        OWNER-MNT\n" +
                "source:        TEST");

        RestTest.target(getPort(), "whois/references/TEST/role/TR2-TEST?password=test")
                .request()
                .delete();

        assertThat(objectExists(ObjectType.MNTNER, "OWNER-MNT"), is(true));
        assertThat(objectExists(ObjectType.ROLE, "TR2-TEST"), is(false));
    }

    @Test
    public void delete_non_mntner_or_role() {
        databaseHelper.addObject(
                "organisation:    ORG-TO1-TEST\n" +
                "org-type:        other\n" +
                "org-name:        First Org\n" +
                "address:         RIPE NCC\n" +
                "e-mail:          dbtest@ripe.net\n" +
                "mnt-by:          OWNER-MNT\n" +
                "source:          TEST");
        try{
            RestTest.target(getPort(), "whois/references/TEST/organisation/ORG-TO1-TEST?password=test")
                    .request()
                    .delete(String.class);
            fail();
        } catch (BadRequestException e) {
            RestTest.assertOnlyErrorMessage(e, "Error", "Object type ORGANISATION is not supported.");

            assertThat(objectExists(ObjectType.MNTNER, "OWNER-MNT"), is(true));
            assertThat(objectExists(ObjectType.ORGANISATION, "ORG-TO1-TEST"), is(true));
        }
    }

    // OWNER-MNT <- TP1-TEST <- ANOTHER-MNT
    @Test
    public void delete_mntner_fails_person_referenced_from_another_mntner() {
        databaseHelper.addObject(
                "mntner:        ANOTHER-MNT\n" +
                "descr:         Another Maintainer\n" +
                "admin-c:       TP1-TEST\n" +
                "upd-to:        noreply@ripe.net\n" +
                "auth:          MD5-PW $1$d9fKeTr2$Si7YudNf4rUGmR71n/cqk/ #test\n" +
                "mnt-by:        ANOTHER-MNT\n" +
                "source:        TEST");

        final Response response = RestTest.target(getPort(), "whois/references/TEST/mntner/OWNER-MNT")
                                    .request()
                                    .delete();

        assertThat(response.getStatus(), is(Response.Status.BAD_REQUEST.getStatusCode()));
        assertThat(response.readEntity(String.class), containsString("Referencing object TP1-TEST itself is referenced by ANOTHER-MNT"));
    }

    @Test
    public void delete_mntner_fails_because_of_authorisation() {
        final Response response = RestTest.target(getPort(), "whois/references/TEST/mntner/OWNER-MNT")
                .request()
                .delete();

        assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    @Test
    public void delete_person_fails_because_of_authorisation() {
        final Response response = RestTest.target(getPort(), "whois/references/TEST/person/TP1-TEST")
                .request()
                .delete();

        assertThat(response.getStatus(), is(Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    @Ignore
    @Test
    public void delete_response_contains_error_message() {

        try {
            RestTest.target(getPort(), "whois/references/TEST/person/TP1-TEST")
                    .request()
                    .delete(String.class);
            fail();
        } catch (NotAuthorizedException e) {
            RestTest.assertOnlyErrorMessage(e, "Error", "Authorisation for [%s] %s failed\nusing \"%s:\"\nnot authenticated by: %s", "person", "TP1-TEST", "mnt-by", "OWNER-MNT");
        }
    }

    // helper methods

    private boolean objectExists(final ObjectType objectType, final String primaryKey) {
        try {
            RestTest.target(getPort(),
                String.format("whois/TEST/%s/%s", objectType.getName(), primaryKey))
                .request()
                .get(WhoisResources.class);
            return true;
        } catch (NotFoundException e) {
            return false;
        }
    }

    private WhoisResources createWhoisResources(final RpslObject ... rpslObjects) {
        return whoisObjectMapper.mapRpslObjects(FormattedClientAttributeMapper.class, rpslObjects);
    }

}