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

package org.apache.polaris.extension.auth.opametadata.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import org.apache.polaris.extension.auth.opametadata.model.identity.AccessControlIdentity;
import org.apache.polaris.immutables.PolarisImmutable;

/**
 * Access control properties for Iceberg objects (namespaces, tables, views). Contains four roles:
 * owners, data_administrators, data_writers, and data_readers. Each role is a list of
 * AccessControlIdentity objects (users or groups).
 */
@PolarisImmutable
@JsonSerialize(as = ImmutableAccessControlProperties.class)
@JsonDeserialize(as = ImmutableAccessControlProperties.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public interface AccessControlProperties {

  /**
   * Owners of the resource. Have full control over the object and all child objects (Read, Write,
   * Admin, Drop).
   */
  List<AccessControlIdentity> owners();

  /**
   * Data administrators of the resource. Can alter metadata, manage schemas, modify properties,
   * drop objects.
   */
  List<AccessControlIdentity> dataAdministrators();

  /** Data writers of the resource. Can insert, update, delete, and merge operations on tables. */
  List<AccessControlIdentity> dataWriters();

  /** Data readers of the resource. Can select, scan, and read operations on tables and views. */
  List<AccessControlIdentity> dataReaders();
}
