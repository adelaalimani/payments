variable "db_password" {
  description = "Master password for the RDS Postgres instance"
  type        = string
  sensitive   = true
}