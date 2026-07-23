output "bucket" {
  description = "Remote state bucket for backend.hcl."
  value       = aws_s3_bucket.state.id
}

output "kms_key_arn" {
  description = "KMS key ARN for backend.hcl."
  value       = aws_kms_key.state.arn
}
