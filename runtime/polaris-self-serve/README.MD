<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
-->

# Integration Test Setup

The self-serve integration flow uses the notebook in `jypyter_notebooks/`, a local Polaris server, and an Open Policy Agent (OPA) sidecar that authorizes every Polaris request.

This setup keeps the existing self-serve authentication model and switches Polaris authorization to the built-in OPA authorizer. The sample OPA policy is intentionally permissive for development only: it logs every authorization request and returns `allow=true`.

For the full OPA feature reference, see `docs/managing-security/external-pdp/opa.md`.

## Prerequisites

- Docker and Docker Compose
- Java 21 for the Gradle build
- `jq`
- The Polaris CLI available as `./polaris`

If you want to test the external realm, start Keycloak first:

```bash
docker compose -f getting-started/keycloak/docker-compose.yml up -d --build keycloak
```

## 1. Start the OPA server in Docker

The OPA sample lives under `runtime/polaris-self-serve/opa/`.

Start OPA from the repository root:

```bash
docker compose -f runtime/polaris-self-serve/opa/docker-compose.yml up -d
```

The compose file publishes OPA on `127.0.0.1:8184` and mounts:

- `runtime/polaris-self-serve/opa/config/config.yaml` to enable decision logging to container stdout
- `runtime/polaris-self-serve/opa/policies/polaris.rego` to log each request and allow it

Optional smoke test against the OPA API:

```bash
curl --fail-with-body -s http://127.0.0.1:8184/v1/data/polaris/authz \
  -H 'Content-Type: application/json' \
  -d '{
    "input": {
      "actor": {"principal": "smoke-test", "roles": []},
      "action": "LIST_CATALOGS",
      "resource": {"targets": [], "secondaries": []},
      "context": {"request_id": "smoke-test"}
    }
  }' | jq
```

The expected response includes:

```json
{
  "result": {
    "allow": true
  }
}
```

## 2. Start Polaris with OPA enabled

Start Polaris from the repository root:

```bash
export POLARIS_BOOTSTRAP_CREDENTIALS='realm-internal,root,s3cr3t;realm-external,root,s3cr3t;realm-mixed,root,s3cr3t'


./gradlew run \
  -Dpolaris.bootstrap.credentials="realm-internal,root,s3cr3t;realm-external,root,s3cr3t;realm-mixed,root,s3cr3t" \
  -Dpolaris.realm-context.realms="realm-internal,realm-external,realm-mixed" \
  -Dpolaris.authentication.type="internal" \
  -Dpolaris.authentication.\"realm-external\".type="external" \
  -Dpolaris.authentication.\"realm-mixed\".type="mixed" \
  -Dquarkus.oidc.tenant-enabled=true \
  -Dquarkus.oidc.auth-server-url="http://keycloak:8080/realms/iceberg" \
  -Dquarkus.oidc.client-id="client1" \
  -Dquarkus.oidc.credentials.secret="s3cr3t" \
  -Dquarkus.oidc.roles.role-claim-path="principal_roles" \
  -Dpolaris.oidc.principal-mapper.id-claim-path="principal_id" \
  -Dpolaris.oidc.principal-mapper.name-claim-path="principal_name" \
  -Dpolaris.oidc.principal-roles-mapper.mappings[0].regex="(.+)" \
  -Dpolaris.oidc.principal-roles-mapper.mappings[0].replacement="PRINCIPAL_ROLE:\$1" \
  -Dpolaris.authorization.type=opa-metadata \
  -Dpolaris.authorization.opa.policy-uri=http://127.0.0.1:8184/v1/data/polaris/authz

```

Add this properties to run in debug
./gradlew :polaris-self-serve:run-keycloak \
  -Dpolaris.authorization.type=opa \
  -Dpolaris.authorization.opa.policy-uri=http://127.0.0.1:8184/v1/data/polaris/authz
```

That task already wires the self-serve Keycloak and realm configuration from `runtime/polaris-self-serve/build.gradle.kts`. The only OPA-specific properties you need are:

```properties
polaris.authorization.type=opa
polaris.authorization.opa.policy-uri=http://127.0.0.1:8184/v1/data/polaris/authz
```

If you want more Polaris-side logging while you test, add:


```bash
  -Dquarkus.log.level=DEBUG \
  -Dquarkus.log.category."org.apache.polaris".level=DEBUG
