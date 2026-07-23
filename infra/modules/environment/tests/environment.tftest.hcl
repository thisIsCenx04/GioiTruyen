mock_provider "aws" {
  mock_data "aws_caller_identity" {
    defaults = {
      account_id = "111111111111"
    }
  }

  mock_data "aws_partition" {
    defaults = {
      partition = "aws"
    }
  }

  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"sts:AssumeRole\",\"Principal\":{\"Service\":\"ecs-tasks.amazonaws.com\"}}]}"
    }
  }

  mock_data "aws_prefix_list" {
    defaults = {
      id = "pl-00000000000000000"
    }
  }

  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::111111111111:role/mock-role"
      id  = "mock-role"
    }
  }

  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:ap-southeast-1:111111111111:log-group:mock"
    }
  }

  mock_resource "aws_kms_key" {
    defaults = {
      arn    = "arn:aws:kms:ap-southeast-1:111111111111:key/00000000-0000-0000-0000-000000000000"
      key_id = "00000000-0000-0000-0000-000000000000"
    }
  }

  mock_resource "aws_secretsmanager_secret" {
    defaults = {
      arn = "arn:aws:secretsmanager:ap-southeast-1:111111111111:secret:mock"
    }
  }

  mock_resource "aws_ecs_task_definition" {
    defaults = {
      arn = "arn:aws:ecs:ap-southeast-1:111111111111:task-definition/mock:1"
    }
  }

  mock_resource "aws_ecs_cluster" {
    defaults = {
      arn = "arn:aws:ecs:ap-southeast-1:111111111111:cluster/mock"
      id  = "arn:aws:ecs:ap-southeast-1:111111111111:cluster/mock"
    }
  }
}

variables {
  environment                 = "dev"
  region                      = "ap-southeast-1"
  vpc_cidr                    = "10.20.0.0/16"
  availability_zones          = ["ap-southeast-1a", "ap-southeast-1b"]
  public_subnet_cidrs         = ["10.20.0.0/24", "10.20.1.0/24"]
  private_subnet_cidrs        = ["10.20.10.0/24", "10.20.11.0/24"]
  single_nat_gateway          = true
  container_image             = "111111111111.dkr.ecr.ap-southeast-1.amazonaws.com/gioitruyen-dev/api@sha256:0000000000000000000000000000000000000000000000000000000000000000"
  log_retention_days          = 30
  secret_recovery_window_days = 7
}

run "dev_isolation_plan" {
  command = plan

  assert {
    condition     = aws_vpc.this.cidr_block == "10.20.0.0/16"
    error_message = "Dev must retain its dedicated VPC CIDR."
  }

  assert {
    condition     = length(aws_subnet.private) == 2
    error_message = "Runtime must span two private availability zones."
  }

  assert {
    condition = alltrue([
      for subnet in values(aws_subnet.private) :
      subnet.map_public_ip_on_launch == false
    ])
    error_message = "Private runtime subnets must never assign public IPs."
  }

  assert {
    condition     = length(aws_nat_gateway.this) == 1
    error_message = "Cost-sensitive dev must use one NAT gateway."
  }

  assert {
    condition = alltrue([
      for repository in values(aws_ecr_repository.runtime) :
      repository.image_tag_mutability == "IMMUTABLE"
    ])
    error_message = "Runtime image tags must be immutable."
  }

  assert {
    condition = (
      jsondecode(aws_ecs_task_definition.api.container_definitions)[0]
      .readonlyRootFilesystem == true
    )
    error_message = "The API container filesystem must be read-only."
  }

  assert {
    condition = (
      aws_ecs_service.api.network_configuration[0].assign_public_ip
      == false
    )
    error_message = "ECS services must not receive public IPs."
  }

  assert {
    condition     = length(aws_iam_role.runtime) == 4
    error_message = "Web, admin, API and worker require separate identities."
  }

  assert {
    condition = alltrue([
      for secret in values(aws_secretsmanager_secret.runtime) :
      secret.recovery_window_in_days >= 7
    ])
    error_message = "Managed secret references need recovery protection."
  }
}

run "staging_parity_plan" {
  command = plan

  variables {
    environment                 = "staging"
    vpc_cidr                    = "10.30.0.0/16"
    public_subnet_cidrs         = ["10.30.0.0/24", "10.30.1.0/24"]
    private_subnet_cidrs        = ["10.30.10.0/24", "10.30.11.0/24"]
    single_nat_gateway          = false
    container_image             = "222222222222.dkr.ecr.ap-southeast-1.amazonaws.com/gioitruyen-staging/api@sha256:0000000000000000000000000000000000000000000000000000000000000000"
    log_retention_days          = 90
    secret_recovery_window_days = 30
  }

  assert {
    condition     = aws_vpc.this.cidr_block == "10.30.0.0/16"
    error_message = "Staging must use a CIDR isolated from dev."
  }

  assert {
    condition     = length(aws_nat_gateway.this) == 2
    error_message = "Staging must retain one NAT gateway per availability zone."
  }

  assert {
    condition = (
      aws_cloudwatch_log_group.application["api"].retention_in_days
      == 90
    )
    error_message = "Staging must retain application logs for 90 days."
  }
}
