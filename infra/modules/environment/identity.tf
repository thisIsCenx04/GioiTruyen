data "aws_iam_policy_document" "ecs_task_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values = [
        "arn:${data.aws_partition.current.partition}:ecs:${var.region}:${data.aws_caller_identity.current.account_id}:*"
      ]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "runtime" {
  for_each = local.runtime_services

  name               = "${local.name}-${each.key}"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json

  tags = merge(local.common_tags, {
    ServiceIdentity = each.key
  })
}

resource "aws_iam_role" "execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_task_assume.json

  tags = merge(local.common_tags, {
    ServiceIdentity = "ecs-execution"
  })
}

resource "aws_iam_role_policy_attachment" "execution_base" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

data "aws_iam_policy_document" "execution_secrets" {
  statement {
    sid       = "ReadEnvironmentSecrets"
    effect    = "Allow"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = values(aws_secretsmanager_secret.runtime)[*].arn
  }

  statement {
    sid       = "DecryptEnvironmentSecrets"
    effect    = "Allow"
    actions   = ["kms:Decrypt"]
    resources = [aws_kms_key.secrets.arn]
  }
}

resource "aws_iam_role_policy" "execution_secrets" {
  name   = "read-environment-secret-references"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution_secrets.json
}

resource "aws_iam_openid_connect_provider" "github" {
  url             = "https://token.actions.githubusercontent.com"
  client_id_list  = ["sts.amazonaws.com"]
  thumbprint_list = []

  tags = local.common_tags
}

data "aws_iam_policy_document" "github_deploy_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [aws_iam_openid_connect_provider.github.arn]
    }

    condition {
      test     = "StringEquals"
      variable = "token.actions.githubusercontent.com:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringLike"
      variable = "token.actions.githubusercontent.com:sub"
      values = [
        "repo:${var.github_repository}:environment:${var.environment}"
      ]
    }
  }
}

resource "aws_iam_role" "github_deploy" {
  name                 = "${local.name}-github-deploy"
  assume_role_policy   = data.aws_iam_policy_document.github_deploy_assume.json
  max_session_duration = 3600

  tags = merge(local.common_tags, {
    ServiceIdentity = "ci-deploy"
  })
}

data "aws_iam_policy_document" "github_deploy" {
  statement {
    sid    = "PushImmutableImages"
    effect = "Allow"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:CompleteLayerUpload",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart"
    ]
    resources = values(aws_ecr_repository.runtime)[*].arn
  }

  statement {
    sid       = "GetEcrAuthorizationToken"
    effect    = "Allow"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid    = "DeployEnvironmentServices"
    effect = "Allow"
    actions = [
      "ecs:DescribeServices",
      "ecs:DescribeTaskDefinition",
      "ecs:RegisterTaskDefinition",
      "ecs:UpdateService"
    ]
    resources = ["*"]
  }

  statement {
    sid     = "PassOnlyEnvironmentRuntimeRoles"
    effect  = "Allow"
    actions = ["iam:PassRole"]
    resources = concat(
      values(aws_iam_role.runtime)[*].arn,
      [aws_iam_role.execution.arn]
    )
  }
}

resource "aws_iam_role_policy" "github_deploy" {
  name   = "deploy-immutable-environment-artifacts"
  role   = aws_iam_role.github_deploy.id
  policy = data.aws_iam_policy_document.github_deploy.json
}
