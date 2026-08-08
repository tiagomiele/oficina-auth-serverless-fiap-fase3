output "api_base_url" {
  description = "URL base do API Gateway."
  value       = aws_apigatewayv2_api.main.api_endpoint
}

output "cpf_authentication_url" {
  description = "Endpoint POST de autenticação por CPF."
  value       = "${aws_apigatewayv2_api.main.api_endpoint}/auth/cpf"
}

output "login_lambda_name" {
  value = aws_lambda_function.login.function_name
}

output "authorizer_lambda_name" {
  value = aws_lambda_function.authorizer.function_name
}
