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

package org.apache.polaris.extension.auth.opametadata.model.identity;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.apache.polaris.immutables.PolarisImmutable;

/**
 * Base interface for access control identities (users and groups) with version support. Uses JSON
 * type info with the "version" property to support multiple identity formats over time.
 */
@PolarisImmutable
@JsonSerialize(as = ImmutableAccessControlIdentity.class)
@JsonDeserialize(as = ImmutableAccessControlIdentity.class)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public interface AccessControlIdentity {

  /** The version of this identity format. */
  String version();

  /** The immutable identifier for this identity (empty for users, UUID for groups). */
  String objectId();

  /** Human-readable display name for this identity. */
  String displayName();

  /** The identity value (email for users, name for groups). */
  String value();

  /** The type of identity: "user" or "group". */
  String type();

  /** The identity provider (e.g., "keycloak", "entra"). */
  String oidProvider();
}
