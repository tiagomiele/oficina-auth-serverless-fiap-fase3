locals {
  name = "${var.project_name}-${var.environment}"
}

resource "aws_cloudwatch_log_group" "login" {
  name              = "/aws/lambda/${local.name}-login"
  retention_in_days = 7
}

resource "aws_cloudwatch_log_group" "authorizer" {
  name              = "/aws/lambda/${local.name}-authorizer"
  retention_in_days = 7
}

resource "aws_lambda_function" "login" {
  function_name = "${local.name}-login"
  description   = "Autenticação de cliente por CPF"
  role          = var.lab_role_arn
  runtime       = "java21"
  architectures = ["arm64"]
  handler       = "br.com.oficina.auth.handler.CpfAuthenticationHandler::handleRequest"

  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  memory_size      = 512
  timeout          = 15

  vpc_config {
    subnet_ids         = var.private_subnet_ids
    security_group_ids = [var.lambda_security_group_id]
  }

  environment {
    variables = {
      DB_URL          = var.db_url
      DB_USER         = var.db_user
      DB_PASSWORD     = var.db_password
      JWT_PRIVATE_KEY = var.jwt_private_key
      JWT_PUBLIC_KEY  = var.jwt_public_key
      JWT_ISSUER      = var.jwt_issuer
      JWT_AUDIENCE    = var.jwt_audience
      JWT_TTL_SECONDS = tostring(var.jwt_ttl_seconds)
    }
  }

  logging_config {
    log_format            = "JSON"
    application_log_level = "INFO"
    system_log_level      = "WARN"
  }

  depends_on = [aws_cloudwatch_log_group.login]
}

resource "aws_lambda_function" "authorizer" {
  function_name = "${local.name}-authorizer"
  description   = "Validação de JWT para rotas protegidas"
  role          = var.lab_role_arn
  runtime       = "java21"
  architectures = ["arm64"]
  handler       = "br.com.oficina.auth.handler.JwtAuthorizerHandler::handleRequest"

  filename         = var.lambda_package_path
  source_code_hash = filebase64sha256(var.lambda_package_path)
  memory_size      = 384
  timeout          = 10

  environment {
    variables = {
      JWT_PUBLIC_KEY = var.jwt_public_key
      JWT_ISSUER     = var.jwt_issuer
      JWT_AUDIENCE   = var.jwt_audience
    }
  }

  logging_config {
    log_format            = "JSON"
    application_log_level = "INFO"
    system_log_level      = "WARN"
  }

  depends_on = [aws_cloudwatch_log_group.authorizer]
}
