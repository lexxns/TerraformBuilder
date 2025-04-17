resource "aws_s3_bucket" "test-bucket" {
  bucket = "${var.name_prefix}${var.bucket_name}"
}