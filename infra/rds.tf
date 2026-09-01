resource "aws_db_subnet_group" "db_subnet_group" {
  name       = "payments-db-subnet-group"
  subnet_ids = [aws_subnet.public_a.id, aws_subnet.public_b.id]

  tags = {
    Name = "My RDS DB subnet group"
  }
}

resource "aws_db_instance" "payments_db" {
  identifier_prefix = "payments-db"
  allocated_storage = 20
  storage_type      = "gp2"
  engine            = "postgres"
  engine_version    = "17.11"
  instance_class    = "db.t3.micro"
  db_name           = "payments"

  vpc_security_group_ids = [aws_security_group.rds_sg.id]
  db_subnet_group_name   = aws_db_subnet_group.db_subnet_group.name
  publicly_accessible    = false
  skip_final_snapshot    = true
  username               = "payments"
  password               = var.db_password
}