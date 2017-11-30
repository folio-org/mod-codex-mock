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
import org.folio.rest.tools.client.test.HttpClientMock2;
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
  private final Header ALLPERM = new Header("X-Okapi-Permissions", "notes.domain.all");
  private final Header USER9 = new Header("X-Okapi-User-Id",
    "99999999-9999-9999-9999-999999999999");
  private final Header USER19 = new Header("X-Okapi-User-Id",
    "11999999-9999-9999-9999-999999999911");  // One that is not found in the mock data
  private final Header USER8 = new Header("X-Okapi-User-Id",
    "88888888-8888-8888-8888-888888888888");
  private final Header USER7 = new Header("X-Okapi-User-Id",
    "77777777-7777-7777-7777-777777777777");
  private final Header JSON = new Header("Content-Type", "application/json");
  private String moduleName; //  "mod-notes"
  private String moduleVersion; // "1.0.0" or "0.1.2-SNAPSHOT"
  private String moduleId; // "mod-notes-1.0.1-SNAPSHOT"
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
      e.printStackTrace();
      context.fail(e);
      return;
    }

    JsonObject conf = new JsonObject()
      .put("http.port", port)
      .put(HttpClientMock2.MOCK_MODE, "true");
    logger.info("notesTest: Deploying "
      + RestVerticle.class.getName() + " "
      + Json.encode(conf));
    DeploymentOptions opt = new DeploymentOptions()
      .setConfig(conf);
    vertx.deployVerticle(RestVerticle.class.getName(),
      opt, context.asyncAssertSuccess());
    RestAssured.port = port;
    logger.info("notesTest: setup done. Using port " + port);
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
    logger.info("notesTest starting");

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

    // Empty list of notes
    given()
      .header(TEN).header(ALLPERM)
      .get("/codex-instances")
      .then()
      .log().ifValidationFails()
      .statusCode(200)
      .body(containsString("\"totalRecords\" : 8"));

    // All done
    logger.info("notesTest done");
    async.complete();
  }

}
