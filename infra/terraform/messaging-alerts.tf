resource "azurerm_monitor_action_group" "operations" {
  name                = "${local.name_base}-operations"
  resource_group_name = azurerm_resource_group.main.name
  short_name          = "sluiceops"
  tags                = local.tags

  dynamic "email_receiver" {
    for_each = toset(var.budget_contact_emails)
    content {
      name                    = "operator-${substr(sha256(email_receiver.value), 0, 8)}"
      email_address           = email_receiver.value
      use_common_alert_schema = true
    }
  }
}

resource "azurerm_monitor_metric_alert" "service_bus_dead_letters" {
  name                = "${local.name_base}-service-bus-dead-letters"
  resource_group_name = azurerm_resource_group.main.name
  scopes              = [azurerm_servicebus_namespace.main.id]
  description         = "A run message entered the Service Bus dead-letter queue."
  severity            = 1
  frequency           = "PT1M"
  window_size         = "PT5M"
  tags                = local.tags

  criteria {
    metric_namespace = "Microsoft.ServiceBus/namespaces"
    metric_name      = "DeadletteredMessages"
    aggregation      = "Average"
    operator         = "GreaterThan"
    threshold        = 0

    dimension {
      name     = "EntityName"
      operator = "Include"
      values   = [azurerm_servicebus_queue.runs.name]
    }
  }

  action {
    action_group_id = azurerm_monitor_action_group.operations.id
  }
}
