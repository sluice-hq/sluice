# Controlled Azure demo deployment

The repository can be validated without an Azure account. No subscription, price,
free allowance, quota, live delivery, or deployment is verified by those tests.
Keep `deploy_container_apps = false` until the preparation below is complete.
The production configuration uses real Azure providers and invited registration.
An empty registration allowlist closes new signup. Public self-service remains
the separate L-08I gate.

## 1. Account, cost, and region approval

Create an account only when ready for the hosted rehearsal. Confirm eligibility
and the actual offer in the portal. Free-account credit is not a permanent free
deployment. Do not upgrade to paid billing or provision resources on the assumption
that all of this architecture fits free allowances.

Record the subscription, billing currency, approved maximum spend, expiry/teardown
date, operator address, and these selected services in a private cost worksheet:

| Resource | Selection to price and verify |
|---|---|
| PostgreSQL Flexible Server | B1ms, 32 GiB, seven-day backups, selected region |
| Container Apps | API minimum one replica, worker 0–2, dashboard 0–1 |
| Registry | Basic storage and image transfer |
| Service Bus | Standard namespace and operations |
| Blob and state storage | Capacity, operations, versioning, soft deletion and egress |
| Telemetry | Log Analytics ingestion/retention, Application Insights and alerts |
| Content Safety | Supported region, F0 availability/quota or explicitly approved S0 |
| ACS Email | Supported geography, sender/domain availability and per-message usage |
| Network | Cross-region traffic, DNS and any additional network resources |

Budget alerts are notifications, not a spending cutoff. The 25-unit Terraform
default is a notification threshold, not a cost estimate or permission to spend.
Stop if any item cannot fit the approved allowance. Recheck prices immediately
before apply. Record an expiry no more than a few days ahead in the GitHub
environment variable `AZURE_PREFLIGHT_VALID_UNTIL` as an RFC3339 UTC timestamp.

Use Azure CLI to inspect the selected account and registered providers:

```powershell
az login
az account set --subscription '<subscription UUID>'
az account show --query '{subscription:id,tenant:tenantId,name:name}'
az provider show --namespace Microsoft.App --query registrationState
az provider show --namespace Microsoft.DBforPostgreSQL --query registrationState
az provider show --namespace Microsoft.CognitiveServices --query registrationState
az provider show --namespace Microsoft.Communication --query registrationState
```

Register the required namespaces through an authorized account: Microsoft.App,
Microsoft.OperationalInsights, Microsoft.Insights, Microsoft.ContainerRegistry,
Microsoft.DBforPostgreSQL, Microsoft.Network, Microsoft.Storage,
Microsoft.ServiceBus, Microsoft.KeyVault, Microsoft.ManagedIdentity,
Microsoft.CognitiveServices, Microsoft.Communication and Microsoft.Consumption.
Verify regional SKUs/quotas in the portal, including Container Apps CPU,
PostgreSQL burstable capacity, Content Safety and the email geography. Terraform
schema validation does not prove subscription eligibility or regional capacity.

## 2. Bootstrap encrypted remote state

An authorized operator creates the state account outside the application resource
group. Use a globally unique account name. The main Terraform root must never use
local state: the database password and provider-computed service keys are retained
in state even though Terraform outputs hide them.

```powershell
az group create --name '<state resource group>' --location '<reviewed region>'
az storage account create --name '<state account>' --resource-group '<state resource group>' --location '<reviewed region>' --sku Standard_LRS --kind StorageV2 --min-tls-version TLS1_2 --https-only true --allow-blob-public-access false --allow-shared-key-access false
az storage account blob-service-properties update --account-name '<state account>' --resource-group '<state resource group>' --enable-versioning true --enable-delete-retention true --delete-retention-days 7
```

