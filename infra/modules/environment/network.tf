resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.common_tags, {
    Name = local.name
  })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name}-igw"
  })
}

resource "aws_subnet" "public" {
  for_each = local.public_subnets

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.key
  cidr_block              = each.value.cidr
  map_public_ip_on_launch = false

  tags = merge(local.common_tags, {
    Name = "${local.name}-public-${each.value.index + 1}"
    Tier = "public"
  })
}

resource "aws_subnet" "private" {
  for_each = local.private_subnets

  vpc_id                  = aws_vpc.this.id
  availability_zone       = each.key
  cidr_block              = each.value.cidr
  map_public_ip_on_launch = false

  tags = merge(local.common_tags, {
    Name = "${local.name}-private-${each.value.index + 1}"
    Tier = "private"
  })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name}-public"
  })
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_eip" "nat" {
  for_each = toset(local.nat_gateway_zones)

  domain = "vpc"

  tags = merge(local.common_tags, {
    Name = "${local.name}-nat-${each.key}"
  })

  depends_on = [aws_internet_gateway.this]
}

resource "aws_nat_gateway" "this" {
  for_each = toset(local.nat_gateway_zones)

  allocation_id = aws_eip.nat[each.key].id
  subnet_id     = aws_subnet.public[each.key].id

  tags = merge(local.common_tags, {
    Name = "${local.name}-nat-${each.key}"
  })
}

resource "aws_route_table" "private" {
  for_each = local.private_subnets

  vpc_id = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name}-private-${each.value.index + 1}"
  })
}

resource "aws_route" "private_egress" {
  for_each = local.private_subnets

  route_table_id         = aws_route_table.private[each.key].id
  destination_cidr_block = "0.0.0.0/0"
  nat_gateway_id = aws_nat_gateway.this[
    var.single_nat_gateway ? var.availability_zones[0] : each.key
  ].id
}

resource "aws_route_table_association" "private" {
  for_each = aws_subnet.private

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private[each.key].id
}

resource "aws_security_group" "runtime" {
  name_prefix = "${local.name}-runtime-"
  description = "Private ECS runtime; ingress is added by edge and data modules"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name}-runtime"
  })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_egress_rule" "runtime_dns_udp" {
  security_group_id = aws_security_group.runtime.id
  description       = "DNS through the VPC resolver"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 53
  to_port           = 53
  ip_protocol       = "udp"
}

resource "aws_vpc_security_group_egress_rule" "runtime_dns_tcp" {
  security_group_id = aws_security_group.runtime.id
  description       = "TCP DNS fallback through the VPC resolver"
  cidr_ipv4         = var.vpc_cidr
  from_port         = 53
  to_port           = 53
  ip_protocol       = "tcp"
}

