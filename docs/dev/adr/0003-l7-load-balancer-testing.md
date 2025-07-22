# 3. L7 Load Balancer Testing

Date: 2025-07-15

## Status

Accepted

## Terms used in this document

An L7 Load Balancer capable of terminating TLS is a synonym for AWS ALB, GCP ALB and Azure Application Gateway

## Context

During the implementation of the AWS ALB support, it has been decided to test the entire setup using the local k3d cluster with HAProxy.

The primary reason for this decision is to speed up development cycles and avoid generating high costs on the infrastructure side.

HAProxy has been chosen as it can easily simulate forwarding Client Certificates in different formats (PEM, AWS ALB etc).

## Alternatives considered

* Using IaC in UDS Core
  * Rejected as the development cycle took more than 3 hours to spin everything up and introduce a simple change.
  * Rejected because of the CI costs.
* Using NGINX as a local proxy
  * Rejected as every modification of the configuration will require changes in https://github.com/defenseunicorns/uds-k3d

## Decision

Test ALB and WAF scenarios using local environment instead of IaC.

## Consequences

The HAProxy temporarily disables the k3d Load Balancer and during the tests, the API Server is not accessible from outside the cluster.
