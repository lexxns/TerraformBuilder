variable "bucket_name" {
  description = "Example bucket name which must be passed in"
}

variable "name_prefix" {
  description = "Name prefix to use for objects that need to be created (only lowercase alphanumeric characters and hyphens allowed, for S3 bucket name compatibility)"
  default     = "my-name-prefix-"
}
