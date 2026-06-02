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
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class OwnerPropertyJsonRewriter {

  private OwnerPropertyJsonRewriter() {}

  public static boolean injectOwnerProperties(ObjectNode root, OwnerTrackingMetadata metadata) {
    JsonNode existingProperties = root.get("properties");
    ObjectNode propertiesNode;

    if (existingProperties instanceof ObjectNode existingObjectNode) {
      propertiesNode = existingObjectNode;
    } else {
      propertiesNode = root.objectNode();
      root.set("properties", propertiesNode);
    }

    propertiesNode.put(OwnerTrackingKeys.OWNER, metadata.owner());
    propertiesNode.put(OwnerTrackingKeys.OWNER_ID, metadata.ownerId());
    propertiesNode.put(OwnerTrackingKeys.OWNER_CREATED_AT, metadata.createdAt());
    return true;
  }
}
