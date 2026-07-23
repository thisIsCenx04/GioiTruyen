resource "aws_kms_key" "secrets" {
  description             = "Encrypt ${local.name} runtime secrets"
  deletion_window_in_days = 30
  enable_key_rotation     = true

  tags = local.common_tags
}

resource "aws_kms_alias" "secrets" {
  name          = "alias/${local.name}-secrets"
  target_key_id = aws_kms_key.secrets.key_id
}

resource "aws_secretsmanager_secret" "runtime" {
  for_each = toset(values(local.secret_environment_variables))

  name                    = "/${var.project_name}/${var.environment}/${each.key}"
  description             = "Managed reference for ${each.key}; value is populated out of band"
  kms_key_id              = aws_kms_key.secrets.arn
  recovery_window_in_days = var.secret_recovery_window_days

  tags = merge(local.common_tags, {
    SecretPurpose = each.key
  })
}
