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

import io.quarkus.security.identity.CurrentIdentityAssociation;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.apache.polaris.core.auth.PolarisPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class UserOwnershipResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(UserOwnershipResolver.class);

  private static final String PRINCIPAL_ID_KEY = "principal_id";
  private static final String PRINCIPAL_ID_ALT_KEY = "principal-id";
  private static final String ID_KEY = "id";

  @Inject CurrentIdentityAssociation currentIdentityAssociation;

  public Optional<OwnerTrackingMetadata> resolve() {
    try {
      SecurityIdentity identity =
          currentIdentityAssociation
              .getDeferredIdentity()
              .subscribeAsCompletionStage()
              .getNow(null);

      if (identity == null || identity.getPrincipal() == null) {
        LOGGER.debug("Owner tracking skipped: no authenticated identity");
        return Optional.empty();
      }

      Principal principal = identity.getPrincipal();
      String owner = principal.getName();
      String ownerId = owner;

      if (principal instanceof PolarisPrincipal polarisPrincipal) {
        Map<String, String> principalProps = polarisPrincipal.getProperties();
        ownerId =
            firstNonBlank(
                principalProps.get(PRINCIPAL_ID_KEY),
                principalProps.get(PRINCIPAL_ID_ALT_KEY),
                principalProps.get(ID_KEY),
                owner);
      }

      OwnerTrackingMetadata metadata =
          new OwnerTrackingMetadata(owner, ownerId, Instant.now().toString());
      LOGGER.debug("Resolved owner metadata for principal={}", owner);
      return Optional.of(metadata);
    } catch (Exception e) {
      LOGGER.debug("Owner tracking skipped: could not resolve principal metadata", e);
      return Optional.empty();
    }
  }

  private static String firstNonBlank(String first, String second, String third, String fallback) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    if (third != null && !third.isBlank()) {
      return third;
    }
    return fallback;
  }
}