resource "aws_security_group" "endpoints" {
  name_prefix = "${local.name}-endpoints-"
  description = "Private AWS API endpoints reachable only from runtime tasks"
  vpc_id      = aws_vpc.this.id

  tags = merge(local.common_tags, {
    Name = "${local.name}-endpoints"
  })

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_vpc_security_group_ingress_rule" "endpoints_from_runtime" {
  security_group_id            = aws_security_group.endpoints.id
  referenced_security_group_id = aws_security_group.runtime.id
  description                  = "TLS from private runtime tasks"
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "runtime_to_endpoints" {
  security_group_id            = aws_security_group.runtime.id
  referenced_security_group_id = aws_security_group.endpoints.id
  description                  = "TLS only to private AWS service endpoints"
  from_port                    = 443
  to_port                      = 443
  ip_protocol                  = "tcp"
}

resource "aws_vpc_endpoint" "interface" {
  for_each = toset([
    "ecr.api",
    "ecr.dkr",
    "logs",
    "secretsmanager"
  ])

  vpc_id              = aws_vpc.this.id
  service_name        = "com.amazonaws.${var.region}.${each.key}"
  vpc_endpoint_type   = "Interface"
  private_dns_enabled = true
  subnet_ids          = values(aws_subnet.private)[*].id
  security_group_ids  = [aws_security_group.endpoints.id]

  tags = merge(local.common_tags, {
    Name = "${local.name}-${replace(each.key, ".", "-")}"
  })
}

resource "aws_vpc_endpoint" "s3" {
  vpc_id            = aws_vpc.this.id
  service_name      = "com.amazonaws.${var.region}.s3"
  vpc_endpoint_type = "Gateway"
  route_table_ids   = values(aws_route_table.private)[*].id

  tags = merge(local.common_tags, {
    Name = "${local.name}-s3"
  })
}

data "aws_prefix_list" "s3" {
  name = "com.amazonaws.${var.region}.s3"
}

resource "aws_vpc_security_group_egress_rule" "runtime_to_s3" {
  security_group_id = aws_security_group.runtime.id
  prefix_list_id    = data.aws_prefix_list.s3.id
  description       = "TLS to the regional S3 gateway endpoint for ECR layers"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_kms_key" "logs" {
  description             = "Encrypt ${local.name} application and VPC flow logs"
  deletion_window_in_days = 30
  enable_key_rotation     = true
  policy                  = data.aws_iam_policy_document.logs_kms.json

  tags = local.common_tags
}

data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

data "aws_iam_policy_document" "logs_kms" {
  statement {
    sid    = "AccountAdministration"
    effect = "Allow"
    actions = [
      "kms:*"
    ]
    resources = ["*"]

    principals {
      type = "AWS"
      identifiers = [
        "arn:${data.aws_partition.current.partition}:iam::${data.aws_caller_identity.current.account_id}:root"
      ]
    }
  }

  statement {
    sid    = "CloudWatchLogsEncryption"
    effect = "Allow"
    actions = [
      "kms:Decrypt",
      "kms:DescribeKey",
      "kms:Encrypt",
      "kms:GenerateDataKey*",
      "kms:ReEncrypt*"
    ]
    resources = ["*"]

    principals {
      type        = "Service"
      identifiers = ["logs.${var.region}.amazonaws.com"]
    }

    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values = [
        "arn:${data.aws_partition.current.partition}:logs:${var.region}:${data.aws_caller_identity.current.account_id}:log-group:/${var.project_name}/${var.environment}/*"
      ]
    }
  }
}

resource "aws_kms_alias" "logs" {
  name          = "alias/${local.name}-logs"
  target_key_id = aws_kms_key.logs.key_id
}

resource "aws_cloudwatch_log_group" "flow" {
  name              = "/${var.project_name}/${var.environment}/vpc-flow"
  retention_in_days = var.log_retention_days
  kms_key_id        = aws_kms_key.logs.arn

  tags = local.common_tags
}

data "aws_iam_policy_document" "flow_assume" {
  statement {
    effect  = "Allow"
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["vpc-flow-logs.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "flow" {
  name               = "${local.name}-vpc-flow"
  assume_role_policy = data.aws_iam_policy_document.flow_assume.json

  tags = local.common_tags
}

data "aws_iam_policy_document" "flow_write" {
  statement {
    effect = "Allow"
    actions = [
      "logs:CreateLogStream",
      "logs:DescribeLogGroups",
      "logs:DescribeLogStreams",
      "logs:PutLogEvents"
    ]
    resources = ["${aws_cloudwatch_log_group.flow.arn}:*"]
  }
}

resource "aws_iam_role_policy" "flow_write" {
  name   = "write-vpc-flow-logs"
  role   = aws_iam_role.flow.id
  policy = data.aws_iam_policy_document.flow_write.json
}

resource "aws_flow_log" "this" {
  iam_role_arn    = aws_iam_role.flow.arn
  log_destination = aws_cloudwatch_log_group.flow.arn
  traffic_type    = "ALL"
  vpc_id          = aws_vpc.this.id

  tags = local.common_tags
}