Grant the operator Storage Blob Data Contributor on this account, wait for role
propagation, then create private `tfstate` and `plans` containers with
`az storage container create --auth-mode login --account-name '<state account>' --name '<container>' --public-access off`.
Azure Storage encrypts these blobs at rest. Restrict account/network access to the
operators and runners that actually need it. Configure a storage lifecycle rule
to delete saved plan blobs and their versions after a short approved retention;
do not apply that rule to `tfstate`. Retain state versions until final cleanup is
confirmed. Never publish state, binary plans, full plan JSON, or unredacted logs
as GitHub artifacts.

## 3. Federated identities and protected GitHub environments

Create separate Microsoft Entra applications/service principals for planning,
applying, and publishing. Add a federated credential to each application:

| Identity | Federated subject |
|---|---|
| Plan | `repo:sluice-hq/sluice:environment:azure-demo-plan` |
| Apply | `repo:sluice-hq/sluice:environment:azure-demo-apply` |
| Publish | `repo:sluice-hq/sluice:environment:azure-demo-publish` |

Issuer: `https://token.actions.githubusercontent.com`. Audience:
`api://AzureADTokenExchange`. Change the owner/repository if using a fork. Create
no client secret. Client/application ID and service-principal object ID are
different: the workflow uses the client ID for login and the apply principal's
object ID for Key Vault key-management permission.

Precreate the exact application resource group (`rg-<prefix>-<environment>-<suffix>`)
as an operator. Grant roles only on that group and the dedicated state containers:

| Identity | Required scope and access |
|---|---|
| Plan | Reader on application group and the dedicated state storage account; state-container Blob Data Contributor for refresh/lease; plan-container Blob Data Contributor for upload; Key Vault key read/rotation-policy read and service `listKeys` read actions when those resources exist |
| Apply | Contributor plus Role Based Access Control Administrator on application group; Reader on the dedicated state storage account; state-container Blob Data Contributor; plan-container Blob Data Reader; Key Vault Crypto Officer granted by Terraform |
| Publish | AcrPush on the created registry, no application/state/Key Vault role |

The planner's supplementary custom role must contain only the refresh operations
the pinned provider needs, including `Microsoft.CognitiveServices/accounts/listKeys/action`,
`Microsoft.Communication/communicationServices/listKeys/action` and
`Microsoft.Storage/storageAccounts/listKeys/action`. Its Key Vault permission is
read-only key/rotation-policy access, never key creation, wrapping, unwrapping, or
secret retrieval. Review provider refresh permission errors explicitly; do not
silently grant Owner. State access itself is sensitive and restricted to trusted
operators. Role Based Access Control Administrator can grant roles within its
scope, so protect the apply identity accordingly.

Storage data roles on a container do not authorize the management-plane
`az storage account show` preflight. Both plan and apply identities therefore need
Reader on the dedicated state storage account itself, in addition to the scoped
blob data roles. The API cryptography role targets the versionless Key Vault key
resource so old envelopes and automatically rotated key versions remain usable.

Before the first workflow, an operator initializes this Terraform root against the
remote backend using `backend.hcl.example`, sets the required variables, and
imports the precreated resource group:

```powershell
terraform -chdir=infra/terraform init -backend-config=backend.hcl
terraform -chdir=infra/terraform import azurerm_resource_group.main '/subscriptions/<subscription>/resourceGroups/<application group>'
```

This import records the existing group in remote state; it does not recreate it.
Do not run a foundation apply locally merely to bypass workflow approval.

Create the three named GitHub environments. Restrict each to the `main` branch.
Require a reviewer for apply and publish; prevent bypass. For a solo repository,
the owner can remain the reviewer if self-review is permitted. The deploy workflow
checks that the apply environment actually has required reviewers. GitHub plan
limitations must be resolved before using this workflow, not bypassed.

Configure environment variables `AZURE_CLIENT_ID`, `AZURE_TENANT_ID`, and
`AZURE_SUBSCRIPTION_ID` for the corresponding identity. Plan and apply also need
`AZURE_STATE_ACCOUNT` and `AZURE_STATE_GROUP`. The plan environment needs:

