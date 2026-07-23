provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]

  default_tags {
    tags = {
      Project     = "gioitruyen"
      Environment = "staging"
      ManagedBy   = "OpenTofu"
      Repository  = "thisIsCenx04/GioiTruyen"
    }
  }
}

module "environment" {
  source = "../../modules/environment"

  environment                 = "staging"
  region                      = var.region
  vpc_cidr                    = "10.30.0.0/16"
  availability_zones          = ["ap-southeast-1a", "ap-southeast-1b"]
  public_subnet_cidrs         = ["10.30.0.0/24", "10.30.1.0/24"]
  private_subnet_cidrs        = ["10.30.10.0/24", "10.30.11.0/24"]
  single_nat_gateway          = false
  container_image             = var.container_image
  api_desired_count           = 0
  worker_desired_count        = 0
  log_retention_days          = 90
  secret_recovery_window_days = 30
}
