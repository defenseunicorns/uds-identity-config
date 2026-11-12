/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */
package com.defenseunicorns.uds.keycloak.plugin.clientpolicy.executor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.protocol.ProtocolMapper;
import org.keycloak.protocol.oidc.mappers.HardcodedClaim;
import org.keycloak.representations.idm.ProtocolMapperRepresentation;
import org.keycloak.services.clientpolicy.ClientPolicyException;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProtocolMapperPolicyTest {
    @Mock KeycloakSession session;
    @Mock KeycloakSessionFactory factory;

    @TestFactory
    Stream<DynamicTest> rejectsProtectedClaimsAfterDecodingPath() {
        return Stream.of("sub", "realm_access.roles", "s\\ub", "r\\esource_access.admin.roles")
                .map(claim -> DynamicTest.dynamicTest(claim, () -> {
                    // given
                    var mapper = mapperWithClaim(claim);
                    // when / then
                    assertThrows(ClientPolicyException.class,
                            () -> ProtocolMapperPolicy.validate(List.of(mapper), List.of("mapper")));
                }));
    }

    @TestFactory
    Stream<DynamicTest> allowsCustomClaimPaths() {
        return Stream.of("custom.profile", "custom\\.profile")
                .map(claim -> DynamicTest.dynamicTest(claim, () -> {
                    // given
                    var mapper = mapperWithClaim(claim);
                    // when / then
                    assertDoesNotThrow(() -> ProtocolMapperPolicy.validate(List.of(mapper), List.of("mapper")));
                }));
    }

    @Test
    void resolvesDefaultsAndRegisteredUdsMappers() {
        // given
        class TestMapper extends HardcodedClaim {}
        when(session.getKeycloakSessionFactory()).thenReturn(factory);
        when(factory.getProviderFactoriesStream(ProtocolMapper.class)).thenReturn(Stream.of(new TestMapper()));
        var config = new UDSClientPolicyPermissionsExecutorConfiguration();
        // when
        var allowed = ProtocolMapperPolicy.resolveAllowed(config, session);
        // then
        assertTrue(allowed.containsAll(ProtocolMapperPolicy.DEFAULT_ALLOWED_PROTOCOL_MAPPERS));
        assertTrue(allowed.contains(new TestMapper().getId()));
    }

    @Test
    void mergesExplicitValuesWithDefaultsDisabled() {
        // given
        var config = new UDSClientPolicyPermissionsExecutorConfiguration();
        config.setUseDefaultAllowedProtocolMappers(false);
        config.setAllowedProtocolMappersAsString("one, two");
        config.setAllowedProtocolMappers(List.of("two", "three"));
        // when
        var allowed = ProtocolMapperPolicy.resolveAllowed(config, session);
        // then
        assertEquals(Set.of("one", "two", "three"), Set.copyOf(allowed));
        verifyNoInteractions(session);
    }

    private static ProtocolMapperRepresentation mapperWithClaim(String claim) {
        var mapper = new ProtocolMapperRepresentation();
        mapper.setProtocolMapper("mapper");
        mapper.setConfig(Map.of("claim.name", claim));
        return mapper;
    }
}
