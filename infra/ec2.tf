resource "aws_key_pair" "ec2_deployer_key" {
  key_name   = "deployer-key"
  public_key = file("C:/Users/adela/.ssh/payments-aws.pub")
}

data "aws_ami" "amazon_linux" {
  most_recent = true
  owners      = ["amazon"]

  filter {
    name   = "name"
    values = ["al2023-ami-*-x86_64"]

  }
}

resource "aws_instance" "ec2_instance" {
  ami                         = data.aws_ami.amazon_linux.id
  instance_type               = "t3.micro"
  key_name                    = aws_key_pair.ec2_deployer_key.key_name
  associate_public_ip_address = true
  subnet_id                   = aws_subnet.public_a.id
  vpc_security_group_ids      = [aws_security_group.ec2_sg.id]

  root_block_device {
    volume_size = 20
    volume_type = "gp3"
  }

  tags = {
    Name = "ec2--payment"
  }

  lifecycle {
    prevent_destroy = true
    ignore_changes  = [ami]
  }
}

