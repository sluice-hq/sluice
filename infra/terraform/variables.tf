variable "subscription_id" {
  description = "Azure subscription that owns the Sluice demo resources."
  type        = string

  validation {
    condition     = can(regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$", var.subscription_id))
    error_message = "subscription_id must be an Azure subscription UUID."
  }
}

variable "location" {
  description = "Azure region selected after checking service and quota availability."
  type        = string
  default     = "centralindia"

  validation {
    condition     = can(regex("^[a-z0-9]+$", var.location))
    error_message = "location must use the Azure CLI name, such as centralindia."
  }
}

variable "name_prefix" {
  description = "Short lowercase product prefix used in Azure resource names."
  type        = string
  default     = "sluice"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{2,7}$", var.name_prefix))
    error_message = "name_prefix must be 3-8 lowercase alphanumeric characters and begin with a letter."
  }
}

variable "environment" {
  description = "Deployment environment label."
  type        = string
  default     = "demo"

  validation {
    condition     = can(regex("^[a-z][a-z0-9]{1,5}$", var.environment))
    error_message = "environment must be 2-6 lowercase alphanumeric characters and begin with a letter."
  }
}

variable "name_suffix" {
  description = "Globally unique lowercase suffix for DNS-scoped Azure names."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9]{3,6}$", var.name_suffix))
    error_message = "name_suffix must contain 3-6 lowercase letters or digits."
  }
}

variable "common_tags" {
  description = "Additional non-sensitive tags applied to Azure resources."
  type        = map(string)
  default     = {}
}

variable "postgres_administrator_login" {
  description = "PostgreSQL administrator login name."
  type        = string
  default     = "sluiceadmin"

  validation {
    condition     = can(regex("^[a-zA-Z][a-zA-Z0-9_]{2,62}$", var.postgres_administrator_login))
    error_message = "postgres_administrator_login must be a valid PostgreSQL identifier."
  }
}

variable "postgres_administrator_password" {
  description = "PostgreSQL administrator password. Supply it through TF_VAR_postgres_administrator_password; it is sensitive but remains present in Terraform state."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.postgres_administrator_password) >= 16 && length(var.postgres_administrator_password) <= 128
    error_message = "postgres_administrator_password must contain 16-128 characters."
  }
}

variable "postgres_sku_name" {
  description = "Flexible Server compute SKU. The burstable default minimizes controlled-demo cost."
  type        = string
  default     = "B_Standard_B1ms"
}

variable "postgres_database_name" {
  description = "Application database created on the Flexible Server."
  type        = string
  default     = "sluice"

  validation {
    condition     = can(regex("^[a-z][a-z0-9_]{2,62}$", var.postgres_database_name))
    error_message = "postgres_database_name must be a lowercase PostgreSQL identifier."
  }
}

variable "budget_amount" {
  description = "Monthly Azure resource-group budget in the subscription billing currency."
  type        = number
  default     = 25

  validation {
    condition     = var.budget_amount > 0
    error_message = "budget_amount must be greater than zero."
  }
}

variable "budget_contact_emails" {
  description = "Addresses that receive actual and forecast Azure budget notifications."
  type        = list(string)

  validation {
    condition = length(var.budget_contact_emails) > 0 && alltrue([
      for email in var.budget_contact_emails : can(regex("^[^@[:space:]]+@[^@[:space:]]+\\.[^@[:space:]]+$", email))
    ])
    error_message = "budget_contact_emails must contain at least one valid email address."
  }
}

variable "budget_start_date" {
  description = "First day of the current or next month in RFC3339 format, for example 2026-09-01T00:00:00Z."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{4}-[0-9]{2}-01T00:00:00Z$", var.budget_start_date))
    error_message = "budget_start_date must be the first day of a month at UTC midnight."
  }
}

variable "budget_end_date" {
  description = "Budget end date in RFC3339 format and later than budget_start_date."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{4}-[0-9]{2}-01T00:00:00Z$", var.budget_end_date))
    error_message = "budget_end_date must be the first day of a month at UTC midnight."
  }
}

variable "deploy_container_apps" {
  description = "Create the API, worker, and dashboard apps only after immutable images and required Key Vault secrets exist."
  type        = bool
  default     = false
}

variable "api_image" {
  description = "Fully qualified immutable API image reference using an sha256 digest."
  type        = string

  validation {
    condition     = can(regex("^[^[:space:]]+@sha256:[0-9a-f]{64}$", var.api_image))
    error_message = "api_image must be an immutable image reference ending in @sha256:<64 lowercase hexadecimal characters>."
  }
}

variable "worker_image" {
  description = "Fully qualified immutable worker image reference using an sha256 digest."
  type        = string

  validation {
    condition     = can(regex("^[^[:space:]]+@sha256:[0-9a-f]{64}$", var.worker_image))
    error_message = "worker_image must be an immutable image reference ending in @sha256:<64 lowercase hexadecimal characters>."
  }
}

variable "dashboard_image" {
  description = "Fully qualified immutable dashboard image reference using an sha256 digest."
  type        = string

  validation {
    condition     = can(regex("^[^[:space:]]+@sha256:[0-9a-f]{64}$", var.dashboard_image))
    error_message = "dashboard_image must be an immutable image reference ending in @sha256:<64 lowercase hexadecimal characters>."
  }
}

variable "api_max_replicas" {
  description = "Maximum API replicas for the controlled demo. The minimum is fixed at one for outbox and webhook timers."
  type        = number
  default     = 2

  validation {
    condition     = var.api_max_replicas >= 1 && var.api_max_replicas <= 3
    error_message = "api_max_replicas must be between 1 and 3."
  }
}

variable "dashboard_max_replicas" {
  description = "Maximum dashboard replicas."
  type        = number
  default     = 1

  validation {
    condition     = var.dashboard_max_replicas >= 1 && var.dashboard_max_replicas <= 3
    error_message = "dashboard_max_replicas must be between 1 and 3."
  }
}

variable "worker_max_replicas" {
  description = "Maximum Service Bus queue-scaled worker replicas for the controlled demo."
  type        = number
  default     = 2

  validation {
    condition     = var.worker_max_replicas >= 1 && var.worker_max_replicas <= 5
    error_message = "worker_max_replicas must be between 1 and 5."
  }
}

variable "enable_key_vault_purge_protection" {
  description = "Protect Key Vault from immediate purge. Keep false for an easily removable short-lived demo; enable for production."
  type        = bool
  default     = false
}

variable "postgres_password_secret_name" {
  description = "Existing Key Vault secret containing the PostgreSQL administrator password."
  type        = string
  default     = "postgres-admin-password"
}

variable "storage_connection_string_secret_name" {
  description = "Existing Key Vault secret containing the Storage connection string until the application uses token credentials."
  type        = string
  default     = "storage-connection-string"
}

variable "jwt_secret_name" {
  description = "Existing Key Vault secret containing the JWT signing secret."
  type        = string
  default     = "jwt-signing-secret"
}

variable "auth_audit_pepper_secret_name" {
  description = "Existing Key Vault secret containing the authentication audit pepper."
  type        = string
  default     = "auth-audit-pepper"
}