- `AZURE_APPLY_PRINCIPAL_ID`: the apply service principal's object ID.
- `AZURE_PREFLIGHT_VALID_UNTIL`: reviewed cost/quota approval expiry.
- `AZURE_TFVARS_JSON`: a JSON object equivalent to `terraform.tfvars.example`,
  containing only reviewed non-secret configuration and digest image references.
- Secret `POSTGRES_ADMIN_PASSWORD`: a generated password supplied only through
  `TF_VAR_postgres_administrator_password`. Never include it in the JSON variable.

The publish environment needs `AZURE_ACR_NAME`. Keep these three environment names
fixed unless the workflows and federated trust subjects are changed together.

## 4. Foundation, providers, images and secrets

1. Run **Deploy Azure demo** from `main` with apply disabled and `cost_approval`
   set to `REVIEWED`. Initially keep apps and hosted providers disabled. A passing
   CI push run for the exact commit is required.
2. Review resource actions in the job summary. An authorized operator downloads
   the referenced binary plan from the private `plans` container with
   `az storage blob download --auth-mode login`, verifies its SHA-256, and runs
   `terraform -chdir=infra/terraform show '<downloaded plan>'` locally. That view
   can contain secrets; do not paste it into issues, screenshots, or public logs.
3. To apply, dispatch a fresh run with apply enabled. Review that run's own saved
   plan, then approve the waiting apply environment. It applies exactly those
   bytes, checks the SHA-256, a 24-hour age limit and the original preflight expiry,
   and does not replan. An expired cost/account approval requires a new reviewed run.
4. After foundation, grant the scoped publisher role. Run **Publish Azure release
   images** for the reviewed main commit. Record all three resulting
   `registry/repository@sha256:...` references in `AZURE_TFVARS_JSON`.
5. Set `enable_hosted_providers = true` while keeping apps disabled. Review/apply
   the provider resource plan. Content Safety denies public clients and permits
   the Container Apps subnet through the Cognitive Services service endpoint.
   Its actual region/tier/network behavior remains a live acceptance check.
6. In Azure, copy the Azure-managed domain's exact verified sender address into
   `email_sender_address`. Azure handles domain ownership/SPF/DKIM for this domain.
   A custom domain requires a separately reviewed DNS/domain-verification change.
7. Populate these Key Vault secrets out of band using the portal or a secure
   operator session, without printing values or placing them in command history:

   | Secret | Consumer |
   |---|---|
   | `postgres-admin-password` | API and worker, identical to provisioned DB password |
   | `storage-connection-string` | API and worker |
   | `jwt-signing-secret` | API |
   | `auth-audit-pepper` | API |
   | `communication-email-access-key` | API only, from the ACS resource |
   | `content-safety-api-key` | Worker only, from Content Safety |

8. Set `demo_registration_allowed_emails` to invited addresses, set
   `deploy_container_apps = true`, and review/apply another saved plan. The
   configuration rejects application deployment without real providers and a
   sender. Check all Key Vault references resolve before routing demo traffic.

Role propagation, service endpoints, sender verification, Key Vault versionless
references, quotas and diagnostics all require real Azure verification. A passed
mock plan does not mean the first hosted apply will succeed. If apply partially
fails, inspect state and provider errors, fix the cause, and create a fresh reviewed
plan. Never run the old plan again to guess past an error.

## 5. Hosted acceptance and rollback rehearsal

API and worker images include Application Insights Java Agent 3.7.10, pinned by
artifact checksum. The entrypoint attaches it only when the configured connection
string is non-empty. Terraform supplies distinct API/worker role names. Local
Compose leaves the exporter detached. Verify hosted request/dependency traces,
log redaction and sampling costs before recording telemetry ingestion as passed.
The dashboard currently relies on Container Apps console logs, without browser
or Node SDK instrumentation.

The Service Bus health indicator uses last-publish evidence, so it is UNKNOWN
before the first queue operation and is not an active connectivity probe.
The production profile disables the unused Rabbit health check and listeners.

