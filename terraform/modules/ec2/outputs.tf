output "public_ip"    { value = aws_eip.app.public_ip }
output "instance_id"  { value = aws_instance.app.id }
output "private_ip"   { value = aws_instance.app.private_ip }
