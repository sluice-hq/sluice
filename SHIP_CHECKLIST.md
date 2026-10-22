# Shipping Sluice

The first release is a controlled Azure portfolio demonstration for invited users. Public self-service registration requires the separate L-08I shared abuse controls and capacity quotas. This checklist distinguishes source implementation from behavior that must be verified against an Azure account.

## Implementation plan

| Area | Work | Acceptance evidence |
|---|---|---|
| Email reliability | Encrypted PostgreSQL outbox, atomic token enqueue, leased delivery, bounded retries, expiry suppression and cleanup | Focused database/crypto/auth tests and full backend suite |
| Governance correctness | Reject incomplete provider evidence, validate Azure request constraints, record provider latency/errors, opt-in live smoke | Malformed-response, media-boundary and policy tests; live test remains pending credentials |
| Developer adoption | Typed reference helper and independent marketplace example, real image processing, opaque application references, verified signed callback | Node helper tests and API demo against release containers |
| Runtime and access | Supported Node runtime; exact local callback exception; production signup restricted to invited accounts | Runtime/security tests, frontend build and browser golden path |
| Hosted observability | Actual Service Bus health source and checksum-pinned Java telemetry agent, detached locally | Broker health tests, release startup and offline agent initialization; real ingestion deferred |
| Deployment preparation | Azure provider resource/config definitions, OIDC delivery, immutable images, protected reviewed plan/apply, rollback and teardown runbook | Credential-free Terraform plan tests and workflow inspection |
| Release verification | Backend, external broker/storage integration, frontend lint/build, release-image health, browser path and API reference integration | Record actual commands and results in local SDD |

## Credentials and live connections deferred

- Azure subscription eligibility, region, quotas and per-service cost approval.
- Remote Terraform state storage and GitHub federated identity/environment protection.
- Container Registry publication, Key Vault secrets, verified email sender and Content Safety endpoint.
- Live email receipt, Content Safety responses, Service Bus scale-from-zero, telemetry, rollback and teardown rehearsal.

No Azure resource has been applied or live provider behavior proven by local tests. Budget notifications do not cap spending. Follow [the deployment runbook](infra/terraform/DEPLOYMENT.md) before enabling the manual deployment workflow.

## Local verification results

- Backend: 227 tests passed after review fixes, with no failures, errors or skips; the separate real broker/storage integration also passed.
- Reference helper: TypeScript check and all 8 Node tests passed on Windows and inside the Linux release image, including interrupted upload completion and checkpoint retry coverage.
- Frontend: lint, production build and the release-container Playwright golden path passed. Processor search/limit descriptions were checked at desktop and mobile widths without browser errors or horizontal overflow.
- Release containers: API, worker and frontend built and reached healthy state; worker has no HTTP listener. The Java telemetry agent initialized under disabled networking with dummy local configuration, without contacting Azure.
- Marketplace reference integration: real upload, correlated assets, policy outcome, metadata removal, WebP download and verified signed terminal callback passed. A callback timestamp serialization bug discovered by this test was fixed and regression-tested.
- Infrastructure: Terraform formatting/validation and 7 mock plan tests passed. All workflow files passed actionlint. No plan was applied or image published.

Review regression coverage includes token lock ordering under real PostgreSQL
contention, independent scheduler progress during stalled email calls, checkpoint
recovery after a lost completion response, versionless key permissions, and LF
checkout for the Linux entrypoint. Final release-container Playwright passed in
49.7 seconds; the API demonstration verified idempotent run replay and a signed
terminal callback.

These results describe local verification. Hosted GitHub execution and the live gates above remain outstanding.

## Deliberately outside this release

L-08I public-beta quotas/gateway controls, arbitrary custom processors, billing, audio/video processing, and a general media-library crawler remain separate work. Existing media must be submitted through the API; Sluice does not scan external storage automatically.

Before accepting untrusted public webhook endpoints, L-08I must also enforce pinned DNS resolution or an outbound proxy/network policy. The existing URL validator checks resolved addresses, but the HTTP client resolves the hostname again at connection time; controlled demo users remain trusted while this public-beta boundary is deferred.
