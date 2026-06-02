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

package org.apache.polaris.runtime.selfserve.interceptor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.ext.ReaderInterceptor;
import jakarta.ws.rs.ext.ReaderInterceptorContext;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@Provider
@jakarta.annotation.Priority(Priorities.ENTITY_CODER)
public class CreateRequestOwnerReaderInterceptor implements ReaderInterceptor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CreateRequestOwnerReaderInterceptor.class);

  private static final Pattern CREATE_NAMESPACE_PATH =
      Pattern.compile("^/?api/catalog/v1/[^/]+/namespaces$");
  private static final Pattern CREATE_TABLE_PATH =
      Pattern.compile("^/?api/catalog/v1/[^/]+/namespaces/[^/]+/tables$");
  private static final Pattern CREATE_VIEW_PATH =
      Pattern.compile("^/?api/catalog/v1/[^/]+/namespaces/[^/]+/views$");

  @Inject ObjectMapper objectMapper;
  @Inject UserOwnershipResolver userOwnershipResolver;

  @Context UriInfo uriInfo;
  @Context HttpServletRequest servletRequest;

  @Override
  public Object aroundReadFrom(ReaderInterceptorContext context)
      throws IOException, WebApplicationException {
    String path = resolvePath();
    String method = resolveMethod();

    if (!isTargetCreateRequest(method, path)) {
      LOGGER.debug("Owner tracking skipped: non-target request method={} path={}", method, path);
      return context.proceed();
    }

    if (!isJsonMediaType(context.getMediaType())) {
      LOGGER.debug("Owner tracking skipped: non-JSON media type path={}", path);
      return context.proceed();
    }

    Optional<OwnerTrackingMetadata> maybeMetadata = userOwnershipResolver.resolve();
    if (maybeMetadata.isEmpty()) {
      LOGGER.debug(
          "Owner tracking skipped: no owner metadata path={} requestId={}", path, requestId());
      return context.proceed();
    }

    byte[] originalBytes = context.getInputStream().readAllBytes();
    if (originalBytes.length == 0) {
      LOGGER.debug("Owner tracking skipped: empty body path={} requestId={}", path, requestId());
      context.setInputStream(new ByteArrayInputStream(originalBytes));
      return context.proceed();
    }

    try {
      JsonNode root = objectMapper.readTree(originalBytes);
      if (!(root instanceof ObjectNode rootObjectNode)) {
        LOGGER.debug("Owner tracking skipped: request body not a JSON object path={}", path);
        context.setInputStream(new ByteArrayInputStream(originalBytes));
        return context.proceed();
      }

      OwnerPropertyJsonRewriter.injectOwnerProperties(rootObjectNode, maybeMetadata.get());
      byte[] rewritten = objectMapper.writeValueAsBytes(rootObjectNode);
      context.setInputStream(new ByteArrayInputStream(rewritten));

      LOGGER.debug(
          "Owner tracking applied path={} requestId={} owner={} ownerId={} bytes={}",
          path,
          requestId(),
          maybeMetadata.get().owner(),
          maybeMetadata.get().ownerId(),
          rewritten.length);
    } catch (Exception e) {
      context.setInputStream(new ByteArrayInputStream(originalBytes));
      LOGGER.warn(
          "Owner tracking rewrite failed; proceeding with original payload path={} requestId={}",
          path,
          requestId(),
          e);
    }

    return context.proceed();
  }

  private String resolvePath() {
    if (uriInfo != null && uriInfo.getPath() != null) {
      return uriInfo.getPath();
    }
    if (servletRequest != null && servletRequest.getRequestURI() != null) {
      String uri = servletRequest.getRequestURI();
      return uri.startsWith("/") ? uri.substring(1) : uri;
    }
    return "";
  }

  private String resolveMethod() {
    if (servletRequest != null && servletRequest.getMethod() != null) {
      return servletRequest.getMethod();
    }
    return "";
  }

  static boolean isTargetCreateRequest(String method, String path) {
    if (!"POST".equalsIgnoreCase(method)) {
      return false;
    }
    return CREATE_NAMESPACE_PATH.matcher(path).matches()
        || CREATE_TABLE_PATH.matcher(path).matches()
        || CREATE_VIEW_PATH.matcher(path).matches();
  }

  private static boolean isJsonMediaType(MediaType mediaType) {
    if (mediaType == null) {
      return false;
    }
    String subtype = mediaType.getSubtype();
    return "json".equalsIgnoreCase(subtype)
        || (subtype != null && subtype.toLowerCase(Locale.ROOT).endsWith("+json"));
  }

  private static String requestId() {
    String requestId = MDC.get(OwnerTrackingKeys.REQUEST_ID_MDC_KEY);
    return requestId != null ? requestId : "n/a";
  }
}
