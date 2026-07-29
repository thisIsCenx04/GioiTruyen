locals {
  name = "${var.project_name}-${var.environment}"

  public_subnets = {
    for index, availability_zone in var.availability_zones :
    availability_zone => {
      cidr  = var.public_subnet_cidrs[index]
      index = index
    }
  }

  private_subnets = {
    for index, availability_zone in var.availability_zones :
    availability_zone => {
      cidr  = var.private_subnet_cidrs[index]
      index = index
    }
  }

  nat_gateway_zones = var.single_nat_gateway ? [
    var.availability_zones[0]
  ] : var.availability_zones

  runtime_services = toset(["api", "worker", "web", "admin"])

  secret_environment_variables = {
    REDIS_URL                   = "redis-url"
    JWT_SIGNING_KEY             = "jwt-signing-key"
    DATA_ENCRYPTION_KEY         = "data-encryption-key"
    CLOUDINARY_API_SECRET       = "cloudinary-api-secret"
    CLOUDINARY_WEBHOOK_SECRET   = "cloudinary-webhook-secret"
    OTEL_EXPORTER_OTLP_ENDPOINT = "otel-exporter-endpoint"
  }

  common_tags = merge(var.tags, {
    Project     = var.project_name
    Environment = var.environment
    ManagedBy   = "OpenTofu"
    DataClass   = "internal"
  })
}
