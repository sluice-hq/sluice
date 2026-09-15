locals {
  name_base    = "${var.name_prefix}-${var.environment}-${var.name_suffix}"
  compact_name = "${var.name_prefix}${var.environment}${var.name_suffix}"

  resource_group_name       = "rg-${local.name_base}"
  container_registry_name   = "${local.compact_name}acr"
  storage_account_name      = "${local.compact_name}st"
  key_vault_name            = "${local.compact_name}kv"
  service_bus_namespace     = "${local.name_base}-bus"
  service_bus_queue         = "media-runs"
  postgres_server_name      = "${local.name_base}-pg"
  container_environment     = "${local.name_base}-cae"
  api_app_name              = "${local.name_base}-api"
  worker_app_name           = "${local.name_base}-worker"
  dashboard_app_name        = "${local.name_base}-dashboard"
  log_analytics_name        = "${local.name_base}-logs"
  application_insights_name = "${local.name_base}-appi"

  dashboard_origin = "https://${local.dashboard_app_name}.${azurerm_container_app_environment.main.default_domain}"
  api_origin       = "https://${local.api_app_name}.${azurerm_container_app_environment.main.default_domain}"
  blob_origin      = "https://${local.storage_account_name}.blob.core.windows.net"

  key_vault_secret_ids = {
    postgres_password = "${azurerm_key_vault.main.vault_uri}secrets/${var.postgres_password_secret_name}"
    storage           = "${azurerm_key_vault.main.vault_uri}secrets/${var.storage_connection_string_secret_name}"
    jwt               = "${azurerm_key_vault.main.vault_uri}secrets/${var.jwt_secret_name}"
    audit_pepper      = "${azurerm_key_vault.main.vault_uri}secrets/${var.auth_audit_pepper_secret_name}"
  }

  tags = merge(var.common_tags, {
    application = "Sluice"
    environment = var.environment
    managed-by  = "Terraform"
  })
}
