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
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"kms:*\",\"Resource\":\"*\",\"Principal\":{\"AWS\":\"arn:aws:iam::111111111111:root\"}}]}"
    }
  }
}

variables {
  account_id        = "111111111111"
  region            = "ap-southeast-1"
  environment       = "dev"
  state_bucket_name = "gioitruyen-dev-state-example"
}

run "protected_remote_state_plan" {
  command = plan

  assert {
    condition     = aws_s3_bucket.state.force_destroy == false
    error_message = "Remote state must be protected from force deletion."
  }

  assert {
    condition     = aws_s3_bucket.state.object_lock_enabled == true
    error_message = "Remote state must enable object lock at bucket creation."
  }

  assert {
    condition = (
      aws_s3_bucket_versioning.state.versioning_configuration[0].status
      == "Enabled"
    )
    error_message = "Remote state must retain version history."
  }

  assert {
    condition = (
      aws_s3_bucket_public_access_block.state.block_public_policy
      && aws_s3_bucket_public_access_block.state.restrict_public_buckets
    )
    error_message = "Remote state must reject all public access."
  }

  assert {
    condition     = aws_kms_key.state.enable_key_rotation == true
    error_message = "The remote-state KMS key must rotate."
  }
}