```

## 3. Create tokens for the internal and external realms

Token issuance is part of authentication, not OPA authorization. The catalog and namespace operations in the next section are the requests that should appear in the OPA logs.

Internal token:

```bash
export INTERNAL_TOKEN=$(curl \
  --fail-with-body \
  --resolve polaris:8181:127.0.0.1 \
  -s \
  http://polaris:8181/api/catalog/v1/oauth/tokens \
  --user root:s3cr3t \
  -H 'Polaris-Realm: realm-internal' \
  -d grant_type=client_credentials \
  -d scope=PRINCIPAL_ROLE:ALL | jq -r .access_token)
```

External token from Keycloak:

```bash
export KEYCLOAK_TOKEN=$(curl -s http://keycloak:8080/realms/iceberg/protocol/openid-connect/token \
  --resolve keycloak:8080:127.0.0.1 \
  --user client1:s3cr3t \
  -d grant_type=client_credentials | jq -r .access_token)
```

## 4. Run Polaris operations that will be authorized by OPA

These commands should succeed because the sample OPA policy allows every request.

Create a file-backed catalog in the internal realm:

```bash
./polaris catalogs create \
  --storage-type file \
  --default-base-location file:///tmp/test_catalog \
  test_catalog \
  --access-token "$INTERNAL_TOKEN" \
  --host 127.0.0.1 \
  --port 8181 \
  --realm realm-internal
```

List catalogs in the internal realm:

```bash
./polaris catalogs list \
  --access-token "$INTERNAL_TOKEN" \
  --host 127.0.0.1 \
  --port 8181 \
  --realm realm-internal
```

Create a file-backed catalog in the mixed realm:

```bash
./polaris catalogs create \
  --storage-type file \
  --default-base-location file:///tmp/test_catalog2 \
  test_catalog2 \
  --access-token "$KEYCLOAK_TOKEN" \
  --host 127.0.0.1 \
  --port 8181 \
  --realm realm-mixed
```


```bash
./polaris catalogs create \
  --storage-type file \
  --default-base-location file:///tmp/test_catalog_mixed \
  test_catalog_mixed \
  --host 127.0.0.1 \
  --port 8181 \
  --client-id root \
  --client-secret s3cr3t \
  --realm realm-mixed
```
Create a namespace in the external realm:

```bash
./polaris namespaces create \
  --catalog test_catalog2 \
  --location file:///tmp/test_catalog2/test_namespace \
  test_namespace \
  --access-token "$KEYCLOAK_TOKEN" \
  --host 127.0.0.1 \
  --port 8181 \
  --realm realm-mixed
```

```bash
./polaris namespaces create \
  --catalog test_catalog_mixed \
  --location file:///tmp/test_catalog_mixed/test_namespace2 \
  test_namespace2 \
  --host 127.0.0.1 \
  --port 8181 \
  --client-id root \
  --client-secret s3cr3t \
  --realm realm-mixed
```

## 5. Inspect OPA logs in Docker

Follow the OPA container logs:

```bash
docker compose -f runtime/polaris-self-serve/opa/docker-compose.yml logs -f opa
```

Read the current logs without following:

```bash
docker compose -f runtime/polaris-self-serve/opa/docker-compose.yml logs opa
```

The logs should show both:

- OPA decision log entries emitted by `decision_logs.console: true`
- The explicit policy log line from `polaris.rego`, including the principal, action, resource targets, and request ID

When you run Polaris catalog or namespace commands, you should see a matching OPA log entry for each authorization decision.

## 6. Stop the OPA container

```bash
docker compose -f runtime/polaris-self-serve/opa/docker-compose.yml down
```

## Notes

- This OPA policy is for local development only. It authorizes every request.
- Polaris expects the OPA endpoint to return `result.allow`, which is why the sample uses `http://127.0.0.1:8184/v1/data/polaris/authz`.
- If you edit `runtime/polaris-self-serve/opa/policies/polaris.rego`, the OPA container reloads it because the compose command starts OPA with `--watch`.