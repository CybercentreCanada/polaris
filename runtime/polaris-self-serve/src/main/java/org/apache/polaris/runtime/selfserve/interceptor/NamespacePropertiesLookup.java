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

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.apache.polaris.core.auth.PolarisPrincipal;
import org.apache.polaris.core.entity.NamespaceEntity;
import org.apache.polaris.core.entity.PolarisEntityType;
import org.apache.polaris.core.persistence.ResolvedPolarisEntity;
import org.apache.polaris.core.persistence.resolver.ResolvedPathKey;
import org.apache.polaris.core.persistence.resolver.Resolver;
import org.apache.polaris.core.persistence.resolver.ResolverFactory;
import org.apache.polaris.core.persistence.resolver.ResolverPath;
import org.apache.polaris.core.persistence.resolver.ResolverStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class NamespacePropertiesLookup {

  private static final Logger LOGGER = LoggerFactory.getLogger(NamespacePropertiesLookup.class);
  private static final String NAMESPACE_SEPARATOR = "\u001f";
  private static final String NAMESPACE_SEPARATOR_ENCODED = "%1F";

  @Inject ResolverFactory resolverFactory;
  @Inject UserOwnershipResolver userOwnershipResolver;

  public Optional<Map<String, String>> lookup(String catalogName, String encodedNamespacePath) {
    Optional<PolarisPrincipal> maybePrincipal = userOwnershipResolver.resolvePolarisPrincipal();
    if (maybePrincipal.isEmpty()) {
      LOGGER.info("Namespace lookup skipped: no PolarisPrincipal available");
      return Optional.empty();
    }

    String normalizedPath = normalizeNamespacePath(encodedNamespacePath);
    List<String> namespaceLevels = toNamespaceLevels(normalizedPath);

    Resolver resolver = resolverFactory.createResolver(maybePrincipal.get(), catalogName);
    resolver.addPath(
        new ResolverPath(ResolvedPathKey.of(namespaceLevels, PolarisEntityType.NAMESPACE), true));
    ResolverStatus status = resolver.resolveAll();

    if (status.getStatus() != ResolverStatus.StatusEnum.SUCCESS) {
      LOGGER.info(
          "Namespace lookup unresolved: catalog={} namespace={} status={}",
          catalogName,
          normalizedPath,
          status.getStatus());
      return Optional.empty();
    }

    ResolvedPolarisEntity leaf = resolver.getResolvedPath().getLast();
    if (leaf.getEntity().getType() != PolarisEntityType.NAMESPACE) {
      LOGGER.info(
          "Namespace lookup returned non-namespace leaf type={} for catalog={} namespace={}",
          leaf.getEntity().getType(),
          catalogName,
          normalizedPath);
      return Optional.empty();
    }

    NamespaceEntity namespaceEntity = NamespaceEntity.of(leaf.getEntity());
    if (namespaceEntity == null) {
      return Optional.empty();
    }

    return Optional.of(namespaceEntity.getPropertiesAsMap());
  }

  public Optional<Map<String, String>> lookupTableProperties(
      String catalogName, String encodedNamespacePath, String tableName) {
    Optional<PolarisPrincipal> maybePrincipal = userOwnershipResolver.resolvePolarisPrincipal();
    if (maybePrincipal.isEmpty()) {
      LOGGER.info("Table lookup skipped: no PolarisPrincipal available");
      return Optional.empty();
    }

    String normalizedPath = normalizeNamespacePath(encodedNamespacePath);
    List<String> namespaceLevels = toNamespaceLevels(normalizedPath);

    List<String> tablePath = new ArrayList<>(namespaceLevels);
    tablePath.add(tableName);
    Resolver resolver = resolverFactory.createResolver(maybePrincipal.get(), catalogName);
    resolver.addPath(
        new ResolverPath(ResolvedPathKey.of(tablePath, PolarisEntityType.TABLE_LIKE), true));
    ResolverStatus status = resolver.resolveAll();

    if (status.getStatus() != ResolverStatus.StatusEnum.SUCCESS) {
      LOGGER.info(
          "Table lookup unresolved: catalog={} namespace={} table={} status={}",
          catalogName,
          normalizedPath,
          tableName,
          status.getStatus());
      return Optional.empty();
    }

    ResolvedPolarisEntity leaf = resolver.getResolvedPath().getLast();
    if (leaf.getEntity().getType() != PolarisEntityType.TABLE_LIKE) {
      LOGGER.info(
          "Table lookup returned non-table leaf type={} for catalog={} namespace={} table={}",
          leaf.getEntity().getType(),
          catalogName,
          normalizedPath,
          tableName);
      return Optional.empty();
    }

    return Optional.of(leaf.getEntity().getPropertiesAsMap());
  }

  private static String normalizeNamespacePath(String encodedNamespacePath) {
    return encodedNamespacePath
        .replace(NAMESPACE_SEPARATOR_ENCODED, NAMESPACE_SEPARATOR)
        .replace(NAMESPACE_SEPARATOR_ENCODED.toLowerCase(Locale.ROOT), NAMESPACE_SEPARATOR);
  }

  private static List<String> toNamespaceLevels(String normalizedPath) {
    List<String> levels =
        new ArrayList<>(Arrays.asList(normalizedPath.split(NAMESPACE_SEPARATOR, -1)));
    levels.removeIf(String::isEmpty);
    return levels;
  }
}
