# Design Document: Access Control for Apache Iceberg via Apache Polaris & Open Policy Agent (OPA)


## 1. Executive Summary

This document outlines the design for a fine-grained, decentralized Access Control (AC) system for Apache Iceberg Namespaces, Tables, and Views.

By leveraging Apache Polaris as the centralized catalog and Open Policy Agent (OPA) as the stateless authorization engine, permissions are defined directly on Iceberg objects using metadata properties. This architectural pattern decouples policy definition and object metadata from enforcement, allowing for dynamic, inherited, and easily auditable data governance.



## 2. Architecture Overview (Updated)

The architecture relies on a decoupled pattern where Apache Polaris acts as the stateful metadata repository, the query engine interceptor acts as the Policy Enforcement Point (PEP), and OPA acts as the stateless Policy Decision Point (PDP).

To support property-driven access control, two critical runtime interactions must occur:

* Metadata Enrichment during DDL Execution: When a user executes a CREATE NAMESPACE, CREATE TABLE, or CREATE VIEW statement, the query engine interceptor must inject the four designated access control properties (polaris_owners, polaris_data_administrators, polaris_data_writers, polaris_data_readers) into the request before committing it to the Apache Polaris Catalog.

* Contextual Payload Construction during Authorization: When a user executes a DML or DDL query, the OPA Authorizer plugin within the query engine must fetch the target object's metadata and its complete parent namespace hierarchy from Polaris. It must then package these custom properties into the authorization context passed to OPA.

```text
 [ User / Client ]
        │
        │ 1. CREATE TABLE / VIEW / NAMESPACE
        ▼
 ┌────────────────────────────────────────────────────────┐
 │ Query Engine Interceptor (PEP)                         │
 │                                                        │
 │  • Catches DDL and injects polaris_* properties        │
 │  • Fetches target + parent lineage from catalog        │
 └───────────────────────┬────────────────────────────────┘
                         │
                         ├─ 2. Commit enriched metadata ──> [ Apache Polaris Catalog ]
                         │                                    (Stores AC properties)
                         │
                         └─ 3. Pass target + parent ──────> [ OPA Engine (PDP) ]
                               properties in payload          (Evaluates Rego Policy)
```
Component Requirements

* Apache Polaris Catalog: Must natively accept and persist the polaris_owners, polaris_data_administrators, polaris_data_writers, and polaris_data_readers string arrays within the properties block of Iceberg Namespaces, Tables, and Views.

* Query Engine Interceptor / OPA Authorizer:

    * On Creation (DDL): Intercepts creation calls to extract default or user-specified access roles and merges them into the Iceberg object creation properties sent to Polaris.

    * On Authorization (DML/DDL): Dynamically constructs the OPA input payload. It is required to pass the additional properties for both the immediate target (Table/View) and its complete upstream lineage (Namespace/Parent Namespaces) to evaluate top-down inheritance accurately.

## 3. Access Control Model & Roles

Each Iceberg object (Namespace, Table, or View) can be configured with four distinct access control properties. These properties accept a list of identities (users or groups).

Role Definitions

| Iceberg Property | Role Name | Permitted Actions |
| :------- | :------- | :------- |
| polaris_owners |	Owner |	Full control over the object and all child objects (Read, Write, Admin, Drop).|
| polaris_data_administrators |	Administrator |	Alter metadata, manage schemas, modify properties, drop objects (cannot read/write data unless explicitly granted or inheriting ownership).     |
| polaris_data_writers | Writer | Insert, Update, Delete, Merge operations on tables. |
| polaris_data_readers | Reader | Select, Scan, and Read operations on tables/views. |



## 4. Iceberg Metadata Properties Schema

Properties are stored as standard key-value pairs within the Iceberg metadata. Multiple users or groups are defined as nested objects.

```json

  "polaris_data_readers": {
    "users": [
      {
        "display_name": "Alice Smith",
        "email": "alice@example.com"
      }
    ],
    "groups": [
      {
        "object_id": "aaaaaaaa-bbbb-4ccc-8ddd-eeeeeeeeeeee",
        "group_name": "data-readers-team"
      }
    ]
  },
  "polaris_data_writers": {
    "users": [],
    "groups": []
  },
  "polaris_data_administrators": {
    "users": [],
    "groups": []
  },
  "polaris_owners": {
    "users": [
      {
        "display_name": "Owner User",
        "email": "owner@example.com"
      }
    ],
    "groups": [
      {
        "object_id": "12345678-1234-4234-9234-1234567890ab",
        "group_name": "catalog-owners"
      }
    ]
  }

```

## 5. OPA Data Input & Policy (Rego)

When a query engine requests authorization, it compiles an input JSON containing the user context, the targeted action, and the lineage metadata fetched from Apache Polaris (the target object and its parent hierarchy).
5.1 OPA Input Payload Example

When a user or service account creates a namespace, table of view, the authenticated person is added to the polaris_owners.users before the request is authorized by OPA


## 5.1 OPA Input Payload Example before modification
The OPA input document will need to be modified to support object properties

This is the current OPA input object, tblproperties and namespace properties have to be added to the input object, we would only copy properties starting with polaris\_\*