Record evidence against a release commit and the three immutable image digests:

| Gate | Required hosted evidence |
|---|---|
| Core API | HTTPS readiness, invited signup/login, tenant isolation, create key, upload supported real image, optimized derivative and matching caller references |
| Governance | Real Content Safety evidence, ALLOW proceeds, REVIEW pauses, BLOCK has no normal output, provider/network/quota failure is visible and never interpreted as ALLOW |
| Async execution | Private worker has no ingress, wakes from zero on Service Bus, finishes run, verified signed terminal webhook, expected bytes and metadata |
| Email | Verification and reset arrive at a controlled mailbox; expired/reused tokens fail; retry/restart preserves queued email; no plaintext link in DB/logs |
| Secrets | API can use wrapping key/email key, worker can use moderation key; each identity is denied the other provider key |
| Operations | Dead-letter alert and Content Safety error alert fire; ACS delivery diagnostics and run-correlated API/worker logs appear without credentials or message links |
| Recovery | Previously saved compatible image set is redeployed, health and golden path pass, and desired state matches Terraform |

Do not call L-08C/L-08D/L-08J complete until this evidence exists. ACS delivery-failure
alert query/table creation and any subscription-specific diagnostics limitations must be verified
during rehearsal. The Content Safety smoke path must execute inside the permitted
subnet, normally by submitting the same public API reference-client workflow; a
direct request from a developer laptop should be denied by the network rule.

For rollback, keep the previous compatible image digests, provider settings and
release commit. Put those digests into the reviewed Terraform input and dispatch
a new plan/apply run. Run health and reference-client acceptance again. Container
Apps uses single active revisions; changing the image creates a replacement
revision. Retained inactive revisions are not a substitute for restoring Terraform
desired state. Database migrations are forward-only: never restore an older binary
that cannot read the current schema or encrypted secrets. After webhook protected
cutover, plaintext-only binaries cannot be rollback targets.

For emergency traffic removal, an authorized operator may disable ingress or stop
revisions in the portal. Record the intervention and reconcile Terraform before
the next deployment. This is containment, not a verified rollback.

## 6. Teardown and retained data

Decide first whether demo assets, audit records or the database must be retained.
Take only explicitly approved backups. Initialize the same remote backend and
create `terraform plan -destroy -out=destroy.tfplan` as an authorized operator.
Review the complete plan, confirm the exact subscription/group names, then run
`terraform apply destroy.tfplan`. Destroy is deliberately not a one-click workflow.

Verify application resources and spend have stopped in Azure. Key Vault and
Cognitive Services can remain soft-deleted; do not purge them merely to reuse a
name. The provider deliberately avoids Key Vault purge. Check PostgreSQL backup
retention and any Azure-managed residual resources before declaring teardown
complete. Remove GitHub federation/roles when the demo is retired. Keep remote
state until verification and approved retention finish, then separately remove
the dedicated state group, plan blobs, retained versions and operator permissions.
Never delete the state group before destroying and verifying the application.

## Reference documentation

- [Terraform AzureRM backend and Microsoft Entra/OIDC authentication](https://developer.hashicorp.com/terraform/language/backend/azurerm)
- [Azure login through GitHub OIDC](https://learn.microsoft.com/en-us/azure/developer/github/connect-from-azure-openid-connect)
- [GitHub deployment environment protection](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments)
- [Azure Content Safety limits and support](https://learn.microsoft.com/en-us/azure/ai-services/content-safety/language-support)
- [Azure services virtual networks](https://learn.microsoft.com/en-us/azure/ai-services/cognitive-services-virtual-networks)
- [ACS Email domain setup](https://learn.microsoft.com/en-us/azure/communication-services/quickstarts/email/add-azure-managed-domains)
- [Azure budget behavior](https://learn.microsoft.com/en-us/azure/cost-management-billing/costs/tutorial-acm-create-budgets)
