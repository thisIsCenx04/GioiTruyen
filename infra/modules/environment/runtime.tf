resource "aws_ecr_repository" "runtime" {
  for_each = toset(["api", "web", "admin"])

  name                 = "${local.name}/${each.key}"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = false

  encryption_configuration {
    encryption_type = "KMS"
    kms_key         = aws_kms_key.secrets.arn
  }

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = merge(local.common_tags, {
    Runtime = each.key
  })
}

resource "aws_ecr_lifecycle_policy" "runtime" {
  for_each = aws_ecr_repository.runtime

  repository = each.value.name
  policy = jsonencode({
    rules = [
      {
        rulePriority = 1
        description  = "Expire untagged images after seven days"
        selection = {
          tagStatus   = "untagged"
          countType   = "sinceImagePushed"
          countUnit   = "days"
          countNumber = 7
        }
        action = {
          type = "expire"
        }
      }
    ]
  })
}

resource "aws_ecs_cluster" "this" {
  name = local.name

  setting {
    name  = "containerInsights"
    value = "enhanced"
  }

  tags = local.common_tags
}

resource "aws_ecs_cluster_capacity_providers" "this" {
  cluster_name = aws_ecs_cluster.this.name

  capacity_providers = ["FARGATE"]

  default_capacity_provider_strategy {
    capacity_provider = "FARGATE"
    base              = 1
    weight            = 1
  }
}

resource "aws_cloudwatch_log_group" "application" {
  for_each = toset(["api", "worker"])

  name              = "/${var.project_name}/${var.environment}/${each.key}"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.logs.arn

  tags = merge(local.common_tags, {
    Runtime = each.key
  })
}

locals {
  common_container_environment = [
    {
      name  = "APP_ENV"
      value = var.environment
    },
    {
      name  = "API_HOST"
      value = "0.0.0.0"
    },
    {
      name  = "LOG_FORMAT"
      value = "ecs"
    }
  ]

  container_secrets = [
    for environment_name, secret_name in local.secret_environment_variables : {
      name      = environment_name
      valueFrom = aws_secretsmanager_secret.runtime[secret_name].arn
    }
  ]
}

resource "aws_ecs_task_definition" "api" {
  family                   = "${local.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.runtime["api"].arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name                   = "api"
      image                  = var.container_image
      essential              = true
      readonlyRootFilesystem = true
      user                   = "65532:65532"
      stopTimeout            = 30
      portMappings = [
        {
          name          = "http"
          containerPort = 8080
          hostPort      = 8080
          protocol      = "tcp"
          appProtocol   = "http"
        }
      ]
      environment = local.common_container_environment
      secrets     = local.container_secrets
      linuxParameters = {
        initProcessEnabled = true
        capabilities = {
          drop = ["ALL"]
        }
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.application["api"].name
          awslogs-region        = var.region
          awslogs-stream-prefix = "api"
        }
      }
    }
  ])

  tags = local.common_tags
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "${local.name}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 512
  memory                   = 1024
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.runtime["worker"].arn

  runtime_platform {
    cpu_architecture        = "X86_64"
    operating_system_family = "LINUX"
  }

  container_definitions = jsonencode([
    {
      name                   = "worker"
      image                  = var.container_image
      essential              = true
      readonlyRootFilesystem = true
      user                   = "65532:65532"
      stopTimeout            = 30
      environment = concat(local.common_container_environment, [
        {
          name  = "OUTBOX_WORKER_ENABLED"
          value = "true"
        }
      ])
      secrets = local.container_secrets
      linuxParameters = {
        initProcessEnabled = true
        capabilities = {
          drop = ["ALL"]
        }
      }
      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.application["worker"].name
          awslogs-region        = var.region
          awslogs-stream-prefix = "worker"
        }
      }
    }
  ])

  tags = local.common_tags
}

resource "aws_ecs_service" "api" {
  name            = "api"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.api.arn
  desired_count   = var.api_desired_count

  enable_execute_command             = false
  wait_for_steady_state              = false
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    assign_public_ip = false
    security_groups  = [aws_security_group.runtime.id]
    subnets          = values(aws_subnet.private)[*].id
  }

  lifecycle {
    ignore_changes = [desired_count]
  }

  tags = local.common_tags
}

resource "aws_ecs_service" "worker" {
  name            = "worker"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = var.worker_desired_count

  enable_execute_command             = false
  wait_for_steady_state              = false
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    assign_public_ip = false
    security_groups  = [aws_security_group.runtime.id]
    subnets          = values(aws_subnet.private)[*].id
  }

  lifecycle {
    ignore_changes = [desired_count]
  }

  tags = local.common_tags
}
