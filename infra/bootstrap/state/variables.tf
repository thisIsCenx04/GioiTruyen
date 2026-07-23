variable "account_id" {
  description = "Dedicated AWS account that owns this environment state."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must contain exactly 12 digits."
  }
}

variable "region" {
  description = "AWS region for the state bucket and KMS key."
  type        = string
  default     = "ap-southeast-1"
}

variable "environment" {
  description = "Environment whose remote state is isolated by this stack."
  type        = string

  validation {
    condition     = contains(["dev", "staging"], var.environment)
    error_message = "environment must be dev or staging."
  }
}

variable "state_bucket_name" {
  description = "Globally unique S3 bucket name without secrets or account data."
  type        = string

  validation {
    condition     = can(regex("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$", var.state_bucket_name))
    error_message = "state_bucket_name must be a valid S3 bucket name."
  }
}
