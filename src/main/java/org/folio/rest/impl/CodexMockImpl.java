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
import org.folio.rest.jaxrs.resource.CodexInstancesResource;


public class CodexMockImpl implements CodexInstancesResource {

  @Override
  public void getCodexInstances(String query, int offset, int limit, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
  }

  private Instance makeInst(String id) {
    Instance inst = new Instance();
    inst.setId(id);
    inst.setTitle("Title of " + id);
    Contributor cont = new Contributor();
    cont.setName("Contributor of " + id);
    Set<Contributor> contset = new LinkedHashSet<>();
    contset.add(cont);
    inst.setContributor(contset);
    return inst;
  }

  @Override
  public void getCodexInstancesById(String id, String lang,
    Map<String, String> okapiHeaders,
    Handler<AsyncResult<Response>> asyncResultHandler, Context vertxContext) throws Exception {

    Instance inst = makeInst(id);
    asyncResultHandler.handle(succeededFuture(
      GetCodexInstancesByIdResponse.withJsonOK(inst)));
  }

}
