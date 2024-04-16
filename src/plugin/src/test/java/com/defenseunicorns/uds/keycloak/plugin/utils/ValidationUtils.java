package com.defenseunicorns.uds.keycloak.plugin.utils;

import org.keycloak.authentication.ValidationContext;
import org.keycloak.common.ClientConnection;
import org.keycloak.component.ComponentModel;
import org.keycloak.events.EventBuilder;
import org.keycloak.http.FormPartValue;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.AuthenticatorConfigModel;
import org.keycloak.models.ClientProvider;
import org.keycloak.models.ClientScopeProvider;
import org.keycloak.models.GroupProvider;
import org.keycloak.models.KeyManager;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.KeycloakTransactionManager;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleProvider;
import org.keycloak.models.SingleUseObjectProvider;
import org.keycloak.models.ThemeManager;
import org.keycloak.models.TokenManager;
import org.keycloak.models.UserLoginFailureProvider;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.utils.FormMessage;
import org.keycloak.provider.InvalidationHandler;
import org.keycloak.provider.Provider;
import org.keycloak.services.clientpolicy.ClientPolicyManager;
import org.keycloak.sessions.AuthenticationSessionModel;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.vault.VaultTranscriber;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class ValidationUtils {

    public static ValidationContext setupVariables(String[] errorEvent, List<FormMessage> errors,
                                                   Map<String, List<String>> valueMap) {

        return new ValidationContext() {
            final RealmModel realmModel = mock(RealmModel.class);

            @Override
            public void validationError(MultivaluedMap<String, String> multivaluedMap, List<FormMessage> list) {
                errors.addAll(list);
            }

            @Override
            public void error(String s) {
                errorEvent[0] = s;
            }

            @Override
            public void success() {

            }

            @Override
            public void excludeOtherErrors() {

            }

            @Override
            public EventBuilder getEvent() {
                return mock(EventBuilder.class);
            }

            @Override
            public EventBuilder newEvent() {
                return null;
            }

            @Override
            public AuthenticationExecutionModel getExecution() {
                return null;
            }

            @Override
            public UserModel getUser() {
                return null;
            }

            @Override
            public void setUser(UserModel userModel) {

            }

            @Override
            public RealmModel getRealm() {
                return realmModel;
            }

            @Override
            public AuthenticationSessionModel getAuthenticationSession() {
                return mock(AuthenticationSessionModel.class);
            }

            @Override
            public ClientConnection getConnection() {
                return mock(ClientConnection.class);
            }

            @Override
            public UriInfo getUriInfo() {
                return mock(UriInfo.class);
            }

            @Override
            public KeycloakSession getSession() {
                return new KeycloakSession() {
                    @Override
                    public KeycloakContext getContext() {
                        return null;
                    }
                    @Override
                    public SingleUseObjectProvider singleUseObjects() {
                        return null;
                    }

                    @Override
                    public GroupProvider groups() {
                        return null;
                    }

                    @Override
                    public RoleProvider roles() {
                        return null;
                    }

                    @Override
                    public KeycloakTransactionManager getTransactionManager() {
                        return null;
                    }

                    @Override
                    public <T extends Provider> T getProvider(Class<T> aClass) {
                        return null;
                    }

                    @Override
                    public <T extends Provider> T getProvider(Class<T> aClass, String s) {
                        return null;
                    }

                    @Override
                    public <T extends Provider> T getComponentProvider(Class<T> aClass, String componentId) {
                        return null;
                    }

                    @Override
                    public <T extends Provider> T getComponentProvider(Class<T> aClass, String componentId, Function<KeycloakSessionFactory,ComponentModel> aFunction) {
                        return null;
                    }

                    @Override
                    public <T extends Provider> T getProvider(Class<T> aClass, ComponentModel componentModel) {
                        return null;
                    }

                    @Override
                    public <T extends Provider> Set<String> listProviderIds(Class<T> aClass) {
                        return null;
                    }

                    @Override
                    public <T extends Provider> Set<T> getAllProviders(Class<T> aClass) {
                        return null;
                    }

                    @Override
                    public Class<? extends Provider> getProviderClass(String s) {
                        return null;
                    }

                    @Override
                    public Object getAttribute(String s) {
                        return null;
                    }

                    @Override
                    public <T> T getAttribute(String s, Class<T> aClass) {
                        return null;
                    }

                    @Override
                    public Object removeAttribute(String s) {
                        return null;
                    }

                    @Override
                    public void setAttribute(String s, Object o) {

                    }

                    @Override
                    public Map<String, Object> getAttributes() {
                        return null;
                    }

                    @Override
                    public void invalidate(InvalidationHandler.InvalidableObjectType type, Object... params) {

                    }

                    @Override
                    public void enlistForClose(Provider provider) {

                    }

                    @Override
                    public KeycloakSessionFactory getKeycloakSessionFactory() {
                        return null;
                    }

                    @Override
                    public RealmProvider realms() {
                        return null;
                    }

                    @Override
                    public ClientProvider clients() {
                        return null;
                    }

                    @Override
                    public UserSessionProvider sessions() {
                        return null;
                    }

                    @Override
                    public AuthenticationSessionProvider authenticationSessions() {
                        return null;
                    }

                    @Override
                    public void close() {

                    }

                    @Override
                    public UserProvider users() {
                        UserProvider userProvider = mock(UserProvider.class);
                        when(userProvider.getUserByEmail(realmModel, "test@ss.usafa.edu"))
                                .thenReturn(mock(UserModel.class));
                        return userProvider;
                    }

                    @Override
                    public ClientScopeProvider clientScopes() {
                        return null;
                    }

                    @Override
                    public KeyManager keys() {
                        return null;
                    }

                    @Override
                    public ThemeManager theme() {
                        return null;
                    }

                    @Override
                    public TokenManager tokens() {
                        return null;
                    }

                    @Override
                    public VaultTranscriber vault() {
                        return null;
                    }

                    @Override
                    public ClientPolicyManager clientPolicy() {
                        return null;
                    }

                    @Override
                    public UserLoginFailureProvider loginFailures() {
                        return null;
                    }

                };
            }

            @Override
            public HttpRequest getHttpRequest() {
                return new HttpRequest() {
                    @Override
                    public HttpHeaders getHttpHeaders() {
                        return null;
                    }

                    @Override
                    public X509Certificate[] getClientCertificateChain() {
                        return new X509Certificate[0];
                    }

                    @Override
                    public UriInfo getUri() {
                        return null;
                    }

                    @Override
                    public String getHttpMethod() {
                        return null;
                    }

                    @Override
                    public MultivaluedMap<String, String> getDecodedFormParameters() {
                        // Create a new MultivaluedMap to hold the data
                        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();

                        // Populate the MultivaluedMap with data from valueMap
                        for (Map.Entry<String, List<String>> entry : valueMap.entrySet()) {
                            formData.addAll(entry.getKey(), entry.getValue());
                        }

                        return Utils.formDataUtil(formData);
                    }

                    @Override
                    public MultivaluedMap<String, FormPartValue> getMultiPartFormParameters() {
                        return null;
                    }
                };
            }

            @Override
            public AuthenticatorConfigModel getAuthenticatorConfig() {
                return null;
            }
        };
    }
}
