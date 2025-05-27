# 2. Switching Required SSL to None

Date: 2025-05-22

## Status

Accepted

## Context

As highlighted in https://github.com/defenseunicorns/uds-identity-config/issues/484, once we moved to Ambient, the Waypoint might get scheduled in one of the "Shared Address Space" (see RFC6598). AWS treats these addresses as a private pool, whereas Keycloak doesn't. This leads to HTTP 403 errors.

Setting this option will not affect the UDS Core (and Keycloak) security posture as all the ports (except JGroups encrypted TLS port) are already configured to STRICT mTLS and locked down by Istio and Network Policies.

## Decision

Switch Required SSL to None
