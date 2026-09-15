resource "azurerm_servicebus_namespace" "main" {
  name                          = local.service_bus_namespace
  resource_group_name           = azurerm_resource_group.main.name
  location                      = azurerm_resource_group.main.location
  sku                           = "Standard"
  capacity                      = 0
  minimum_tls_version           = "1.2"
  local_auth_enabled            = false
  public_network_access_enabled = true
  tags                          = local.tags
}

resource "azurerm_servicebus_queue" "runs" {
  name                                    = local.service_bus_queue
  namespace_id                            = azurerm_servicebus_namespace.main.id
  lock_duration                           = "PT5M"
  max_delivery_count                      = 5
  max_size_in_megabytes                   = 1024
  default_message_ttl                     = "P1D"
  dead_lettering_on_message_expiration    = true
  requires_duplicate_detection            = true
  duplicate_detection_history_time_window = "PT10M"
  requires_session                        = false
  batched_operations_enabled              = true
}
