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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
import org.z3950.zing.cql.cql2pgjson.ServerChoiceIndexesException;


public class CodexMockImpl implements CodexInstancesResource {
  private final Logger logger = LoggerFactory.getLogger("mod-codex-mock");
  private String MOCK_SCHEMA = null;  // NOSONAR
  public static final String MOCK_TABLE = "codex_mock_data";
  private static final String IDFIELDNAME = "id";
  private static final String MOCK_SCHEMA_NAME = "apidocs/raml/codex.json";
  private final Messages messages = Messages.getInstance();

  private CQLWrapper getCQL(String query, int limit, int offset,
    String schema) throws IOException, FieldException, SchemaException, ServerChoiceIndexesException {
    CQL2PgJSON cql2pgJson = null;
    List serverChoice =  Arrays.asList("title","contributor");
    if (schema != null) {
      cql2pgJson = new CQL2PgJSON(MOCK_TABLE + ".jsonb", schema, serverChoice);
    } else {
      cql2pgJson = new CQL2PgJSON(MOCK_TABLE + ".jsonb", serverChoice);
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
        + ", validation of query fields will not be active", e);
    }
  }

  public CodexMockImpl(Vertx vertx, String tenantId) {
    if (MOCK_SCHEMA == null) {
      //initCQLValidation();
      logger.warn("InitCqlValidation is COMMENTED OUT, because it does not work!");
      // This seems not to work, logs the "unable to load schema" message above
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }


  /*
  Get the mock number from a -D command line option, or context config, whihc
  is where the unit test can put it. Returns "" if none.
   *
   */
  private String mockN(Context context) {
    String def = context.config().getString("mock", "");
    return System.getProperty("mock", def);
  }

  /*
  Get the source override value from the -D command line option, or context.
  If specified, this will be put in all returned records.
   */
  private String overrideSrc(Context context) {
    String def = context.config().getString("source", "");
    return System.getProperty("source", def);
  }
  /*
  Query rewriting:
    - resourceType -> type
    - identifier/type=isbn ->  (identifier=isbn and identifier=XXXX)
   */
  private String mockQuery(String query, Context context) {
    if (query != null) {
      query = query.replaceAll("resourceType", "type");
      query = query.replaceAll("identifier */type=isbn *= *([0-9a-zA-Z-]+)",
        "(identifier=isbn AND identifier=\"$1\"*)");
      query = query.replaceAll("identifier */type=issn *= *([0-9a-zA-Z-]+)",
        "(identifier=issn AND identifier=\"$1\"*)");
      // For some reason the asterisk is required, or it will not match
      // numbers that do not start with a leading zero !!?!
    }
    String m = mockN(context);
    if (m.isEmpty()) {
      return query;
    }
    String mq = "id=*" + m + "*";
    if (query == null) {
      return mq;
    }
    Pattern pat = Pattern.compile("^(.+?)( sortBy .*)?$");
    Matcher mat = pat.matcher(query);
    String qry = query;
    String sort = "";
    if (mat.find()) {
      qry = mat.group(1);
      sort = mat.group(2);
      if (sort == null) {
        sort = "";
      }
    }
    return "(" + qry + ") AND (" + mq + ")" + sort;
  }

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    try {
      logger.info("Getting mock instances " + offset + "+" + limit
        + " q=" + query + " m=" + mockN(vertxContext));
      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));
      query = mockQuery(query, vertxContext);
      logger.info("  Mocked query: " + query);
      CQLWrapper cql = getCQL(query, limit, offset, MOCK_SCHEMA);

      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(MOCK_TABLE, Instance.class, new String[]{"*"}, cql,
          true /*get count too*/, false /* set id */,
          reply -> {
            if (reply.succeeded()) {
              InstanceCollection instColl = new InstanceCollection();
              @SuppressWarnings("unchecked")
              List<Instance> instList = (List<Instance>) reply.result().getResults();
              for (Instance i : instList) {
                if (i == null) {
                  asyncResultHandler.handle(succeededFuture(
                    GetCodexInstancesResponse.withPlainInternalServerError(
                      "Got a null record from the database")));
                  return;
                }
                String mockN = mockN(vertxContext);
                String src = overrideSrc(vertxContext);
                if (!src.isEmpty()) {
                  i.setSource(src);
                } else if (i.getSource() == null || i.getSource().isEmpty() || !mockN.isEmpty()) {
                  i.setSource("Mock" + mockN(vertxContext));
                }
              }
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
      logger.error("Pg Exception: " + e.getMessage(), e);
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
    String query = mockQuery("id=" + id, vertxContext);
    logger.info("Get one mock " + id + " q=" + query);
    CQLWrapper cql = getCQL(query, 1, 0, MOCK_SCHEMA);
    try {
      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(MOCK_TABLE, Instance.class, new String[]{"*"}, cql, true, true,
          reply -> {
            if (reply.succeeded()) {
              @SuppressWarnings("unchecked")
              List<Instance> instList = (List<Instance>) reply.result().getResults();
              if (instList.isEmpty()) {
                logger.info("Got an empty list");
                asyncResultHandler.handle(succeededFuture(
                  GetCodexInstancesByIdResponse.withPlainNotFound(
                          "Instance " + id + " not found")));
              } else {
                Instance inst = instList.get(0);
                logger.info("Got inst " + Json.encode(instList));
                if (inst == null) {
                  asyncResultHandler.handle(succeededFuture(
                    GetCodexInstancesByIdResponse.withPlainInternalServerError(
                      "Got a null record from the database")));
                } else {
                  String src = overrideSrc(vertxContext);
                  if (!src.isEmpty()) {
                    inst.setSource(src);
                  } else if (inst.getSource() == null || inst.getSource().isEmpty()) {
                    inst.setSource("Mock" + mockN(vertxContext));
                  }
                  asyncResultHandler.handle(succeededFuture(
                    GetCodexInstancesByIdResponse.withJsonOK(inst)));
                }
              }
            } else {
              String error = messages.getMessage(lang, MessageConsts.InternalServerError);
              logger.error(error, reply.cause());
              asyncResultHandler.handle(succeededFuture(
                GetCodexInstancesByIdResponse.withPlainInternalServerError(error)));
            }
          });
    } catch (Exception e) {
      logger.error("PG exception: " + e.getMessage(), e);
      String message = messages.getMessage(lang, MessageConsts.InternalServerError);
      if (e.getCause() != null && e.getCause().getClass().getSimpleName()
        .endsWith("CQLParseException")) {
        message = " CQL parse error " + e.getLocalizedMessage();
      }
      asyncResultHandler.handle(succeededFuture(GetCodexInstancesResponse
        .withPlainInternalServerError(message)));
    }
  }

}