```json
{
  "actor": {
    "principal": "user@example.com",
    "roles": ["<group1 object id>", "<group2 object id>"]
  },
  "action": "CREATE_TABLE_DIRECT",
  "resource": {
    "targets": [
      {
        "type": "TABLE",
        "name": "my_table",
        "parents": [
          {
            "type": "CATALOG",
            "name": "my_catalog"
          },
          {
            "type": "NAMESPACE",
            "name": "schema1"
          }
        ]
      }
    ],
    "secondaries": []
  },
  "context": {
    "request_id": "uuid"
  }
}
```

## 5.2 Modified OPA input document

```json
{
  "actor": {
    "principal": "user@example.com",
    "roles": ["<group1 object id>", "<group2 object id>"]
  },
  "action": "CREATE_TABLE_DIRECT",
  "resource": {
    "targets": [
      {
        "type": "TABLE",
        "name": "my_table",
        "properties": {
          "polaris_data_readers": {
            "users": [],
            "groups": []
          },
          "polaris_data_writers": {
            "users": [],
            "groups": []
          },
          "polaris_data_administrators": {
            "users": [],
            "groups": []
          },
          "polaris_owners": {
            "users": [
              {
                "display_name": "User Exmaple",
                "email": "user@example.com"
              }
            ],
            "groups": []
          }
        },
        "parents": [
          {
            "type": "CATALOG",
            "name": "my_catalog"
          },
          {
            "type": "NAMESPACE",
            "name": "schema1",
            "properties": {
              "polaris_data_readers": {
                "users": [],
                "groups": []
              },
              "polaris_data_writers": {
                "users": [],
                "groups": []
              },
              "polaris_data_administrators": {
                "users": [
                  {
                    "display_name": "User Exmaple",
                    "email": "user@example.com"
                  }
                ],
                "groups": []
              },
              "polaris_owners": {
                "users": [
                  {
                    "display_name": "Owner User",
                    "email": "owner@example.com"
                  }
                ],
                "groups": []
              }
            }
          }
        ]
      }
    ],
    "secondaries": []
  },
  "context": {
    "request_id": "uuid"
  }
}
```

## 6. Security & Operational Considerations

    [!IMPORTANT]
    Property Mutability Guardrails: Because permissions are stored as standard metadata properties, any user with polaris_data_administrators or polaris_owners can alter privileges via ALTER TABLE ... SET PROPERTIES. The OPA engine itself must strictly guard the admin action to prevent unauthorized elevation of privilege.


# Phase II Implementation Proposal


Here is the design documentation for the custom SQL commands and property storage, modifying the identity format to cleanly support user emails and group names mapped to object IDs.

# 1. Structural Identity Format

To ensure that the OPA engine can accurately evaluate identities against your organization's Identity Provider (IdP) (e.g., Entra ID or KeyCloak), identity strings stored in the polaris_* properties must adhere to a strict, prefixed format:

    Users: Prefixed with user: followed by their fully qualified corporate email address.

        Example: user:alice.smith@company.com

    Groups: Prefixed with group: followed by the Group Name, its immutable Object ID and IdP separated by a pipe (|) character.

        Example: group:Finance-Engineers|da39a3ee-5e6b-4b0d-9b32-c6f2a3452101|EntraId

    [!NOTE]
    Including both the Group Name and Object ID ensures that policies remain human-readable within SQL commands, while remaining resilient to group renames over time because OPA can uniquely match the immutable Object ID for Entra Id but on name when using KeyCloak.

# 2. Updated Custom SQL Syntax Examples

The custom SQL commands are updated to accept strings matching these specific identity structures.

## 2.1 Table & View Management

```SQL

-- Granting access using user emails and group mappings
ALTER TABLE catalog.accounting.tax_returns_2026 
ADD DATA_READERS ('user:bob.jones@company.com', 'group:Tax-Auditors|8a71c8b3-1122-3344-5566-778899aabbcc|EntraId');

-- Removing a group's access
ALTER VIEW catalog.accounting.executive_summary 
REMOVE OWNERS ('group:Finance-Leads|b1234567-89ab-cdef-0123-456789abcdef|EntraId');
```

## 2.2 Namespace Management

```SQL

-- Granting global writer rights at the namespace level
ALTER NAMESPACE catalog.accounting 
ADD DATA_WRITERS ('group:Finance-Data-Engineers|f81d4fae-7dec-11d0-a765-00a0c91e6bf6|EntraId');
```


## 3 The Spark SQL Extension Pattern for supporting custom roles

### 3.1 Define the ANTLR4 Grammar

```antlr
grammar PolarisSqlExtensions;

@header {
package org.apache.spark.sql.polaris.parser;
}

statement
    : ALTER (TABLE | VIEW) multipartIdentifier ADD roleType '(' identity (',' identity)* ')' #addRole
    | ALTER NAMESPACE multipartIdentifier ADD roleType '(' identity (',' identity)* ')'     #addNamespaceRole
    ;

roleType
    : DATA_READERS | DATA_WRITERS | DATA_ADMINISTRATORS | OWNERS
    ;

multipartIdentifier
    : errorCapturingIdentifier ('.' errorCapturingIdentifier)*
    ;

identity
    : STRING_LITERAL
    ;
```
### 3.2 
