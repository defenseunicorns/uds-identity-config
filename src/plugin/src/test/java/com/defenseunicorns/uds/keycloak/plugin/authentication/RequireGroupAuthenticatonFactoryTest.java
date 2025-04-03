/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.AuthenticationExecutionModel;

import java.util.List;

import static org.junit.Assert.*;

@RunWith(MockitoJUnitRunner.class)
public class RequireGroupAuthenticatonFactoryTest {

    private final RequireGroupAuthenticatorFactory factory = new RequireGroupAuthenticatorFactory();

    @Test
    public void testGetId() {
        assertEquals("uds-group-restriction", factory.getId());
    }

    @Test
    public void testCreate() {
        Authenticator authenticator = factory.create(null); // Pass null as session, as it's not needed for create method

        assertNotNull(authenticator);
        assertTrue(authenticator instanceof RequireGroupAuthenticator);
    }

    @Test
    public void testGetDisplayType() {
        assertEquals("UDS Operator Group Authentication Validation", factory.getDisplayType());
    }

    @Test
    public void testGetRequirementChoices() {
        AuthenticationExecutionModel.Requirement[] requirementChoices = factory.getRequirementChoices();

        assertNotNull(requirementChoices);
        assertEquals(1, requirementChoices.length);
        assertEquals(AuthenticationExecutionModel.Requirement.REQUIRED, requirementChoices[0]);
    }

    @Test
    public void testIsConfigurable() {
        assertTrue(factory.isConfigurable());
    }

    @Test
    public void testIsUserSetupAllowed() {
        assertFalse(factory.isUserSetupAllowed());
    }

    @Test
    public void testGetConfigProperties() {
        List<?> configProperties = factory.getConfigProperties();

        assertNotNull(configProperties);
        assertEquals(1, configProperties.size());
    }
}
