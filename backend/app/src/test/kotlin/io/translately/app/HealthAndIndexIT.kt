package io.translately.app

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.jupiter.api.Test

@QuarkusTest
class HealthAndIndexIT {
    @Test
    fun `liveness probe returns UP`() {
        given()
            .`when`()
            .get("/q/health/live")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test
    fun `readiness probe returns UP`() {
        given()
            .`when`()
            .get("/q/health/ready")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test
    fun `started probe returns UP`() {
        given()
            .`when`()
            .get("/q/health/started")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test
    fun `aggregate health endpoint returns UP`() {
        given()
            .`when`()
            .get("/q/health")
            .then()
            .statusCode(200)
            .body("status", equalTo("UP"))
    }

    @Test
    fun `index returns service metadata`() {
        given()
            .`when`()
            .get("/")
            .then()
            .statusCode(200)
            .body("name", equalTo("translately"))
            .body("version", notNullValue())
            .body("health", containsString("/q/health"))
            .body("openapi", containsString("/q/openapi"))
            .body("docs", containsString("github.com/Pratiyush/translately"))
    }

    @Test
    fun `openapi endpoint is reachable`() {
        given()
            .`when`()
            .get("/q/openapi")
            .then()
            .statusCode(200)
    }
}
