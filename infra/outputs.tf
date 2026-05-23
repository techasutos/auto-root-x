output "cluster_name" {
  description = "Name of the GKE cluster"
  value       = google_container_cluster.primary.name
}

output "cluster_endpoint" {
  description = "GKE control plane endpoint"
  value       = google_container_cluster.primary.endpoint
}


output "network_name" {
  description = "VPC network name"
  value       = google_compute_network.vpc.name
}

output "subnetwork_name" {
  description = "Subnetwork name"
  value       = google_compute_subnetwork.subnet.name
}

output "gke_node_service_account" {
  description = "Service account used by GKE nodes"
  value       = google_service_account.gke_nodes.email
}

output "workload_identity_pool" {
  description = "Workload Identity pool for Kubernetes service accounts"
  value       = "${var.project_id}.svc.id.goog"
}

output "backend_workload_service_account" {
  description = "Google service account used by the backend workload via Workload Identity"
  value       = google_service_account.backend_workload.email
}
