provider "aws" {
  region              = var.region
  allowed_account_ids = [var.account_id]

  default_tags {
    tags = {
      Project     = "gioitruyen"
      Environment = "dev"
      ManagedBy   = "OpenTofu"
      Repository  = "thisIsCenx04/GioiTruyen"
    }
  }
}

module "environment" {
  source = "../../modules/environment"

  environment                 = "dev"
  region                      = var.region
  vpc_cidr                    = "10.20.0.0/16"
  availability_zones          = ["ap-southeast-1a", "ap-southeast-1b"]
  public_subnet_cidrs         = ["10.20.0.0/24", "10.20.1.0/24"]
  private_subnet_cidrs        = ["10.20.10.0/24", "10.20.11.0/24"]
  single_nat_gateway          = true
  container_image             = var.container_image
  api_desired_count           = 0
  worker_desired_count        = 0
  log_retention_days          = 30
  secret_recovery_window_days = 7
}
