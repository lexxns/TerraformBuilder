variable "api_domain" {
  description = "Domain on which the Lambda will be made available (e.g. api.example.com)"
  type        = string
}

variable "function_name" {
  type        = string
}

variable "comment_prefix" {
  description = "This will be included in comments for resources that are created"
  type        = string
  default     = "Lambda API: "
}

variable "function_s3_bucket" {
  description = "When provided, the zipfile is retrieved from an S3 bucket by this name instead"
  type        = string
  default     = ""
}

variable "function_zipfile" {
  description = "Path to a ZIP file that will be installed as the Lambda function"
  type        = string
} 