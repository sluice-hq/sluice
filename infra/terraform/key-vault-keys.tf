resource "azurerm_key_vault_key" "webhook_secret_kek" {
  name         = "webhook-secret-kek"
  key_vault_id = azurerm_key_vault.main.id
  key_type     = "RSA"
  key_size     = 2048

  key_opts = [
    "unwrapKey",
    "wrapKey",
  ]

  rotation_policy {
    automatic {
      time_before_expiry = "P30D"
    }

    expire_after         = "P90D"
    notify_before_expiry = "P29D"
  }

  tags = local.tags

  depends_on = [azurerm_role_assignment.terraform_key_vault_crypto_officer]
}

resource "azurerm_role_assignment" "terraform_key_vault_crypto_officer" {
  scope                = azurerm_key_vault.main.id
  role_definition_name = "Key Vault Crypto Officer"
  principal_id         = coalesce(var.terraform_key_manager_object_id, data.azurerm_client_config.current.object_id)
}
