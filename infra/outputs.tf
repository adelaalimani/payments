output "rds_endpoint" {
  value = aws_db_instance.payments_db.endpoint
}