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

plugins {
  id("org.kordamp.gradle.jandex")
  id("polaris-server")
}

val keycloakBootstrapCredentials =
  (System.getProperty("polaris.bootstrap.credentials")
      ?: project.findProperty("polaris.bootstrap.credentials")?.toString()
      ?: System.getenv("POLARIS_BOOTSTRAP_CREDENTIALS"))
    ?: "realm-internal,root,s3cr3t;realm-external,root,s3cr3t;realm-mixed,root,s3cr3t"

val keycloakJvmArgs =
  listOf(
    "-Dpolaris.bootstrap.credentials=$keycloakBootstrapCredentials",
    "-Dpolaris.realm-context.realms=realm-internal,realm-external,realm-mixed",
    "-Dpolaris.authentication.type=internal",
    "-Dpolaris.authentication.realm-external.type=external",
    "-Dpolaris.authentication.realm-mixed.type=mixed",
    "-Dquarkus.oidc.tenant-enabled=true",
    "-Dquarkus.oidc.auth-server-url=http://127.0.0.1:8080/realms/iceberg",
    "-Dquarkus.oidc.client-id=client1",
    "-Dquarkus.oidc.credentials.secret=s3cr3t",
    "-Dquarkus.oidc.roles.role-claim-path=principal_roles",
    "-Dpolaris.oidc.principal-mapper.id-claim-path=principal_id",
    "-Dpolaris.oidc.principal-mapper.name-claim-path=principal_name",
    "-Dpolaris.oidc.principal-roles-mapper.mappings[0].regex=(.+)",
    "-Dpolaris.oidc.principal-roles-mapper.mappings[0].replacement=PRINCIPAL_ROLE:\$1",
    "-Dquarkus.console.color=true",
    "-Dpolaris.features.\"ALLOW_INSECURE_STORAGE_TYPES\"=true",
    "-Dpolaris.features.\"SUPPORTED_CATALOG_STORAGE_TYPES\"=[\"FILE\",\"S3\",\"GCS\",\"AZURE\"]",
    "-Dpolaris.readiness.ignore-severe-issues=true",
    "-Dpolaris.features.\"DROP_WITH_PURGE_ENABLED\"=true",
  )

tasks.register<GradleBuild>("run-keycloak") {
  group = "application"
  description = "Runs the Apache Polaris server for self-serve Keycloak scenarios"
  tasks = listOf(":polaris-server:run")
  startParameter.systemPropertiesArgs.putAll(
    keycloakJvmArgs
      .mapNotNull {
        if (!it.startsWith("-D")) {
          null
        } else {
          val assignment = it.removePrefix("-D")
          val separatorIndex = assignment.indexOf('=')
          if (separatorIndex <= 0) {
            null
          } else {
            assignment.substring(0, separatorIndex) to assignment.substring(separatorIndex + 1)
          }
        }
      }
      .toMap()
  )
}

dependencies {
  implementation(project(":polaris-core"))

  implementation(platform(libs.quarkus.bom))
  implementation("io.quarkus:quarkus-rest-jackson")
  implementation("io.quarkus:quarkus-security")

  implementation(libs.jakarta.enterprise.cdi.api)
  implementation(libs.jakarta.inject.api)
  implementation(libs.jakarta.ws.rs.api)

  implementation(platform(libs.jackson.bom))
  implementation("com.fasterxml.jackson.core:jackson-databind")

  implementation(libs.slf4j.api)

  testImplementation(enforcedPlatform(libs.junit.bom))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testImplementation(libs.assertj.core)
}
