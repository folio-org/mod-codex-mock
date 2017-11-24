/*
 * Copyright (c) 2015-2017, Index Data
 * All rights reserved.
 * See the file LICENSE for details.
 */
package org.folio.rest.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import static io.vertx.core.Future.succeededFuture;
import io.vertx.core.Handler;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.ws.rs.core.Response;
import org.folio.rest.jaxrs.model.Contributor;
import org.folio.rest.jaxrs.model.Instance;
import org.folio.rest.jaxrs.model.InstanceCollection;
import org.folio.rest.jaxrs.resource.CodexInstancesResource;


public class CodexMockImpl implements CodexInstancesResource {

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

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang,
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
  public void getCodexInstancesById(String id, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    if (id.equals("11111111-1111-1111-1111-111111111111")
      || id.equals("11111111-1111-1111-1111-111111111112")) {
      Instance inst = makeInst(id);
      asyncResultHandler.handle(succeededFuture(
        GetCodexInstancesByIdResponse.withJsonOK(inst)));
      return;
    }
    asyncResultHandler.handle(succeededFuture(
      GetCodexInstancesByIdResponse.withPlainNotFound(id)));

  }

}
