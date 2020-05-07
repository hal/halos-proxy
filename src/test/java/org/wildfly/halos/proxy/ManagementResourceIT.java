package org.wildfly.halos.proxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.ws.rs.core.MediaType;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.config;
import static io.restassured.RestAssured.given;
import static io.restassured.config.EncoderConfig.encoderConfig;
import static org.jboss.as.controller.client.helpers.ClientConstants.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wildfly.halos.proxy.ManagementResource.DMR_ENCODED;

@QuarkusTest
public class ManagementResourceIT {

    private static final Instance WF_0 = new Instance("wf0", "localhost", 9990, "admin", "admin");
    private static final Instance WF_1 = new Instance("wf1", "localhost", 9991, "admin", "admin");
    private static final Instance WF_2 = new Instance("wf2", "localhost", 9992, "admin", "admin");

    @BeforeEach
    void setUp() {
        given().contentType(MediaType.APPLICATION_JSON).body(WF_0).when().post("/v1/instance").thenReturn();
        given().contentType(MediaType.APPLICATION_JSON).body(WF_1).when().post("/v1/instance").thenReturn();
        given().contentType(MediaType.APPLICATION_JSON).body(WF_2).when().post("/v1/instance").thenReturn();
    }

    @AfterEach
    void tearDown() {
        given().contentType(MediaType.APPLICATION_JSON).body(WF_0).when().delete("/v1/instance").thenReturn();
        given().contentType(MediaType.APPLICATION_JSON).body(WF_1).when().delete("/v1/instance").thenReturn();
        given().contentType(MediaType.APPLICATION_JSON).body(WF_2).when().delete("/v1/instance").thenReturn();
    }

    @Test
    void execute() throws IOException {
        ModelNode operation = operation(new ModelNode().setEmptyList(), READ_RESOURCE_OPERATION);
        String base64 = ModelNodeUtils.base64(operation);
        InputStream inputStream = given()
                .config(config().encoderConfig(encoderConfig().encodeContentTypeAs(DMR_ENCODED, ContentType.TEXT)))
                .contentType(DMR_ENCODED)
                .body(base64)
                .when().post("/v1/management")
                .then()
                .extract().asInputStream();
        ModelNode result = ModelNode.fromBase64(inputStream);
        assertNotNull(result);
        List<Property> properties = result.asPropertyList();
        assertEquals(3, properties.size());
        for (Property property : properties) {
            assertEquals(SUCCESS, property.getValue().get(OUTCOME).asString());
        }
    }

    private ModelNode operation(ModelNode address, String name) {
        ModelNode operation = new ModelNode();
        operation.get(ADDRESS).set(address);
        operation.get(OP).set(name);
        return operation;
    }
}
