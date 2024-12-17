terraform {
  required_providers {
    keycloak = {
      source  = "mrparkers/keycloak"
      version = "4.4.0"
    }
    random = {
      source = "hashicorp/random"
    }
  }
}