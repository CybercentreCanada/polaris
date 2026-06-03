#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

package polaris.authz

import future.keywords.if

default allow := false

allow if {
    print(sprintf("polaris self-serve OPA allow action=%v principal=%v targets=%v secondaries=%v request_id=%v", [
        input.action,
        input.actor.principal,
        input.resource.targets,
        input.resource.secondaries,
        input.context.request_id,
    ]))
}