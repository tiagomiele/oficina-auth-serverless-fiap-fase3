variable "aws_region" {
  description = "Região AWS Academy."
  type        = string
  default     = "us-west-2"
}

variable "project_name" {
  description = "Nome base dos recursos."
  type        = string
  default     = "oficina-auth"
}

variable "environment" {
  description = "Ambiente lógico."
  type        = string

  validation {
    condition     = contains(["homolog", "production"], var.environment)
    error_message = "environment deve ser homolog ou production."
  }
}

variable "lab_role_arn" {
  description = "ARN da LabRole existente no AWS Academy."
  type        = string

  validation {
    condition     = can(regex("^arn:aws:iam::[0-9]{12}:role/LabRole$", var.lab_role_arn))
    error_message = "lab_role_arn deve apontar para arn:aws:iam::<conta>:role/LabRole."
  }
}

variable "lambda_security_group_id" {
  description = "Security group do EKS já autorizado no RDS e reutilizado pela Lambda."
  type        = string
}

variable "private_subnet_ids" {
  description = "Subnets privadas usadas pelas Lambdas."
  type        = list(string)

  validation {
    condition     = length(var.private_subnet_ids) >= 2
    error_message = "Informe ao menos duas subnets privadas."
  }
}

variable "db_url" {
  description = "URL JDBC PostgreSQL com sslmode=require."
  type        = string
  sensitive   = true
}

variable "db_user" {
  description = "Usuário PostgreSQL de leitura para autenticação."
  type        = string
  sensitive   = true
}

variable "db_password" {
  description = "Senha PostgreSQL do usuário de autenticação."
  type        = string
  sensitive   = true
}

variable "jwt_private_key" {
  description = "Chave privada RSA PKCS#8 em PEM usada apenas pela Lambda de login."
  type        = string
  sensitive   = true
}

variable "jwt_public_key" {
  description = "Chave pública RSA em PEM usada pelo authorizer e pelo backend."
  type        = string
}

variable "jwt_issuer" {
  description = "Emissor obrigatório do JWT."
  type        = string
  default     = "oficina-auth-serverless"
}

variable "jwt_audience" {
  description = "Audiência obrigatória do JWT."
  type        = string
  default     = "oficina-backend"
}

variable "jwt_ttl_seconds" {
  description = "Validade do token do cliente."
  type        = number
  default     = 900

  validation {
    condition     = var.jwt_ttl_seconds >= 300 && var.jwt_ttl_seconds <= 3600
    error_message = "jwt_ttl_seconds deve estar entre 300 e 3600 segundos."
  }
}

variable "lambda_package_path" {
  description = "JAR empacotado pelo Maven antes do plan."
  type        = string
  default     = "target/oficina-auth.jar"
}

variable "backend_base_url" {
  description = "URL pública do LoadBalancer do backend, sem barra final. Nulo cria somente /auth/cpf."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.backend_base_url == null || can(regex("^https?://[^/]+$", var.backend_base_url))
    error_message = "backend_base_url deve ser uma URL HTTP(S) sem barra final."
  }
}
