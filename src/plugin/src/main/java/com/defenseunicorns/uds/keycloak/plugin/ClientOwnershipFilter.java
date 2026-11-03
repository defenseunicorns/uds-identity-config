/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.UDSClientPolicyPermissionsExecutor;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.utils.StripSecretsUtils;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.representations.idm.ClientRepresentation;

import java.util.stream.Stream;

/** Enforces client ownership on client sub-resources not covered by client-policy events. */
@Provider
public class ClientOwnershipFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private final boolean enabled;

    @Context
    KeycloakSession session;

    public ClientOwnershipFilter() {
        this(System.getenv("UDS_CLIENT_OWNERSHIP_FILTER_ENABLED"));
    }

    ClientOwnershipFilter(String enabled) {
        this.enabled = !"false".equalsIgnoreCase(enabled);
    }

    @Override
    public void filter(ContainerRequestContext request) {
        if (!enabled) {
            return;
        }
        var path = request.getUriInfo().getPathSegments();
        // /admin/realms/{realm}/clients/{uuid}: leave CRUD and its errors to the policy.
        if (path.size() <= 5 || !"admin".equals(path.get(0).getPath())
                || !"realms".equals(path.get(1).getPath()) || !"clients".equals(path.get(3).getPath())) {
            return;
        }

        String owner = getOwnerClientId();
        if (owner == null) {
            return;
        }

        ClientModel client = session.clients().getClientById(session.getContext().getRealm(), path.get(4).getPath());
        if (client == null || !owner.equals(
                client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY))) {
            throw new ForbiddenException();
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        if (!enabled) {
            return;
        }
        // Apply ownership masking to client lists, which do not emit client-policy events.
        String owner = getOwnerClientId();
        if (owner != null && response.getEntity() instanceof Stream<?> clients) {
            response.setEntity(clients.map(value -> {
                if (value instanceof ClientRepresentation client
                        && (client.getAttributes() == null || !owner.equals(
                                client.getAttributes().get(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY)))) {
                    StripSecretsUtils.stripClient(client);
                }
                return value;
            }));
        }
    }

    private String getOwnerClientId() {
        // Post-matching: AdminRoot has already authenticated and verified this token.
        return AdminRoot.getRealmAdminAuth(session)
                .map(auth -> UDSClientPolicyPermissionsExecutor.getOwnerClientId(auth.getClient(), auth.getUser()))
                .orElse(null);
    }
}
