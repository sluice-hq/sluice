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

  assert {
    condition     = length(azurerm_container_app.api) == 0 && length(azurerm_container_app.worker) == 0 && length(azurerm_container_app.dashboard) == 0
    error_message = "The first foundation plan must not deploy apps before images and secrets are prepared."
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
}

run "controlled_demo_apps" {
  command = plan

  variables {
    deploy_container_apps = true
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
    error_message = "The worker must start at zero and expose no ingress before L-08G adds Service Bus scaling."
  }

  assert {
    condition     = azurerm_container_app.dashboard[0].template[0].min_replicas == 0
    error_message = "The dashboard should scale to zero when idle."
  }
}

run "reject_mutable_images" {
  command = plan

  variables {
    deploy_container_apps = true
    api_image             = "sluicedemotst001acr.azurecr.io/sluice-api:latest"
    worker_image          = "sluicedemotst001acr.azurecr.io/sluice-worker:latest"
    dashboard_image       = "sluicedemotst001acr.azurecr.io/sluice-dashboard:latest"
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
