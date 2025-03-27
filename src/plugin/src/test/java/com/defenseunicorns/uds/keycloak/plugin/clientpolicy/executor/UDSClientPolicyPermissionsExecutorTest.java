/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.models.ClientModel;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.services.clientpolicy.context.ClientCRUDContext;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UDSClientPolicyPermissionsExecutorTest extends TestCase {

    private final UDSClientPolicyPermissionsExecutor executor = new UDSClientPolicyPermissionsExecutor();
    @Mock
    private ClientModel client;

    @Test
    public void shouldReportAsOwnedByUDS() {
        // given
        when(client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_UDS_OPERATOR))
                .thenReturn(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_UDS_OPERATOR_VALUE);

        // when
        boolean result = executor.isOwnedByUDSOperator(client);

        // then
        assertTrue(result);
    }

    @Test
    public void shouldNotReportAsOwnedByUDS() {
        // given
        when(client.getAttribute(UDSClientPolicyPermissionsExecutor.ATTRIBUTE_UDS_OPERATOR)).thenReturn(null);

        // when
        boolean result = executor.isOwnedByUDSOperator(client);

        // then
        assertFalse(result);
    }

    @Test
    public void shouldNotThrowExceptionWhenAuthenticatedClientIsNull() {
        // given
        ClientCRUDContext clientCRUDContext = mock(ClientCRUDContext.class);

        // when
        String result = executor.getAuthenticatedClientId(clientCRUDContext);

        // then
        assertNull(result);
    }

    @Test
    public void shouldEnforceFullScopeDisabled() {
        // given
        ClientRepresentation rep = new ClientRepresentation();
        rep.setFullScopeAllowed(true);

        // when
        executor.enforceClientSettings(rep);

        // then
        assertFalse(rep.isFullScopeAllowed());
    }

}