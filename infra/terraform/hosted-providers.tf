# Keys are deliberately not read into application configuration or secret resources.
# AzureRM still retains provider-computed service keys in encrypted remote state.
resource "azurerm_cognitive_account" "content_safety" {
  count                 = var.enable_hosted_providers ? 1 : 0
  name                  = "${local.name_base}-safety"
  location              = var.content_safety_location
  resource_group_name   = azurerm_resource_group.main.name
  kind                  = "ContentSafety"
  sku_name              = var.content_safety_sku
  custom_subdomain_name = "${local.name_base}-safety"
  tags                  = local.tags

  network_acls {
    default_action = "Deny"
    bypass         = "None"
    virtual_network_rules {
      subnet_id = azurerm_subnet.container_apps.id
    }
  }
}

resource "azurerm_communication_service" "email" {
  count               = var.enable_hosted_providers ? 1 : 0
  name                = "${local.name_base}-communication"
  resource_group_name = azurerm_resource_group.main.name
  data_location       = var.email_data_location
  tags                = local.tags
}

resource "azurerm_email_communication_service" "email" {
  count               = var.enable_hosted_providers ? 1 : 0
  name                = "${local.name_base}-email"
  resource_group_name = azurerm_resource_group.main.name
  data_location       = var.email_data_location
  tags                = local.tags
}

resource "azurerm_email_communication_service_domain" "email" {
  count             = var.enable_hosted_providers ? 1 : 0
  name              = "AzureManagedDomain"
  email_service_id  = azurerm_email_communication_service.email[0].id
  domain_management = "AzureManaged"
  tags              = local.tags
}

resource "azurerm_communication_service_email_domain_association" "email" {
  count                    = var.enable_hosted_providers ? 1 : 0
  communication_service_id = azurerm_communication_service.email[0].id
  email_service_domain_id  = azurerm_email_communication_service_domain.email[0].id
}

resource "azurerm_monitor_diagnostic_setting" "content_safety" {
  count                      = var.enable_hosted_providers ? 1 : 0
  name                       = "provider-diagnostics"
  target_resource_id         = azurerm_cognitive_account.content_safety[0].id
  log_analytics_workspace_id = azurerm_log_analytics_workspace.main.id
  enabled_log {
    category_group = "allLogs"
  }
  enabled_metric {
    category = "AllMetrics"
  }
}

resource "azurerm_monitor_diagnostic_setting" "email" {
  count                          = var.enable_hosted_providers ? 1 : 0
  name                           = "email-delivery-diagnostics"
  target_resource_id             = azurerm_communication_service.email[0].id
  log_analytics_workspace_id     = azurerm_log_analytics_workspace.main.id
  log_analytics_destination_type = "Dedicated"
  enabled_log {
    category_group = "allLogs"
  }
  enabled_metric {
    category = "AllMetrics"
  }
}

resource "azurerm_monitor_scheduled_query_rules_alert_v2" "email_delivery_failures" {
  count                = var.enable_hosted_providers ? 1 : 0
  name                 = "${local.name_base}-email-delivery-failures"
  resource_group_name  = azurerm_resource_group.main.name
  location             = azurerm_resource_group.main.location
  scopes               = [azurerm_log_analytics_workspace.main.id]
  description          = "Authentication email delivery was bounced, suppressed, or failed. Inspect restricted ACS diagnostics."
  severity             = 1
  evaluation_frequency = "PT5M"
  window_duration      = "PT15M"
  # The resource-specific table exists only after the first provider log arrives.
  skip_query_validation = true
  tags                  = local.tags

  criteria {
    query                   = <<-KQL
      ACSEmailStatusUpdateOperational
      | where _ResourceId =~ '${azurerm_communication_service.email[0].id}'
      | where DeliveryStatus in ('Bounced', 'Suppressed', 'Failed')
      | project TimeGenerated, _ResourceId
    KQL
    time_aggregation_method = "Count"
    operator                = "GreaterThan"
    threshold               = 0
    failing_periods {
      minimum_failing_periods_to_trigger_alert = 1
      number_of_evaluation_periods             = 1
    }
  }
  action {
    action_groups = [azurerm_monitor_action_group.operations.id]
  }
  depends_on = [azurerm_monitor_diagnostic_setting.email]
}

resource "azurerm_monitor_metric_alert" "content_safety_errors" {
  count               = var.enable_hosted_providers ? 1 : 0
  name                = "${local.name_base}-content-safety-errors"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [azurerm_cognitive_account.content_safety[0].id]
  severity            = 1
  frequency           = "PT1M"
  window_size         = "PT5M"
  tags                = local.tags
  criteria {
    metric_namespace = "Microsoft.CognitiveServices/accounts"
    metric_name      = "TotalErrors"
    aggregation      = "Total"
    operator         = "GreaterThan"
    threshold        = 0
  }
  action {
    action_group_id = azurerm_monitor_action_group.operations.id
  }
}
