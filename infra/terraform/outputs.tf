output "resource_group_name" {
  description = "Resource group containing the Sluice demo platform."
  value       = azurerm_resource_group.main.name
}

output "container_registry" {
  description = "ACR login server used to publish immutable release images."
  value       = azurerm_container_registry.main.login_server
}

output "key_vault_name" {
  description = "Key Vault where deployment secrets must be populated out of band."
  value       = azurerm_key_vault.main.name
}

output "storage_account_name" {
  description = "Private Blob Storage account used for media assets."
  value       = azurerm_storage_account.main.name
}

output "postgres_fqdn" {
  description = "Private PostgreSQL Flexible Server endpoint."
  value       = azurerm_postgresql_flexible_server.main.fqdn
}

output "application_insights_connection_string" {
  description = "Sensitive Application Insights connection string for controlled deployment wiring."
  value       = azurerm_application_insights.main.connection_string
  sensitive   = true
}

output "service_bus_contract" {
  description = "Non-secret broker contract that L-08G must implement in the application adapter and KEDA rule."
  value = {
    fully_qualified_namespace          = "${azurerm_servicebus_namespace.main.name}.servicebus.windows.net"
    queue_name                         = azurerm_servicebus_queue.runs.name
    lock_duration                      = azurerm_servicebus_queue.runs.lock_duration
    max_delivery_count                 = azurerm_servicebus_queue.runs.max_delivery_count
    duplicate_detection                = azurerm_servicebus_queue.runs.requires_duplicate_detection
    duplicate_detection_history_window = azurerm_servicebus_queue.runs.duplicate_detection_history_time_window
    dead_letter_on_expiration          = azurerm_servicebus_queue.runs.dead_lettering_on_message_expiration
    local_auth_enabled                 = azurerm_servicebus_namespace.main.local_auth_enabled
  }
}

output "dashboard_url" {
  description = "Stable dashboard URL, or null until deploy_container_apps is enabled."
  value       = var.deploy_container_apps ? local.dashboard_origin : null
}

output "api_url" {
  description = "Stable API base URL, or null until deploy_container_apps is enabled."
  value       = var.deploy_container_apps ? "${local.api_origin}/api/v1" : null
}

output "managed_identity_client_ids" {
  description = "Client IDs used by the API, worker, and dashboard."
  value = {
    api       = azurerm_user_assigned_identity.api.client_id
    worker    = azurerm_user_assigned_identity.worker.client_id
    dashboard = azurerm_user_assigned_identity.dashboard.client_id
  }
}
