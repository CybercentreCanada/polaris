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
import jakarta.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.iceberg.Table;
import org.apache.iceberg.catalog.Catalog;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.polaris.core.auth.PolarisPrincipal;
import org.apache.polaris.core.catalog.LocalCatalogFactory;
import org.apache.polaris.core.entity.PolarisEntityType;
import org.apache.polaris.core.persistence.resolver.PolarisResolutionManifest;
import org.apache.polaris.core.persistence.resolver.ResolutionManifestFactory;
import org.apache.polaris.core.persistence.resolver.ResolvedPathKey;
import org.apache.polaris.core.persistence.resolver.ResolverPath;
import org.apache.polaris.core.persistence.resolver.ResolverStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
class CatalogTableMetadataPropertiesLookup implements TableMetadataPropertiesLookup {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(CatalogTableMetadataPropertiesLookup.class);

  private final ResolutionManifestFactory resolutionManifestFactory;
  private final LocalCatalogFactory localCatalogFactory;

  @Inject
  CatalogTableMetadataPropertiesLookup(
      ResolutionManifestFactory resolutionManifestFactory,
      LocalCatalogFactory localCatalogFactory) {
    this.resolutionManifestFactory = resolutionManifestFactory;
    this.localCatalogFactory = localCatalogFactory;
  }

  @Override
  public Optional<Map<String, String>> lookupTableProperties(
      PolarisPrincipal principal, String catalogName, List<String> tablePathSegments) {
    if (tablePathSegments == null || tablePathSegments.isEmpty()) {
      return Optional.empty();
    }

    TableIdentifier tableIdentifier = tableIdentifierFromSegments(tablePathSegments);
    if (tableIdentifier == null) {
      return Optional.empty();
    }

    try {
      PolarisResolutionManifest manifest =
          resolutionManifestFactory.createResolutionManifest(principal, catalogName);
      manifest.addPassthroughPath(
          new ResolverPath(
              ResolvedPathKey.of(tablePathSegments, PolarisEntityType.TABLE_LIKE), true));
      ResolverStatus status = manifest.resolveAll();
      if (status.getStatus() != ResolverStatus.StatusEnum.SUCCESS) {
        return Optional.empty();
      }

      Catalog catalog = localCatalogFactory.createCatalog(manifest);
      Table table = catalog.loadTable(tableIdentifier);
      return Optional.of(Map.copyOf(table.properties()));
    } catch (RuntimeException e) {
      LOGGER.debug(
          "Failed to resolve table properties for catalog={} table={} using catalog load",
          catalogName,
          tableIdentifier,
          e);
      return Optional.empty();
    }
  }

  private TableIdentifier tableIdentifierFromSegments(List<String> tablePathSegments) {
    if (tablePathSegments.size() < 1) {
      return null;
    }

    String tableName = tablePathSegments.get(tablePathSegments.size() - 1);
    String[] namespaceLevels =
        tablePathSegments.subList(0, tablePathSegments.size() - 1).toArray(String[]::new);
    return TableIdentifier.of(Namespace.of(namespaceLevels), tableName);
  }
}
