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
package org.apache.polaris.extension.auth.opametadata;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.apache.iceberg.MetadataUpdate;
import org.apache.iceberg.rest.requests.UpdateTableRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures inbound table properties from UpdateTableRequest for OPA authorization use. */
@Provider
@ApplicationScoped
class UpdateTableRequestInterceptor implements ReaderInterceptor {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpdateTableRequestInterceptor.class);

  private final Instance<PendingTablePropertiesHolder> pendingTablePropertiesHolder;

  @Inject
  UpdateTableRequestInterceptor(
      Instance<PendingTablePropertiesHolder> pendingTablePropertiesHolder) {
    this.pendingTablePropertiesHolder = pendingTablePropertiesHolder;
  }

  @Override
  public Object aroundReadFrom(ReaderInterceptorContext context)
      throws IOException, WebApplicationException {
    Object requestBody = context.proceed();
    LOGGER.info("Intercepting request to capture inbound table properties for OPA authorization of type {}", requestBody.getClass().getSimpleName());
    
    if (!(requestBody instanceof UpdateTableRequest updateTableRequest)) {
      return requestBody;
    }
    if (!pendingTablePropertiesHolder.isResolvable()) {
      return requestBody;
    }
    LOGGER.info("Captured UpdateTableRequest for OPA authorization, extracting inbound table properties if present");
    pendingTablePropertiesHolder
        .get()
        .setInboundSetProperties(extractInboundSetProperties(updateTableRequest));
    return requestBody;
  }

  private static Map<String, String> extractInboundSetProperties(UpdateTableRequest request) {
    if (request.updates() == null || request.updates().isEmpty()) {
        LOGGER.info("No metadata updates found in UpdateTableRequest");
        return Map.of();
    }

    Map<String, String> mergedUpdates = new LinkedHashMap<>();
    for (MetadataUpdate update : request.updates()) {
      if (update instanceof MetadataUpdate.SetProperties setProperties) {
        mergedUpdates.putAll(setProperties.updated());
      }
    }
    if (mergedUpdates.isEmpty()) {
        LOGGER.info("No set properties found in UpdateTableRequest");
        return Map.of();
    }
    LOGGER.info("Extracted inbound table properties from UpdateTableRequest: {}", mergedUpdates);
    return Map.copyOf(mergedUpdates);
  }
}
