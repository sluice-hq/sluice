resource "terraform_data" "configuration_contract" {
  input = {
    deploy_container_apps = var.deploy_container_apps
    budget_start_date     = var.budget_start_date
    budget_end_date       = var.budget_end_date
  }

  lifecycle {
    precondition {
      condition     = timecmp(var.budget_end_date, var.budget_start_date) > 0
      error_message = "budget_end_date must be later than budget_start_date."
    }
  }
}

resource "azurerm_container_app" "api" {
  count = var.deploy_container_apps ? 1 : 0

  name                         = local.api_app_name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  max_inactive_revisions       = 3
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.api.id]
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = azurerm_user_assigned_identity.api.id
  }

  secret {
    name                = "postgres-password"
    identity            = azurerm_user_assigned_identity.api.id
    key_vault_secret_id = local.key_vault_secret_ids.postgres_password
  }

  secret {
    name                = "storage-connection"
    identity            = azurerm_user_assigned_identity.api.id
    key_vault_secret_id = local.key_vault_secret_ids.storage
  }

  secret {
    name                = "jwt-signing-secret"
    identity            = azurerm_user_assigned_identity.api.id
    key_vault_secret_id = local.key_vault_secret_ids.jwt
  }

  secret {
    name                = "auth-audit-pepper"
    identity            = azurerm_user_assigned_identity.api.id
    key_vault_secret_id = local.key_vault_secret_ids.audit_pepper
  }

  ingress {
    external_enabled           = true
    allow_insecure_connections = false
    target_port                = 8080
    transport                  = "auto"

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = 1
    max_replicas = var.api_max_replicas

    http_scale_rule {
      name                = "http"
      concurrent_requests = 50
    }

    container {
      name   = "api"
      image  = var.api_image
      cpu    = 0.5
      memory = "1Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "production"
      }
      env {
        name  = "SLUICE_RUNTIME_MODE"
        value = "api"
      }
      env {
        name  = "SLUICE_MESSAGING_PROVIDER"
        value = "servicebus"
      }
      env {
        name  = "AZURE_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE"
        value = "${azurerm_servicebus_namespace.main.name}.servicebus.windows.net"
      }
      env {
        name  = "AZURE_SERVICE_BUS_QUEUE_NAME"
        value = azurerm_servicebus_queue.runs.name
      }
      env {
        name  = "AZURE_SERVICE_BUS_MAX_DELIVERY_COUNT"
        value = tostring(azurerm_servicebus_queue.runs.max_delivery_count)
      }
      env {
        name  = "AZURE_CLIENT_ID"
        value = azurerm_user_assigned_identity.api.client_id
      }
      env {
        name  = "SLUICE_DB_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${var.postgres_database_name}?sslmode=require&options=-c%20TimeZone=UTC"
      }
      env {
        name  = "SLUICE_DB_USERNAME"
        value = var.postgres_administrator_login
      }
      env {
        name        = "SLUICE_DB_PASSWORD"
        secret_name = "postgres-password"
      }
      env {
        name  = "SLUICE_FLYWAY_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${var.postgres_database_name}?sslmode=require&options=-c%20TimeZone=UTC"
      }
      env {
        name  = "SLUICE_FLYWAY_USERNAME"
        value = var.postgres_administrator_login
      }
      env {
        name        = "SLUICE_FLYWAY_PASSWORD"
        secret_name = "postgres-password"
      }
      env {
        name        = "AZURE_STORAGE_CONNECTION_STRING"
        secret_name = "storage-connection"
      }
      env {
        name  = "AZURE_STORAGE_CONTAINER_NAME"
        value = azurerm_storage_container.assets.name
      }
      env {
        name  = "AZURE_STORAGE_PUBLIC_BASE_URL"
        value = local.blob_origin
      }
      env {
        name        = "SLUICE_JWT_SECRET"
        secret_name = "jwt-signing-secret"
      }
      env {
        name        = "SLUICE_AUTH_AUDIT_PEPPER"
        secret_name = "auth-audit-pepper"
      }
      env {
        name  = "SLUICE_CORS_ALLOWED_ORIGINS"
        value = local.dashboard_origin
      }
      env {
        name  = "SLUICE_AUTH_FRONTEND_BASE_URL"
        value = local.dashboard_origin
      }
      env {
        name  = "SLUICE_AUTH_EMAIL_PROVIDER"
        value = "local"
      }
      env {
        name  = "SLUICE_GOVERNANCE_PROVIDER"
        value = "local"
      }
      env {
        name  = "MANAGEMENT_ENDPOINT_HEALTH_PROBES_ENABLED"
        value = "true"
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = azurerm_application_insights.main.connection_string
      }

      liveness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/liveness"
        initial_delay           = 45
        interval_seconds        = 30
        timeout                 = 5
        failure_count_threshold = 3
      }

      readiness_probe {
        transport               = "HTTP"
        port                    = 8080
        path                    = "/actuator/health/readiness"
        initial_delay           = 30
        interval_seconds        = 15
        timeout                 = 5
        failure_count_threshold = 6
      }
    }
  }

  lifecycle {
    precondition {
      condition     = startswith(var.api_image, "${azurerm_container_registry.main.login_server}/")
      error_message = "api_image must come from the ACR created by this configuration."
    }
  }

  depends_on = [
    azurerm_role_assignment.api_acr_pull,
    azurerm_role_assignment.api_key_vault_secrets,
    azurerm_role_assignment.api_service_bus_sender,
  ]
}

