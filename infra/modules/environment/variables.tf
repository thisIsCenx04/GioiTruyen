variable "project_name" {
  description = "Stable lowercase project identifier used in resource names."
  type        = string
  default     = "gioitruyen"

  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{2,23}$", var.project_name))
    error_message = "project_name must be a lowercase, bounded identifier."
  }
}

variable "environment" {
  description = "Isolated deployment environment."
  type        = string

  validation {
    condition     = contains(["dev", "staging"], var.environment)
    error_message = "environment must be dev or staging."
  }
}

variable "region" {
  description = "AWS region used by this isolated environment."
  type        = string
}

variable "vpc_cidr" {
  description = "Environment-specific VPC IPv4 CIDR."
  type        = string

  validation {
    condition     = can(cidrnetmask(var.vpc_cidr))
    error_message = "vpc_cidr must be a valid IPv4 CIDR."
  }
}

variable "availability_zones" {
  description = "Two distinct availability zones for environment parity."
  type        = list(string)

  validation {
    condition = (
      length(var.availability_zones) == 2
      && length(distinct(var.availability_zones)) == 2
    )
    error_message = "Exactly two distinct availability zones are required."
  }
}

variable "public_subnet_cidrs" {
  description = "One public subnet CIDR per availability zone."
  type        = list(string)

  validation {
    condition = (
      length(var.public_subnet_cidrs) == 2
      && alltrue([for cidr in var.public_subnet_cidrs : can(cidrnetmask(cidr))])
    )
    error_message = "Two valid public subnet CIDRs are required."
  }
}

variable "private_subnet_cidrs" {
  description = "One private runtime subnet CIDR per availability zone."
  type        = list(string)

  validation {
    condition = (
      length(var.private_subnet_cidrs) == 2
      && alltrue([for cidr in var.private_subnet_cidrs : can(cidrnetmask(cidr))])
    )
    error_message = "Two valid private subnet CIDRs are required."
  }
}

variable "single_nat_gateway" {
  description = "Use one NAT gateway for cost-sensitive dev; staging uses one per AZ."
  type        = bool
}

variable "container_image" {
  description = "Immutable ECR image reference, always pinned by sha256 digest."
  type        = string

  validation {
    condition = can(regex(
      "^[0-9]{12}\\.dkr\\.ecr\\.[a-z0-9-]+\\.amazonaws\\.com/[a-z0-9][a-z0-9._/-]*@sha256:[a-f0-9]{64}$",
      var.container_image
    ))
    error_message = "container_image must be an ECR URI pinned with @sha256."
  }
}

variable "api_desired_count" {
  description = "API tasks to start; keep zero until secrets and image exist."
  type        = number
  default     = 0

  validation {
    condition     = var.api_desired_count >= 0 && var.api_desired_count <= 20
    error_message = "api_desired_count must be between 0 and 20."
  }
}

variable "worker_desired_count" {
  description = "Worker tasks to start; keep zero until secrets and image exist."
  type        = number
  default     = 0

  validation {
    condition     = var.worker_desired_count >= 0 && var.worker_desired_count <= 20
    error_message = "worker_desired_count must be between 0 and 20."
  }
}

variable "log_retention_days" {
  description = "CloudWatch application and network-flow log retention."
  type        = number

  validation {
    condition     = contains([30, 60, 90, 120, 180, 365], var.log_retention_days)
    error_message = "log_retention_days must use an approved retention period."
  }
}

variable "secret_recovery_window_days" {
  description = "Recovery window protecting managed secret references."
  type        = number

  validation {
    condition = (
      var.secret_recovery_window_days >= 7
      && var.secret_recovery_window_days <= 30
    )
    error_message = "Secret recovery window must be between 7 and 30 days."
  }
}

variable "github_repository" {
  description = "GitHub owner/repository allowed to assume the deployment role."
  type        = string
  default     = "thisIsCenx04/GioiTruyen"

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must use owner/repository format."
  }
}

variable "tags" {
  description = "Additional non-sensitive resource tags."
  type        = map(string)
  default     = {}
}
