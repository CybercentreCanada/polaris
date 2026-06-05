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
import java.util.Set;

/** Holds inbound namespace property changes for the active HTTP request. */
@RequestScoped
public class PendingNamespacePropertiesHolder {

  private Map<String, String> inboundUpdates = Map.of();
  private Set<String> inboundRemovals = Set.of();

  public void setInboundPropertyChanges(Map<String, String> updates, Set<String> removals) {
    if (updates == null || updates.isEmpty()) {
      this.inboundUpdates = Map.of();
    } else {
      this.inboundUpdates = Map.copyOf(updates);
    }

    if (removals == null || removals.isEmpty()) {
      this.inboundRemovals = Set.of();
    } else {
      this.inboundRemovals = Set.copyOf(removals);
    }
  }

  public Map<String, String> getInboundUpdates() {
    return inboundUpdates;
  }

  public Set<String> getInboundRemovals() {
    return inboundRemovals;
  }
}