resource "azurerm_container_app" "worker" {
  count = var.deploy_container_apps ? 1 : 0

  name                         = local.worker_app_name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  max_inactive_revisions       = 3
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.worker.id]
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = azurerm_user_assigned_identity.worker.id
  }

  secret {
    name                = "postgres-password"
    identity            = azurerm_user_assigned_identity.worker.id
    key_vault_secret_id = local.key_vault_secret_ids.postgres_password
  }

  secret {
    name                = "storage-connection"
    identity            = azurerm_user_assigned_identity.worker.id
    key_vault_secret_id = local.key_vault_secret_ids.storage
  }

  template {
    min_replicas                = 0
    max_replicas                = var.worker_max_replicas
    polling_interval_in_seconds = 15
    cooldown_period_in_seconds  = 60

    custom_scale_rule {
      name             = "service-bus-runs"
      custom_rule_type = "azure-servicebus"
      identity_id      = azurerm_user_assigned_identity.worker.id
      metadata = {
        namespace    = azurerm_servicebus_namespace.main.name
        queueName    = azurerm_servicebus_queue.runs.name
        messageCount = "1"
      }
    }

    container {
      name   = "worker"
      image  = var.worker_image
      cpu    = 1
      memory = "2Gi"

      env {
        name  = "SPRING_PROFILES_ACTIVE"
        value = "production"
      }
      env {
        name  = "SLUICE_RUNTIME_MODE"
        value = "worker"
      }
      env {
        name  = "SLUICE_MESSAGING_PROVIDER"
        value = "servicebus"
      }
      env {
        name  = "AZURE_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE"
        value = "${azurerm_servicebus_namespace.main.name}.servicebus.windows.net"
      }
      env {
        name  = "AZURE_SERVICE_BUS_QUEUE_NAME"
        value = azurerm_servicebus_queue.runs.name
      }
      env {
        name  = "AZURE_SERVICE_BUS_MAX_DELIVERY_COUNT"
        value = tostring(azurerm_servicebus_queue.runs.max_delivery_count)
      }
      env {
        name  = "AZURE_CLIENT_ID"
        value = azurerm_user_assigned_identity.worker.client_id
      }
      env {
        name  = "SPRING_FLYWAY_ENABLED"
        value = "false"
      }
      env {
        name  = "SLUICE_DB_URL"
        value = "jdbc:postgresql://${azurerm_postgresql_flexible_server.main.fqdn}:5432/${var.postgres_database_name}?sslmode=require&options=-c%20TimeZone=UTC"
      }
      env {
        name  = "SLUICE_DB_USERNAME"
        value = var.postgres_administrator_login
      }
      env {
        name        = "SLUICE_DB_PASSWORD"
        secret_name = "postgres-password"
      }
      env {
        name        = "AZURE_STORAGE_CONNECTION_STRING"
        secret_name = "storage-connection"
      }
      env {
        name  = "AZURE_STORAGE_CONTAINER_NAME"
        value = azurerm_storage_container.assets.name
      }
      env {
        name  = "AZURE_STORAGE_PUBLIC_BASE_URL"
        value = local.blob_origin
      }
      env {
        name  = "SLUICE_GOVERNANCE_PROVIDER"
        value = "local"
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = azurerm_application_insights.main.connection_string
      }
    }
  }

  lifecycle {
    precondition {
      condition     = startswith(var.worker_image, "${azurerm_container_registry.main.login_server}/")
      error_message = "worker_image must come from the ACR created by this configuration."
    }
  }

  depends_on = [
    azurerm_role_assignment.worker_acr_pull,
    azurerm_role_assignment.worker_key_vault_secrets,
    azurerm_role_assignment.worker_service_bus_receiver,
  ]
}

