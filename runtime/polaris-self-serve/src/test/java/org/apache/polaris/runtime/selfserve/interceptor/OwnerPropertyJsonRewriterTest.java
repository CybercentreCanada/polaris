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

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class OwnerPropertyJsonRewriterTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void injectOwnerPropertiesCreatesPropertiesNodeWhenMissing() {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("name", "tbl");

    OwnerTrackingMetadata metadata =
        new OwnerTrackingMetadata("alice", "alice-id", "2026-06-02T12:00:00Z");

    boolean updated = OwnerPropertyJsonRewriter.injectOwnerProperties(root, metadata);

    assertThat(updated).isTrue();
    assertThat(root.path("properties").path(OwnerTrackingKeys.OWNER).asText()).isEqualTo("alice");
    assertThat(root.path("properties").path(OwnerTrackingKeys.OWNER_ID).asText())
        .isEqualTo("alice-id");
    assertThat(root.path("properties").path(OwnerTrackingKeys.OWNER_CREATED_AT).asText())
        .isEqualTo("2026-06-02T12:00:00Z");
  }

  @Test
  void injectOwnerPropertiesPreservesExistingNonOwnerKeys() {
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode props = root.putObject("properties");
    props.put("comment", "hello");
    props.put("x", "1");

    OwnerTrackingMetadata metadata =
        new OwnerTrackingMetadata("alice", "alice-id", "2026-06-02T12:00:00Z");

    OwnerPropertyJsonRewriter.injectOwnerProperties(root, metadata);

    assertThat(root.path("properties").path("comment").asText()).isEqualTo("hello");
    assertThat(root.path("properties").path("x").asText()).isEqualTo("1");
    assertThat(root.path("properties").path(OwnerTrackingKeys.OWNER).asText()).isEqualTo("alice");
  }

  @Test
  void injectOwnerPropertiesOverwritesOwnerKeys() {
    ObjectNode root = MAPPER.createObjectNode();
    ObjectNode props = root.putObject("properties");
    props.put(OwnerTrackingKeys.OWNER, "old");
    props.put(OwnerTrackingKeys.OWNER_ID, "old-id");
    props.put(OwnerTrackingKeys.OWNER_CREATED_AT, "old-ts");

    OwnerTrackingMetadata metadata =
        new OwnerTrackingMetadata("new-owner", "new-id", "2026-06-02T12:00:00Z");

    OwnerPropertyJsonRewriter.injectOwnerProperties(root, metadata);

    assertThat(root.path("properties").path(OwnerTrackingKeys.OWNER).asText())
        .isEqualTo("new-owner");
    assertThat(root.path("properties").path(OwnerTrackingKeys.OWNER_ID).asText())
        .isEqualTo("new-id");
    assertThat(root.path("properties").path(OwnerTrackingKeys.OWNER_CREATED_AT).asText())
        .isEqualTo("2026-06-02T12:00:00Z");
  }

  @Test
  void injectOwnerPropertiesReplacesInvalidPropertiesNode() {
    ObjectNode root = MAPPER.createObjectNode();
    root.put("properties", "unexpected-string");

    OwnerTrackingMetadata metadata =
        new OwnerTrackingMetadata("alice", "alice-id", "2026-06-02T12:00:00Z");

    OwnerPropertyJsonRewriter.injectOwnerProperties(root, metadata);

    assertThat(root.path("properties").isObject()).isTrue();
    assertThat(root.path("properties").path(OwnerTrackingKeys.OWNER).asText()).isEqualTo("alice");
  }
}
