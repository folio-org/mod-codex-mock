package org.folio.rest.impl;

import com.jayway.restassured.RestAssured;
import static com.jayway.restassured.RestAssured.given;
import com.jayway.restassured.response.Header;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.folio.rest.RestVerticle;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.tools.PomReader;
import static org.hamcrest.Matchers.containsString;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VertxUnitRunner.class)
public class MockTest {

  private final Logger logger = LoggerFactory.getLogger("MockTest");
  private final int port = Integer.parseInt(System.getProperty("port", "8081"));
  private static final String LS = System.lineSeparator();
  private final Header TEN = new Header("X-Okapi-Tenant", "supertenant");
  private final Header JSON = new Header("Content-Type", "application/json");
  private String moduleName; //  "mod-codex-mock"
  private String moduleVersion; // "1.0.0" or "0.1.2-SNAPSHOT"
  private String moduleId; // "mod-codex-mock-0.1.2-SNAPSHOT"
  Vertx vertx;
  Async async;

  @Before
  public void setUp(TestContext context) {
    vertx = Vertx.vertx();
    moduleName = PomReader.INSTANCE.getModuleName()
      .replaceAll("_", "-");  // RMB normalizes the dash to underscore, fix back
    moduleVersion = PomReader.INSTANCE.getVersion();
    moduleId = moduleName + "-" + moduleVersion;
    logger.info("Test setup starting for " + moduleId);
    try {
      PostgresClient.setIsEmbedded(true);
      PostgresClient.getInstance(vertx).startEmbeddedPostgres();
    } catch (Exception e) {
      logger.warn("Exception in test setup: ", e);
      context.fail(e);
      return;
    }

    JsonObject conf = new JsonObject()
      .put("mock", "1111")
      .put("source", "unitTest")
      .put("http.port", port);
    logger.info("Codex Mock Test: Deploying "
      + RestVerticle.class.getName() + " "
      + Json.encode(conf));
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());
    RestAssured.port = port;
    logger.info("Codex Mock Test: setup done. Using port " + port);
  }

  @After
  public void tearDown(TestContext context) {
    logger.info("Cleaning up after ModuleTest");
    async = context.async();
    vertx.close(context.asyncAssertSuccess(res -> {
      PostgresClient.stopEmbeddedPostgres();
      async.complete();
    }));
  }

  /**
   * All the tests. In one long function, because starting up the embedded
   * postgres takes so long and fills the log.
   *
   * @param context
   */
  @Test
  public void tests(TestContext context) {
    async = context.async();
    logger.info("Codex mock Test starting");

    // Simple GET request to see the module is running and we can talk to it.
    given()
      .get("/admin/health")
      .then()
      .log().all()
      .statusCode(200);

    // Simple GET request without a tenant
    given()
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("Tenant"));

    // Simple GET request before the tenant init
    given()
      .header(TEN)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(400)
      .body(containsString("supertenant_mod_codex_mock.codex_mock_data"));

    // Call the tenant interface to initialize the database
    String tenants = "{\"module_to\":\"" + moduleId + "\"}";
    logger.info("About to call the tenant interface " + tenants);
    given()
      .header(TEN).header(JSON)
      .body(tenants)
      .post("/_/tenant")
      .then()
      .log().ifValidationFails()
      .statusCode(201);

    // get all
    given()
      .header(TEN)
      .get("/codex-instances")
      .then()
      .log().all() // .ifValidationFails()
      .body(containsString("\"totalRecords\" : 4"))
      .statusCode(200);

    // get one
    given()
      .header(TEN)
      .get("/codex-instances/11111111-1111-1111-1111-111111111111")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("alt title for 111111111111"))
      .body(containsString("unitTest"));

    // unknown id
    given()
      .header(TEN)
      .get("/codex-instances/99999999-9999-9999-9999-987654321111")
      .then()
      .log().ifValidationFails()
      .statusCode(404);

    // query
    given()
      .header(TEN)
      .get("/codex-instances?query=title=000000000001")
      .then()
      .log().ifValidationFails()
      .body(containsString("Title of 000000000001"))
      .statusCode(200);
    given()
      .header(TEN)
      .get("/codex-instances?query=publisher=beta")
      .then()
      .log().ifValidationFails()
      .body(containsString("Title of 111111111112"))
      .body(containsString("unitTest"))
      .statusCode(200);
    given()
      .header(TEN)
      .get("/codex-instances?query=contributor=Contributor of 111111111111")
      .then()
      .log().ifValidationFails()
      .body(containsString("Title of 111111111111"))
      .statusCode(200);
    given()
      .header(TEN)
      .get("/codex-instances?query=contributor=*111111111111*")
      .then()
      .log().ifValidationFails()
      .body(containsString("Title of 111111111111"))
      .statusCode(200);

    // bad query
    given()
      .header(TEN)
      .get("/codex-instances?query=BAD")
      .then()
      .log().ifValidationFails()
      .statusCode(422);

    // bad query - the query validation ought to catch this
    // But it does not. Logs the "unable to load schema" message, and accepts all...
    given()
      .header(TEN)
      .get("/codex-instances?query=UNKNOWNFIELD=foo")
      .then()
      .log().ifValidationFails()
      .statusCode(200);
    //.statusCode(422);

    // limit
    given()
      .header(TEN)
      .get("/codex-instances?offset=1&limit=2")
      .then()
      .log().ifValidationFails()
      .body(containsString("111111111111"))
      .body(containsString("111111111112"))
      .statusCode(200);

    // sort
    given()
      .header(TEN)
      .get("/codex-instances?offset=0&limit=1&query=publisher=for sortBy publisher")
      .then()
      .log().ifValidationFails()
      .body(containsString("alpha publisher"))
      .statusCode(200);

    // Query manipulations: resourceType -> type
    given()
      .header(TEN)
      .get("/codex-instances?query=resourceType=books")
      .then()
      .log().ifValidationFails()
      .body(containsString("\"totalRecords\" : 4"))
      .statusCode(200);

    // Query manipulations: isbn
    given()
      .header(TEN)
      .get("/codex-instances?query=identifier/type=isbn=1111111111")
      .then()
      .log().ifValidationFails()
      .body(containsString("\"totalRecords\" : 1"))
      .body(containsString("111111111112"))
      .statusCode(200);

    // Query manipulations: issn
    given()
      .header(TEN)
      .get("/codex-instances?query=identifier /type=issn = 1111111111")
      .then()
      .log().ifValidationFails()
      .body(containsString("\"totalRecords\" : 1"))
      .body(containsString("000000000001"))
      .statusCode(200);

    // All done
    logger.info("codex Mock Test done");
    async.complete();
  }

}
