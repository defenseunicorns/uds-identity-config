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
import jakarta.ws.rs.core.PathSegment;
import jakarta.ws.rs.ext.Provider;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RoleModel;
import org.keycloak.models.utils.StripSecretsUtils;
import org.keycloak.services.resources.admin.AdminRoot;
import org.keycloak.representations.idm.ClientRepresentation;

import java.util.List;
import java.util.stream.Stream;

/** Enforces client ownership on client sub-resources not covered by client-policy events. */
@Provider
public class ClientOwnershipFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static final String UDS_REALM = "uds";
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
        // This switch disables only the filters; the client policy remains active.
        if (!enabled) {
            return;
        }
        var path = request.getUriInfo().getPathSegments();
        if (!isOwnershipResource(path)) {
            return;
        }

        String owner = getOwnerClientId();
        if (owner == null) {
            return;
        }

        ClientModel client;
        if ("roles-by-id".equals(path.get(3).getPath())) {
            RoleModel role = session.getContext().getRealm().getRoleById(path.get(4).getPath());
            // Client roles inherit ownership; realm roles and missing roles use native authorization/errors.
            if (role == null || !(role.getContainer() instanceof ClientModel roleClient)) {
                return;
            }
            client = roleClient;
        } else {
            client = session.clients().getClientById(session.getContext().getRealm(), path.get(4).getPath());
        }
        if (client == null || !owner.equals(
                client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_CREATED_BY))) {
            throw new ForbiddenException();
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // This switch disables only the filters; the client policy remains active.
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

    static boolean isOwnershipResource(List<PathSegment> path) {
        // Other realms are unaffected; client CRUD keeps the policy's existing errors.
        return path.size() > 4 && "admin".equals(path.get(0).getPath())
                && "realms".equals(path.get(1).getPath()) && UDS_REALM.equals(path.get(2).getPath())
                && (("clients".equals(path.get(3).getPath()) && path.size() > 5)
                    || "roles-by-id".equals(path.get(3).getPath()));
    }

    private String getOwnerClientId() {
        // No authenticated admin context means Keycloak handles authentication itself.
        return AdminRoot.getRealmAdminAuth(session)
                .filter(auth -> UDS_REALM.equals(session.getContext().getRealm().getName()))
                .filter(auth -> !UDSClientPolicyPermissionsExecutor.isRealmAdmin(session))
                .filter(auth -> UDSClientPolicyPermissionsExecutor.isManagedClient(auth.getClient(), auth.getUser()))
                .map(auth -> auth.getClient().getClientId())
                .orElse(null);
    }
}
