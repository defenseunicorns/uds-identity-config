/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor.ClientOwnershipPolicy;
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

import static com.defenseunicorns.uds.keycloak.plugin.UDSConstants.ATTRIBUTE_CREATED_BY;
import static com.defenseunicorns.uds.keycloak.plugin.UDSConstants.UDS_REALM;

/**
 * Enforces client ownership for enrolled callers in the {@code uds} realm. The two UDS
 * management clients and OpenTofu are enrolled by default; edit the ownership-enforced clients list in
 * the UDS client profile executor to change enrollment.
 * Ownership requires {@code created-by} to match the authenticated caller's client ID;
 * an absent stamp does not grant access or cause automatic adoption.
 * <p>
 * Both filter phases are needed:
 * <ul>
 *   <li>The request filter checks ownership before an endpoint executes. For example,
 *       {@code POST /admin/realms/uds/clients/{id}/roles} cannot add a role to another
 *       owner's client. Client roles are also protected through
 *       {@code DELETE /admin/realms/uds/roles-by-id/{role-id}}.</li>
 *   <li>The response filter masks sensitive fields on non-owned client representations.
 *       For example, {@code GET /admin/realms/uds/clients} keeps all entries but preserves
 *       credentials only for the caller's own clients.</li>
 * </ul>
 * Client CRUD remains covered by the client policy. Native Keycloak authorization still
 * applies. Both filter phases follow the enabled client policies referencing the UDS executor.
 * Disabling or removing those policies, or detaching their profiles, disables filtering.
 */
@Provider
public class ClientOwnershipFilter implements ContainerRequestFilter, ContainerResponseFilter {
    @Context
    KeycloakSession session;

    @Override
    public void filter(ContainerRequestContext request) {
        // Segments are zero-based: /admin/realms/uds/{resource}/{id}/... puts resource at 3 and ID at 4.
        var path = request.getUriInfo().getPathSegments();
        // Match client subresources and roles-by-id only in uds. For example, /clients/{uuid}/roles
        // is protected here; /clients/{uuid} uses client-policy CRUD checks, and other realms are skipped.
        if (!isOwnershipResource(path)) {
            return;
        }

        String owner = getOwnerClientId();
        if (owner == null) {
            return;
        }

        ClientModel client;
        if ("roles-by-id".equals(path.get(3).getPath())) {
            // /admin/realms/uds/roles-by-id/{role-uuid}, including suffixes such as /composites,
            // contains a role UUID, not a client UUID. Resolve its containing client so this alternate
            // route cannot bypass the ownership enforced on /clients/{client-uuid}/roles/{role-name}.
            RoleModel role = session.getContext().getRealm().getRoleById(path.get(4).getPath());
            // Client roles inherit ownership; realm roles and missing roles use native authorization/errors.
            if (role == null || !(role.getContainer() instanceof ClientModel roleClient)) {
                return;
            }
            client = roleClient;
        } else {
            // /admin/realms/uds/clients/{client-uuid}/roles/{role-name} or /protocol-mappers/models:
            // segment 4 is the client's internal UUID, not its readable clientId. Every subresource
            // under that client inherits the same ownership check, regardless of HTTP method.
            client = session.clients().getClientById(session.getContext().getRealm(), path.get(4).getPath());
        }
        if (client == null || !owner.equals(
                client.getAttribute(ATTRIBUTE_CREATED_BY))) {
            throw new ForbiddenException();
        }
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        String owner = getOwnerClientId();
        if (owner != null && response.getEntity() instanceof Stream<?> clients) {
            response.setEntity(clients.map(value -> {
                if (value instanceof ClientRepresentation client
                        && (client.getAttributes() == null || !owner.equals(
                                client.getAttributes().get(ATTRIBUTE_CREATED_BY)))) {
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

    /** Checks enrollment only in UDS executor profiles attached to an enabled client policy. */
    boolean isOwnershipEnforcementEnabled(String clientId) {
        return ClientOwnershipPolicy.isManagedClient(session, clientId);
    }

    private String getOwnerClientId() {
        return AdminRoot.getRealmAdminAuth(session)
                .filter(auth -> UDS_REALM.equals(session.getContext().getRealm().getName()))
                .map(auth -> auth.getClient())
                .map(ClientModel::getClientId)
                .filter(this::isOwnershipEnforcementEnabled)
                .orElse(null);
    }
}
