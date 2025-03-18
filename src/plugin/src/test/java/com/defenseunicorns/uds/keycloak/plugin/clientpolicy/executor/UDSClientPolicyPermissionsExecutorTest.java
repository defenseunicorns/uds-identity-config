/*
 * Copyright 2025 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import junit.framework.TestCase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.keycloak.models.ClientModel;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class UDSClientPolicyPermissionsExecutorTest extends TestCase {

    @Mock
    private ClientModel client;

    private final UDSClientPolicyPermissionsExecutor executor = new UDSClientPolicyPermissionsExecutor();

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
    public void shouldBeBackwardsCompatible() {
        // given
        when(client.getClientId()).thenReturn("uds-core-admin-grafana");

        // when
        boolean result = executor.hasBackwardsCompatibleClientName(client);

        // then
        assertTrue(result);
    }

    @Test
    public void shouldNotBeBackwardsCompatible() {
        // given
        when(client.getClientId()).thenReturn("admin-cli");

        // when
        boolean result = executor.hasBackwardsCompatibleClientName(client);

        // then
        assertFalse(result);
    }

}