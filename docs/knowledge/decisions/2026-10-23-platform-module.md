tags: [modulith, idempotency, platform]
Decision: new Modulith module `platform` holds the Idempotency-Key store, RFC 9457 problem mapping and the shared Clock; every later create reuses it.
OpenAPI snapshot openapi/kanakku-v1.json is the client source; OpenApiSnapshotTest fails on drift.
