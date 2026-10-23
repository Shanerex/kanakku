tags: [architecture, conventions, layers]
Decision: controller = routes and validation, service = business rules and transactions, repository = all SQL and external sources, domain = shared records and types. Services never hold a DSLContext.
Utils: module-local helpers go in the module's `util` package; cross-module helpers go in `platform` behind a named interface. A global `utils` package is avoided because it escapes Modulith boundaries.
Applied to identity (UserRepository, SignInFailureRepository) and platform (IdempotencyKeyRepository) in the accounts-password PR.
Packages: flat under the module root: controller, request, validation, advice, service, repository, domain, exception, config, util; api/ is the only named interface. Modulith hides every sub-package except the named interface, so no web/ or internal/ wrapper is needed. Classes used across these subpackages are public, which Modulith still hides from other modules.
