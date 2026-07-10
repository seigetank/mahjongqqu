terraform {
  required_version = ">= 1.8.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "~> 6.0"
    }
  }
}

variable "project_id" {
  type = string
}

variable "region" {
  type    = string
  default = "asia-northeast3"
}

provider "google" {
  project = var.project_id
  region  = var.region
}

resource "google_cloud_run_v2_service" "server" {
  name     = "mahjongqqu-server"
  location = var.region

  template {
    containers {
      image = "gcr.io/${var.project_id}/mahjongqqu-server:latest"

      env {
        name  = "REQUIRE_PLAY_INTEGRITY"
        value = "true"
      }
    }
  }
}

resource "google_cloud_scheduler_job" "season_rollover" {
  name      = "mahjongqqu-season-rollover"
  region    = var.region
  schedule  = "0 0 * * MON"
  time_zone = "Asia/Seoul"

  http_target {
    http_method = "POST"
    uri         = "${google_cloud_run_v2_service.server.uri}/v1/admin/seasons/rollover"
  }
}
