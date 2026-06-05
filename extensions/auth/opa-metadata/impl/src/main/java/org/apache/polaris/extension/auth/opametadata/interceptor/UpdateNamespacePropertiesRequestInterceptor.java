/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.polaris.extension.auth.opametadata.interceptor;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.iceberg.rest.requests.UpdateNamespacePropertiesRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Captures inbound namespace property changes from UpdateNamespacePropertiesRequest for OPA
 * authorization use.
 */
@Provider
@ApplicationScoped
class UpdateNamespacePropertiesRequestInterceptor implements ReaderInterceptor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UpdateNamespacePropertiesRequestInterceptor.class);

  private final Instance<PendingNamespacePropertiesHolder> pendingNamespacePropertiesHolder;

  @Inject
  UpdateNamespacePropertiesRequestInterceptor(
      Instance<PendingNamespacePropertiesHolder> pendingNamespacePropertiesHolder) {
    this.pendingNamespacePropertiesHolder = pendingNamespacePropertiesHolder;
  }

  @Override
  public Object aroundReadFrom(ReaderInterceptorContext context)
      throws IOException, WebApplicationException {
    Object requestBody = context.proceed();
    if (!(requestBody instanceof UpdateNamespacePropertiesRequest request)) {
      return requestBody;
    }
    if (!pendingNamespacePropertiesHolder.isResolvable()) {
      return requestBody;
    }

    Map<String, String> updates = request.updates() == null ? Map.of() : request.updates();
    Set<String> removals =
        request.removals() == null ? Set.of() : Set.copyOf(new HashSet<>(request.removals()));

    LOGGER.debug(
        "Captured namespace property changes for OPA authorization: updates={} removals={}",
        updates.size(),
        removals.size());
    pendingNamespacePropertiesHolder.get().setInboundPropertyChanges(updates, removals);
    return requestBody;
  }
}
