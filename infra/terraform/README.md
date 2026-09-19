# Azure infrastructure

This Terraform root defines the L-08F Azure foundation for Sluice. It is intentionally safe to validate without an Azure account and intentionally staged for a real deployment.

## What it creates

- One resource group and monthly cost budget with 50%, forecasted 80%, and actual 100% notifications.
- Basic Azure Container Registry with the administrator account disabled.
- A workload-profiles Azure Container Apps environment connected to Log Analytics.
- Separate API, worker, and dashboard Container Apps and user-assigned managed identities.
- Private-networked PostgreSQL Flexible Server and the `sluice` database.
- Standard locally redundant Blob Storage with a private `assets` container.
- Standard Service Bus with a `media-runs` queue, duplicate detection, bounded delivery attempts, dead-lettering, and an operator email alert when the dead-letter count rises above zero.
- RBAC-enabled Key Vault, Log Analytics, and workspace-based Application Insights.
- Least-privilege ACR, Blob, Service Bus, and Key Vault role assignments for each runtime.

The Container App definitions are present for plan-time review and include the L-08G application configuration and Managed Identity KEDA rule. When enabled, the API has a fixed minimum of one replica because it owns outbox, webhook, and durable recovery timers; the dashboard can scale to zero; and the private worker wakes when the Service Bus run queue contains work.

## What it does not complete

The configuration does not enable real Azure Content Safety, configure Azure Communication Services Email, add API Management, publish or deploy images, or prove the hosted golden path. The foundation temporarily configures local email and governance providers when the apps are enabled so those later integrations are not falsely represented as complete. Service Bus application wiring is implemented and locally tested, but still requires a live Azure deployment to verify Managed Identity, KEDA, network, and broker behavior.

This configuration is not permanently free. PostgreSQL Flexible Server, Standard Service Bus, the always-on API replica, registry storage, telemetry ingestion, and network traffic can consume Azure credit. Budget notifications report spend but do not stop resources automatically.

## Local validation

Terraform 1.16.2 and AzureRM 5.4.0 are pinned. Validation and mock plans require no Azure credentials:

```powershell
terraform -chdir=infra/terraform init -backend=false
terraform -chdir=infra/terraform fmt -check -recursive
terraform -chdir=infra/terraform validate
terraform -chdir=infra/terraform test
```

The mock tests prove the important plan invariants without creating cloud resources or spending money.

## Real deployment sequence

Do not run `terraform apply` until an Azure subscription, supported region, quotas, prices, and the remote state backend have been reviewed.

1. Create a dedicated Azure Storage container for remote Terraform state and copy `backend.hcl.example` to an ignored `backend.hcl`.
2. Copy `terraform.tfvars.example` to the ignored `terraform.tfvars` and replace non-sensitive identifiers, dates, contact addresses, and names.
3. Set the PostgreSQL password only in the process environment:

   ```powershell
   $env:TF_VAR_postgres_administrator_password = '<generate-a-long-random-password>'
   ```

4. Authenticate with Azure CLI and initialize the remote backend:

   ```powershell
   az login
   az account set --subscription '<subscription-id>'
   terraform -chdir=infra/terraform init -backend-config=backend.hcl
   terraform -chdir=infra/terraform plan -out=foundation.tfplan
   ```

5. Review and apply the first plan with `deploy_container_apps = false`. This creates the platform foundation but no application revisions.
6. Publish the API, worker, and dashboard images to the created ACR. Resolve each image digest and replace the example image references with `registry/repository@sha256:digest` values.
7. Populate these Key Vault secrets out of band so their plaintext does not enter source control or Terraform configuration:

   - `postgres-admin-password`
   - `storage-connection-string`
   - `jwt-signing-secret`
   - `auth-audit-pepper`

8. Confirm the saved plan contains the Service Bus environment contract, sender/receiver role assignments, dead-letter alert, and Managed Identity worker scale rule.
9. Set `deploy_container_apps = true`, create a new saved plan, review it, and apply it. Never apply an old plan after changing images or secrets.

The PostgreSQL password is necessarily sent to the AzureRM provider and retained in Terraform state. Use the encrypted remote backend, restrict access to it, and never apply this root with local state.

## State and secret safety

- `terraform.tfvars`, backend configuration, state, plan files, and `.terraform` directories are ignored.
- Checked-in examples contain no usable credential.
- Container Apps reference Key Vault secret URLs through managed identities; Terraform does not read the secret values.
- Outputs contain identifiers and endpoints only. The Application Insights connection string is marked sensitive.
- Image variables require digest references and the app resources require the registry created by this root.
- Blob and Key Vault endpoints remain public for the controlled demo, but the Blob container is private, Key Vault requires RBAC, ACR administration is disabled, and Service Bus shared-key authentication is disabled. The application still uses a Key Vault-backed Storage connection string until token-based Blob access is implemented.
