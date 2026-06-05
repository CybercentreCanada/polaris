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

import jakarta.enterprise.context.RequestScoped;
import java.util.Map;

/** Holds inbound table properties for the active HTTP request. */
@RequestScoped
public class PendingTablePropertiesHolder {

  private Map<String, String> inboundSetProperties = Map.of();

  public void setInboundSetProperties(Map<String, String> inboundSetProperties) {
    if (inboundSetProperties == null || inboundSetProperties.isEmpty()) {
      this.inboundSetProperties = Map.of();
      return;
    }
    this.inboundSetProperties = Map.copyOf(inboundSetProperties);
  }

  public Map<String, String> getInboundSetProperties() {
    return inboundSetProperties;
  }
}
