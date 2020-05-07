package org.wildfly.halos.proxy;

import javax.ws.rs.core.MediaType;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class InstanceResourceIT {

    @Test
    public void registerExisting() {
        Instance instance = new Instance("wf0", "localhost", 9990, "admin", "admin");
        Instance result = given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(instance)
                .when().post("/v1/instance")
                .then()
                .statusCode(201)
                .extract().as(Instance.class);
        assertEquals(instance, result);
    }

    @Test
    public void registerInvalid() {
        Instance instance = new Instance("nirvana", "come-as-you-are", 1991, null, null);
        given()
                .contentType(MediaType.APPLICATION_JSON)
                .body(instance)
                .when().post("/v1/instance")
                .then()
                .statusCode(500);
    }
}
