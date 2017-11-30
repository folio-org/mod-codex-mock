package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.Response;
import org.apache.commons.io.IOUtils;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PostgresClient;
import org.folio.rest.persist.cql.CQLQueryValidationException;
import org.folio.rest.persist.cql.CQLWrapper;
import org.folio.rest.tools.messages.MessageConsts;
import org.folio.rest.tools.messages.Messages;
import org.folio.rest.tools.utils.TenantTool;
import org.folio.rest.tools.utils.ValidationHelper;
import org.z3950.zing.cql.cql2pgjson.CQL2PgJSON;
import org.z3950.zing.cql.cql2pgjson.FieldException;
import org.z3950.zing.cql.cql2pgjson.SchemaException;


public class CodexMockImpl implements CodexInstancesResource {
  private final Logger logger = LoggerFactory.getLogger("mod-notes");
  private String MOCK_SCHEMA = null;  // NOSONAR
  public static final String MOCK_TABLE = "codex_mock_data";
  private static final String IDFIELDNAME = "id";
  private static final String MOCK_SCHEMA_NAME = "apidocs/raml/codex.json";

  private final Messages messages = Messages.getInstance();

  private CQLWrapper getCQL(String query, int limit, int offset,
    String schema) throws IOException, FieldException, SchemaException {
    CQL2PgJSON cql2pgJson = null;
    if (schema != null) {
      cql2pgJson = new CQL2PgJSON(MOCK_TABLE + ".jsonb", schema);
    } else {
      cql2pgJson = new CQL2PgJSON(MOCK_TABLE + ".jsonb");
    }
    return new CQLWrapper(cql2pgJson, query)
      .setLimit(new Limit(limit))
      .setOffset(new Offset(offset));
  }
  private void initCQLValidation() {
    String path = MOCK_SCHEMA_NAME;
    try {
      MOCK_SCHEMA = IOUtils.toString(
        getClass().getClassLoader().getResourceAsStream(path), "UTF-8");
    } catch (Exception e) {
      logger.error("unable to load schema - " + path
        + ", validation of query fields will not be active");
    }
  }

  public CodexMockImpl(Vertx vertx, String tenantId) {
    if (MOCK_SCHEMA == null) {
      //initCQLValidation();
      // Commented out, because it can not find the json files for instance
      // Was commented out, because it fails a perfectly valid query
      // like metadata.createdDate=2017
      // See RMB-54
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    try {
      String mockN = System.getProperty("mock");
      logger.info("Getting mock instances " + offset + "+" + limit + " q=" + query + " m=" + mockN);
      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

      if (mockN != null) {
        String mq = "id=*" + mockN + "*";
        if (query == null) {
          query = mq;
        } else {
          query = "(" + query + ") AND (" + mq + ")";
        }
      }
      CQLWrapper cql = getCQL(query, limit, offset, MOCK_SCHEMA);

      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(MOCK_TABLE, Instance.class, new String[]{"*"}, cql,
          true /*get count too*/, false /* set id */,
          reply -> {
            if (reply.succeeded()) {
              InstanceCollection instColl = new InstanceCollection();
              @SuppressWarnings("unchecked")
              List<Instance> instList = (List<Instance>) reply.result().getResults();
              instColl.setInstances(instList);
              Integer totalRecords = reply.result().getResultInfo().getTotalRecords();
              instColl.setTotalRecords(totalRecords);
              asyncResultHandler.handle(succeededFuture(
                GetCodexInstancesResponse.withJsonOK(instColl)));
            } else {
              logger.error(reply.cause().getMessage(), reply.cause());
              asyncResultHandler.handle(succeededFuture(GetCodexInstancesResponse
                .withPlainBadRequest(reply.cause().getMessage())));
            }
          });
    } catch (CQLQueryValidationException e1) {
      int start = e1.getMessage().indexOf('\'');
      int end = e1.getMessage().lastIndexOf('\'');
      String field = e1.getMessage();
      if (start != -1 && end != -1) {
        field = field.substring(start + 1, end);
      }
      Errors e = ValidationHelper.createValidationErrorMessage(field,
        "", e1.getMessage());
      asyncResultHandler.handle(succeededFuture(GetCodexInstancesResponse
        .withJsonUnprocessableEntity(e)));
    } catch (Exception e) {
      logger.error(e.getMessage(), e);
      String message = messages.getMessage(lang, MessageConsts.InternalServerError);
      if (e.getCause() != null && e.getCause().getClass().getSimpleName()
        .endsWith("CQLParseException")) {
        message = " CQL parse error " + e.getLocalizedMessage();
      }
      asyncResultHandler.handle(succeededFuture(GetCodexInstancesResponse
        .withPlainInternalServerError(message)));
    }
  }

  @Override
  public void getCodexInstancesById(String id, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    String tenantId = TenantTool.calculateTenantId(
      okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
    String mockN = System.getProperty("mock");
    String query = "id=" + id;
    if (mockN != null) {
      String mq = "id=*" + mockN + "*";
      query = "(" + mq + ") AND (" + query + ")";
    }
    logger.info("Get one mock " + id + " q=" + query);
    CQLWrapper cql = getCQL(query, 1, 0, MOCK_SCHEMA);

    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(MOCK_TABLE, Instance.class, new String[]{"*"}, cql, true, true,
        reply -> {
          if (reply.succeeded()) {
            @SuppressWarnings("unchecked")
            List<Instance> instList = (List<Instance>) reply.result().getResults();
            if (instList.isEmpty()) {
              logger.info("Got an empty list");
              asyncResultHandler.handle(succeededFuture(
                GetCodexInstancesByIdResponse.withPlainNotFound(id)));
            } else {
              Instance inst = instList.get(0);
              logger.info("Got inst " + Json.encode(instList));
              asyncResultHandler.handle(succeededFuture(
                GetCodexInstancesByIdResponse.withJsonOK(inst)));
            }
          } else {
            String error = messages.getMessage(lang, MessageConsts.InternalServerError);
            logger.error(error, reply.cause());
            asyncResultHandler.handle(succeededFuture(
              GetCodexInstancesByIdResponse.withPlainInternalServerError(error)));
          }
        });
  }

}
