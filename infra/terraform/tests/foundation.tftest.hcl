mock_provider "azurerm" {
  mock_data "azurerm_client_config" {
    defaults = {
      client_id       = "00000000-0000-0000-0000-000000000001"
      object_id       = "00000000-0000-0000-0000-000000000002"
      subscription_id = "00000000-0000-0000-0000-000000000000"
      tenant_id       = "00000000-0000-0000-0000-000000000003"
    }
  }

  mock_resource "azurerm_container_registry" {
    defaults = {
      login_server = "sluicedemotst001acr.azurecr.io"
    }
  }

  mock_resource "azurerm_container_app_environment" {
    defaults = {
      default_domain = "mock.centralindia.azurecontainerapps.io"
    }
  }

}

variables {
  subscription_id                 = "00000000-0000-0000-0000-000000000000"
  location                        = "centralindia"
  name_suffix                     = "tst001"
  postgres_administrator_password = "Test-only-password-1234"
  budget_contact_emails           = ["test@example.com"]
  budget_start_date               = "2026-09-01T00:00:00Z"
  budget_end_date                 = "2027-09-01T00:00:00Z"
  api_image                       = "sluicedemotst001acr.azurecr.io/sluice-api@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  worker_image                    = "sluicedemotst001acr.azurecr.io/sluice-worker@sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  dashboard_image                 = "sluicedemotst001acr.azurecr.io/sluice-dashboard@sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc"
}

run "foundation_without_apps" {
  command = plan

  override_resource {
    target          = azurerm_key_vault_key.webhook_secret_kek
    override_during = plan
    values = {
      resource_versionless_id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/mock/providers/Microsoft.KeyVault/vaults/mock/keys/webhook-secret-kek"
      resource_id             = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/mock/providers/Microsoft.KeyVault/vaults/mock/keys/webhook-secret-kek/version1"
    }
  }

  assert {
    condition     = length(azurerm_container_app.api) == 0 && length(azurerm_container_app.worker) == 0 && length(azurerm_container_app.dashboard) == 0
    error_message = "The first foundation plan must not deploy apps before images and secrets are prepared."
  }

  assert {
    condition     = length(azurerm_cognitive_account.content_safety) == 0 && length(azurerm_communication_service.email) == 0
    error_message = "Foundation must not silently provision external AI or email resources."
  }

  assert {
    condition     = azurerm_postgresql_flexible_server.main.public_network_access_enabled == false
    error_message = "PostgreSQL must not expose a public endpoint."
  }

  assert {
    condition     = azurerm_storage_container.assets.container_access_type == "private"
    error_message = "The media container must remain private."
  }

  assert {
    condition     = azurerm_servicebus_namespace.main.sku == "Standard" && azurerm_servicebus_namespace.main.local_auth_enabled == false
    error_message = "Hosted messaging must use Standard Service Bus with managed-identity authentication."
  }

  assert {
    condition     = azurerm_servicebus_queue.runs.requires_duplicate_detection && azurerm_servicebus_queue.runs.dead_lettering_on_message_expiration
    error_message = "The run queue must keep duplicate detection and dead-letter expired messages."
  }

  assert {
    condition     = azurerm_log_analytics_workspace.main.daily_quota_gb == 0.5 && azurerm_application_insights.main.daily_data_cap_in_gb == 1
    error_message = "Log Analytics and Application Insights must retain controlled-demo ingestion caps."
  }

  assert {
    condition     = azurerm_role_assignment.api_service_bus_sender.role_definition_name == "Azure Service Bus Data Sender" && azurerm_role_assignment.worker_service_bus_receiver.role_definition_name == "Azure Service Bus Data Receiver"
    error_message = "The API and worker must receive separate send-only and receive-only Service Bus roles."
  }

  assert {
    condition     = contains(azurerm_key_vault_key.webhook_secret_kek.key_opts, "wrapKey") && contains(azurerm_key_vault_key.webhook_secret_kek.key_opts, "unwrapKey") && azurerm_key_vault_key.webhook_secret_kek.rotation_policy[0].expire_after == "P90D" && azurerm_role_assignment.api_webhook_key_crypto.role_definition_name == "Key Vault Crypto User"
    error_message = "Webhook envelope encryption must use a Key Vault wrapping key available only to the API identity."
  }

  assert {
    condition     = azurerm_role_assignment.api_webhook_key_crypto.scope == azurerm_key_vault_key.webhook_secret_kek.resource_versionless_id
    error_message = "API key permissions must cover the key across versions so automatic rotation does not revoke wrap/unwrap access."
  }
}

