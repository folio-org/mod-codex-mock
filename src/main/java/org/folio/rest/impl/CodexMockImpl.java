package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.folio.rest.RestVerticle;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.Errors;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;
import org.folio.rest.persist.Criteria.Criteria;
import org.folio.rest.persist.Criteria.Criterion;
import org.folio.rest.persist.Criteria.Limit;
import org.folio.rest.persist.Criteria.Offset;
import org.folio.rest.persist.PgExceptionUtil;
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

  public CodexMockImpl(Vertx vertx, String tenantId) {
    if (MOCK_SCHEMA == null) {
      //initCQLValidation();  // NOSONAR
      // Commented out, because it fails a perfectly valid query
      // like metadata.createdDate=2017
      // See RMB-54
    }
    PostgresClient.getInstance(vertx, tenantId).setIdField(IDFIELDNAME);
  }

  private Instance makeInst(String id) {
    String key = id.replaceFirst(".*-", "");
    Instance inst = new Instance();
    inst.setId(id);
    inst.setTitle("Title of " + key);
    inst.setAltTitle("alt title for " + key);
    inst.setPublisher("Publisher for " + key);
    inst.setType("Type for " + key);
    Contributor cont = new Contributor();
    cont.setName("Contributor of " + key);
    cont.setType("some type");
    Set<Contributor> contset = new LinkedHashSet<>();
    contset.add(cont);
    inst.setContributor(contset);
    return inst;
  }

  public void OLDgetCodexInstances(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    List<Instance> lst = new LinkedList<>();
    lst.add(makeInst("11111111-1111-1111-1111-111111111111"));
    lst.add(makeInst("11111111-1111-1111-1111-111111111112"));
    InstanceCollection coll = new InstanceCollection();
    coll.setInstances(lst);
    coll.setTotalRecords(2);
    asyncResultHandler.handle(succeededFuture(GetCodexInstancesResponse.withJsonOK(coll)));
  }

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {
    try {
      logger.info("Getting mock instances " + offset + "+" + limit + " q=" + query);

      String tenantId = TenantTool.calculateTenantId(
        okapiHeaders.get(RestVerticle.OKAPI_HEADER_TENANT));

      CQLWrapper cql = getCQL(query, limit, offset, MOCK_SCHEMA);

      PostgresClient.getInstance(vertxContext.owner(), tenantId)
        .get(MOCK_TABLE, InstanceCollection.class, new String[]{"*"}, cql,
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
    Criterion c = new Criterion(
      new Criteria().addField(IDFIELDNAME).setJSONB(false)
        .setOperation("=").setValue("'" + id + "'"));
    PostgresClient.getInstance(vertxContext.owner(), tenantId)
      .get(MOCK_TABLE, Instance.class, c, true,
        reply -> {
          if (reply.succeeded()) {
            @SuppressWarnings("unchecked")
            List<Instance> instList = (List<Instance>) reply.result().getResults();
            if (instList.isEmpty()) {
              asyncResultHandler.handle(succeededFuture(
                GetCodexInstancesByIdResponse.withPlainNotFound(tenantId)));
            } else {
              Instance inst = instList.get(0);
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
