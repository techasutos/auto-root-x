variable "project_id" {
  description = "GCP project ID"
  type        = string
}

variable "region" {
  description = "GCP region"
  type        = string
  default     = "us-central1"
}


variable "cluster_name" {
  description = "GKE cluster name"
  type        = string
  default     = "autorootx-cluster"
}

variable "network_name" {
  description = "VPC network name"
  type        = string
  default     = "autorootx-vpc"
}

variable "subnet_name" {
  description = "Subnetwork name"
  type        = string
  default     = "autorootx-subnet"
}

variable "subnet_cidr" {
  description = "Subnetwork CIDR range"
  type        = string
  default     = "10.10.0.0/16"
}

variable "node_count" {
  description = "Number of nodes in default pool"
  type        = number
  default     = 1
}

variable "node_machine_type" {
  description = "Machine type for GKE nodes"
  type        = string
  default     = "e2-small"
}

variable "min_node_count" {
  description = "Minimum number of nodes for autoscaling"
  type        = number
  default     = 1
}

variable "max_node_count" {
  description = "Maximum number of nodes for autoscaling"
  type        = number
  default     = 2
}

variable "node_disk_size_gb" {
  description = "Node disk size in GB"
  type        = number
  default     = 30
}

variable "use_spot_nodes" {
  description = "Use spot VMs for node pool to reduce cost"
  type        = bool
  default     = true
}

variable "deletion_protection" {
  description = "Enable deletion protection on GKE cluster"
  type        = bool
  default     = false
}

variable "k8s_namespace" {
  description = "Kubernetes namespace used by the Helm release"
  type        = string
  default     = "autorootx"
}

variable "backend_ksa_name" {
  description = "Kubernetes service account name used by the backend workload"
  type        = string
  default     = "autorootx-backend"
}