run "controlled_demo_apps" {
  command = plan

  override_resource {
    target          = azurerm_user_assigned_identity.worker
    override_during = plan
    values = {
      id = "/subscriptions/00000000-0000-0000-0000-000000000000/resourceGroups/mock/providers/Microsoft.ManagedIdentity/userAssignedIdentities/worker"
    }
  }

  variables {
    deploy_container_apps   = true
    enable_hosted_providers = true
    email_sender_address    = "DoNotReply@test.azurecomm.net"
  }

  assert {
    condition     = azurerm_container_app.api[0].template[0].min_replicas == 1
    error_message = "The controlled-demo API must keep one replica for outbox and webhook timers."
  }

  assert {
    condition     = azurerm_container_app.api[0].ingress[0].external_enabled && azurerm_container_app.api[0].template[0].container[0].liveness_probe[0].path == "/actuator/health/liveness" && azurerm_container_app.api[0].template[0].container[0].readiness_probe[0].path == "/actuator/health/readiness"
    error_message = "The API must expose HTTPS ingress and separate liveness and readiness probes."
  }

  assert {
    condition     = azurerm_container_app.worker[0].template[0].min_replicas == 0 && length(azurerm_container_app.worker[0].ingress) == 0
    error_message = "The worker must start at zero and expose no ingress."
  }

  assert {
    condition     = azurerm_container_app.worker[0].template[0].custom_scale_rule[0].custom_rule_type == "azure-servicebus" && azurerm_container_app.worker[0].template[0].custom_scale_rule[0].identity_id == azurerm_user_assigned_identity.worker.id
    error_message = "The worker must scale from the run queue using its receiver-only managed identity."
  }

  assert {
    condition     = azurerm_container_app.worker[0].template[0].custom_scale_rule[0].metadata["queueName"] == azurerm_servicebus_queue.runs.name && azurerm_container_app.worker[0].template[0].custom_scale_rule[0].metadata["messageCount"] == "1"
    error_message = "The worker scale rule must target the run queue and wake for one queued message."
  }

  assert {
    condition     = azurerm_container_app.dashboard[0].template[0].min_replicas == 0
    error_message = "The dashboard should scale to zero when idle."
  }

  assert {
    condition     = one([for setting in azurerm_container_app.api[0].template[0].container[0].env : setting.value if setting.name == "SLUICE_WEBHOOK_ENCRYPTION_PROVIDER"]) == "azure" && one([for setting in azurerm_container_app.api[0].template[0].container[0].env : setting.value if setting.name == "SLUICE_WEBHOOK_ENCRYPTION_MIGRATION_MODE"]) == "protected" && contains([for setting in azurerm_container_app.api[0].template[0].container[0].env : setting.name], "AZURE_KEY_VAULT_URL") && contains([for setting in azurerm_container_app.api[0].template[0].container[0].env : setting.name], "AZURE_WEBHOOK_KEK_NAME")
    error_message = "The API app must select Azure Key Vault webhook-secret protection."
  }

  assert {
    condition     = one([for setting in azurerm_container_app.api[0].template[0].container[0].env : setting.value if setting.name == "SLUICE_AUTH_EMAIL_PROVIDER"]) == "azure" && one([for setting in azurerm_container_app.worker[0].template[0].container[0].env : setting.value if setting.name == "SLUICE_GOVERNANCE_PROVIDER"]) == "azure"
    error_message = "Hosted apps must never silently use simulated email or content moderation."
  }

  assert {
    condition     = azurerm_cognitive_account.content_safety[0].network_acls[0].default_action == "Deny" && azurerm_cognitive_account.content_safety[0].network_acls[0].bypass == "None"
    error_message = "Content Safety must restrict access to the application subnet."
  }

  assert {
    condition     = !contains(keys(azurerm_role_assignment.worker_key_vault_secrets), var.email_key_secret_name) && !contains(keys(azurerm_role_assignment.api_key_vault_secrets), var.content_safety_key_secret_name)
    error_message = "Worker and API must not share provider secret permissions."
  }

  assert {
    condition     = one([for setting in azurerm_container_app.api[0].template[0].container[0].env : setting.secret_name if setting.name == "AZURE_COMMUNICATION_EMAIL_ACCESS_KEY"]) == "email-access-key" && one([for setting in azurerm_container_app.worker[0].template[0].container[0].env : setting.secret_name if setting.name == "AZURE_CONTENT_SAFETY_API_KEY"]) == "content-safety-key"
    error_message = "Provider credentials must enter application settings through Key Vault-backed secrets, never plaintext values."
  }

  assert {
    condition     = one([for setting in azurerm_container_app.api[0].template[0].container[0].env : setting.value if setting.name == "SLUICE_AUTH_REGISTRATION_MODE"]) == "allowlist" && length(var.demo_registration_allowed_emails) == 0
    error_message = "Hosted registration must default to a closed allowlist."
  }
}

run "reject_apps_without_real_providers" {
  command = plan
  variables {
    deploy_container_apps = true
  }
  expect_failures = [terraform_data.configuration_contract]
}

run "reject_apps_without_sender" {
  command = plan
  variables {
    deploy_container_apps   = true
    enable_hosted_providers = true
  }
  expect_failures = [terraform_data.configuration_contract]
}

run "separate_plan_and_apply_identities" {
  command = plan
  variables {
    terraform_key_manager_object_id = "00000000-0000-0000-0000-000000000099"
  }
  assert {
    condition     = azurerm_role_assignment.terraform_key_vault_crypto_officer.principal_id == var.terraform_key_manager_object_id
    error_message = "Key-management permission must follow the explicit apply identity rather than the caller that creates the plan."
  }
}

run "reject_mutable_images" {
  command = plan

  variables {
    deploy_container_apps   = true
    enable_hosted_providers = true
    email_sender_address    = "DoNotReply@test.azurecomm.net"
    api_image               = "sluicedemotst001acr.azurecr.io/sluice-api:latest"
    worker_image            = "sluicedemotst001acr.azurecr.io/sluice-worker:latest"
    dashboard_image         = "sluicedemotst001acr.azurecr.io/sluice-dashboard:latest"
  }

  expect_failures = [
    var.api_image,
    var.worker_image,
    var.dashboard_image,
  ]
}

run "reject_malformed_subscription_id" {
  command = plan

  variables {
    subscription_id = "------------------------------------"
  }

  expect_failures = [var.subscription_id]
}