resource "azurerm_container_app" "dashboard" {
  count = var.deploy_container_apps ? 1 : 0

  name                         = local.dashboard_app_name
  container_app_environment_id = azurerm_container_app_environment.main.id
  resource_group_name          = azurerm_resource_group.main.name
  revision_mode                = "Single"
  max_inactive_revisions       = 3
  tags                         = local.tags

  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.dashboard.id]
  }

  registry {
    server   = azurerm_container_registry.main.login_server
    identity = azurerm_user_assigned_identity.dashboard.id
  }

  ingress {
    external_enabled           = true
    allow_insecure_connections = false
    target_port                = 3000
    transport                  = "auto"

    traffic_weight {
      latest_revision = true
      percentage      = 100
    }
  }

  template {
    min_replicas = 0
    max_replicas = var.dashboard_max_replicas

    http_scale_rule {
      name                = "http"
      concurrent_requests = 30
    }

    container {
      name   = "dashboard"
      image  = var.dashboard_image
      cpu    = 0.25
      memory = "0.5Gi"

      env {
        name  = "API_BASE_URL"
        value = "${local.api_origin}/api/v1"
      }
      env {
        name  = "SLUICE_PUBLIC_API_BASE_URL"
        value = "${local.api_origin}/api/v1"
      }
      env {
        name  = "SLUICE_DASHBOARD_URL"
        value = local.dashboard_origin
      }
      env {
        name  = "SLUICE_SECURE_COOKIES"
        value = "true"
      }
      env {
        name  = "SLUICE_STORAGE_PUBLIC_BASE_URL"
        value = local.blob_origin
      }
      env {
        name  = "SLUICE_STORAGE_INTERNAL_BASE_URL"
        value = local.blob_origin
      }
      env {
        name  = "APPLICATIONINSIGHTS_CONNECTION_STRING"
        value = azurerm_application_insights.main.connection_string
      }

      liveness_probe {
        transport               = "HTTP"
        port                    = 3000
        path                    = "/"
        initial_delay           = 20
        interval_seconds        = 30
        timeout                 = 5
        failure_count_threshold = 3
      }

      readiness_probe {
        transport               = "HTTP"
        port                    = 3000
        path                    = "/"
        initial_delay           = 10
        interval_seconds        = 15
        timeout                 = 5
        failure_count_threshold = 6
      }
    }
  }

  lifecycle {
    precondition {
      condition     = startswith(var.dashboard_image, "${azurerm_container_registry.main.login_server}/")
      error_message = "dashboard_image must come from the ACR created by this configuration."
    }
  }

  depends_on = [azurerm_role_assignment.dashboard_acr_pull]
}
