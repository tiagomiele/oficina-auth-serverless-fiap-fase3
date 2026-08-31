data "aws_caller_identity" "current" {}

data "aws_partition" "current" {}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project     = var.project_name
      Environment = var.environment
      ManagedBy   = "Terraform"
      Component   = "authentication"
    }
  }
}

provider "aws" {
  alias  = "ses_identity"
  region = var.aws_region
}
