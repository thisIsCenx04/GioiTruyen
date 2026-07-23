variable "account_id" {
  description = "Dedicated AWS account for the staging environment."
  type        = string

  validation {
    condition     = can(regex("^[0-9]{12}$", var.account_id))
    error_message = "account_id must contain exactly 12 digits."
  }
}

variable "region" {
  description = "AWS region for staging."
  type        = string
  default     = "ap-southeast-1"
}

variable "container_image" {
  description = "API image pinned by digest; initially a deployment placeholder."
  type        = string
}
