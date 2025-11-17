/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin;

import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.x509.CertificatePolicies;
import org.bouncycastle.asn1.x509.PolicyInformation;
import org.jboss.logging.Logger;
import org.keycloak.authentication.FormContext;
import org.keycloak.authentication.RequiredActionContext;
import org.keycloak.authentication.authenticators.x509.AbstractX509ClientCertificateAuthenticator;
import org.keycloak.authentication.authenticators.x509.X509AuthenticatorConfigModel;
import org.keycloak.authentication.authenticators.x509.X509ClientCertificateAuthenticator;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.services.x509.X509ClientCertificateLookup;
import org.keycloak.sessions.AuthenticationSessionModel;

import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public final class X509Tools {

    private static final Logger LOG = Logger.getLogger(X509Tools.class.getName());

    private static String getLogPrefix(final AuthenticationSessionModel authenticationSession, final String suffix) {
        return "P1_X509_TOOLS_" + suffix + "_" + authenticationSession.getParentSession().getId();
    }

    private static boolean isX509Registered(final KeycloakSession session, final HttpRequest httpRequest, final RealmModel realm) {
        String logPrefix = getLogPrefix(session.getContext().getAuthenticationSession(), "IS_X509_REGISTERED");
        String username = getX509Username(session, httpRequest, realm);
        LOG.infof("{} X509 ID: {}", logPrefix, username);

        if (username != null) {
            Stream<UserModel> users = session.users().searchForUserByUserAttributeStream(realm, Common.USER_X509_ID_ATTRIBUTE, username);
            return users != null && users.findAny().isPresent();
        }
        return false;
    }

    /**
     * Determine if x509 is registered from form context.
     */
    public static boolean isX509Registered(final FormContext context) {
        return isX509Registered(context.getSession(), context.getHttpRequest(), context.getRealm());
    }

    /**
     * Determine if x509 is registered from required action.
     */
    public static boolean isX509Registered(final RequiredActionContext context) {
        return isX509Registered(context.getSession(), context.getHttpRequest(), context.getRealm());
    }

    /**
     * Get x509 username from identity.
     */
    private static String getX509Username(final KeycloakSession session, final HttpRequest httpRequest, final RealmModel realm) {
        Object identity = getX509Identity(session, httpRequest, realm);
        if (identity != null && !identity.toString().isEmpty()) {
            return identity.toString();
        }
        return null;
    }

    /**
     * Get x509 username from form context.
     */
    public static String getX509Username(final FormContext context) {
        return getX509Username(context.getSession(), context.getHttpRequest(), context.getRealm());
    }

    /**
     * Get x509 username from required action context.
     */
    public static String getX509Username(final RequiredActionContext context) {
        return getX509Username(context.getSession(), context.getHttpRequest(), context.getRealm());
    }

    /**
     * Get x509 certificate policy.
     */
    public static String getCertificatePolicyId(final X509Certificate cert, final int certificatePolicyPos, final int policyIdentifierPos) throws IOException {
        byte[] extPolicyBytes = cert.getExtensionValue(Common.CERTIFICATE_POLICY_OID);
        if (extPolicyBytes == null) {
            return null;
        }

        // Use try-with-resources to ensure streams are closed
        try (ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(extPolicyBytes))) {
            DEROctetString oct = (DEROctetString) asn1InputStream.readObject();

            try (ASN1InputStream seqInputStream = new ASN1InputStream(new ByteArrayInputStream(oct.getOctets()))) {
                ASN1Sequence seq = (ASN1Sequence) seqInputStream.readObject();

                if (seq.size() <= certificatePolicyPos) {
                    return null;
                }

                CertificatePolicies certificatePolicies = new CertificatePolicies(PolicyInformation.getInstance(seq.getObjectAt(certificatePolicyPos)));
                if (certificatePolicies.getPolicyInformation().length <= policyIdentifierPos) {
                    return null;
                }

                PolicyInformation[] policyInformation = certificatePolicies.getPolicyInformation();
                return policyInformation[policyIdentifierPos].getPolicyIdentifier().getId();
            }
        }
    }

    /**
     * Get x509 identity from cert chain.
     */
    public static Object getX509IdentityFromCertChain(final X509Certificate[] certs, final RealmModel realm, final AuthenticationSessionModel authenticationSession) {
        String logPrefix = getLogPrefix(authenticationSession, "GET_X509_IDENTITY_FROM_CHAIN");

        if (certs == null || certs.length == 0) {
            LOG.infof("{} no valid certs found", logPrefix);
            return null;
        }

        boolean hasValidPolicy = false;

        int index = 0;
        // Only check up to 10 cert policies, DoD only uses 1-2 policies
        while (!hasValidPolicy && index < Common.MAX_CERT_POLICIES_TO_CHECK) {
            try {
                String certificatePolicyId = getCertificatePolicyId(certs[0], index, 0);
                if (certificatePolicyId == null) {
                    break;
                }
                LOG.infof("{} checking cert policy {}", logPrefix, certificatePolicyId);
                hasValidPolicy = Common.REQUIRED_CERT_POLICIES.stream().anyMatch(s -> s.equals(certificatePolicyId));
                index++;
            } catch (Exception ignored) {
                LOG.warnf("{} error parsing cert policies", logPrefix);
                // abort checks
                index = Common.MAX_CERT_POLICIES_TO_CHECK;
            }
        }

        if (!hasValidPolicy) {
            LOG.warnf("{} no valid cert policies found", logPrefix);
            return null;
        }

        if (realm.getAuthenticatorConfigsStream().findAny().isPresent()) {
            return realm.getAuthenticatorConfigsStream().filter(config -> config.getConfig().containsKey(AbstractX509ClientCertificateAuthenticator.CUSTOM_ATTRIBUTE_NAME)).map(config -> {
                X509ClientCertificateAuthenticator authenticator = new X509ClientCertificateAuthenticator();
                X509AuthenticatorConfigModel model = new X509AuthenticatorConfigModel(config);
                return authenticator.getUserIdentityExtractor(model).extractUserIdentity(certs);
            }).findFirst().orElse(null);
        }

        return null;
    }

    private static Object getX509Identity(final KeycloakSession session, final HttpRequest httpRequest, final RealmModel realm) {
        try {
            if (session == null || httpRequest == null || realm == null) {
                return null;
            }

            X509ClientCertificateLookup provider = session.getProvider(X509ClientCertificateLookup.class);
            if (provider == null) {
                return null;
            }

            X509Certificate[] certs = provider.getCertificateChain(httpRequest);

            AuthenticationSessionModel authenticationSession = session.getContext().getAuthenticationSession();

            return getX509IdentityFromCertChain(certs, realm, authenticationSession);
        } catch (GeneralSecurityException e) {
            LOG.error(e.getMessage());
        }
        return null;
    }

    /**
     * Get x509 subject DN from form context.
     */
    public static String getX509SubjectDN(final FormContext context) {
        if (context.getSession() == null || context.getHttpRequest() == null || context.getSession().getProvider(X509ClientCertificateLookup.class) == null) {
            return null;
        }

        return getX509SubjectDN(context.getSession(), context.getHttpRequest(), context.getSession().getProvider(X509ClientCertificateLookup.class));
    }

    /**
     * Get x509 subject DN from form context.
     */
    public static String getX509SubjectDN(RequiredActionContext context) {
        if (context.getSession() == null || context.getHttpRequest() == null || context.getSession().getProvider(X509ClientCertificateLookup.class) == null) {
            return null;
        }

        return getX509SubjectDN(context.getSession(), context.getHttpRequest(), context.getSession().getProvider(X509ClientCertificateLookup.class));
    }

    /**
     * Gets x509 subject DN using provided Keycloak session/request/provider.
     */
    public static String getX509SubjectDN(final KeycloakSession session, final HttpRequest httpRequest,
                                          final X509ClientCertificateLookup provider) {
        try {
            X509Certificate[] certs = provider.getCertificateChain(httpRequest);
            if (certs != null && certs.length > 0) {
                return certs[0].getSubjectX500Principal().getName();
            }
        } catch (GeneralSecurityException e) {
            LOG.error(e.getMessage());
        }
        return null;
    }

    /**
     * Extracts the User's Common Name (CN) from the subject DN of the x509 certificate.
     */
    public static String getX509CommonName(final FormContext context) {
        return getX509CommonName(context.getSession(), context.getHttpRequest());
    }

    /**
     * Extracts the User's Common Name (CN) from the subject DN of the x509 certificate.
     */
    public static String getX509CommonName(final RequiredActionContext context) {
        return getX509CommonName(context.getSession(), context.getHttpRequest());
    }

    /**
     * Extracts the first email address from the certificate Subject Alternative Name (SAN).
     * Stops at the first rfc822Name (type 1). Returns null when unavailable.
     */
    public static String getX509Email(final FormContext context) {
        return getX509Email(context.getSession(), context.getHttpRequest());
    }

    /**
     * Extracts the first email address from the certificate Subject Alternative Name (SAN).
     * Stops at the first rfc822Name (type 1). Returns null when unavailable.
     */
    public static String getX509Email(final RequiredActionContext context) {
        return getX509Email(context.getSession(), context.getHttpRequest());
    }

    /**
     * Extracts the first email address from the certificate Subject Alternative Name (SAN).
     * Stops at the first rfc822Name (type 1). Returns null when unavailable
     */
    public static String getX509Email(final KeycloakSession session, final HttpRequest httpRequest) {
        try {
            X509ClientCertificateLookup provider = session != null ? session.getProvider(X509ClientCertificateLookup.class) : null;
            X509Certificate[] certs = provider != null ? provider.getCertificateChain(httpRequest) : null;
            if (certs != null && certs.length > 0 && certs[0] != null) {
                Collection<List<?>> san = certs[0].getSubjectAlternativeNames();
                if (san != null) {
                    for (List<?> entry : san) {
                        if (entry == null || entry.size() < 2) continue;
                        Object type = entry.get(0);
                        Object value = entry.get(1);
                        if (type instanceof Integer && ((Integer) type) == 1 && value instanceof String email) { // rfc822Name
                            if (!isBlank(email)) {
                                return email;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Error extracting SAN email");
        }
        return null;
    }

    /**
     * Extracts the User's Common Name (CN) from the subject DN of the x509 certificate.
     */
    public static String getX509CommonName(final KeycloakSession session, final HttpRequest httpRequest) {
        try {
            X509ClientCertificateLookup provider = session.getProvider(X509ClientCertificateLookup.class);
            if (provider == null) {
                return null;
            }

            X509Certificate[] certs = provider.getCertificateChain(httpRequest);
            if (certs != null && certs.length > 0) {
                String subjectDN = certs[0].getSubjectX500Principal().getName();
                LdapName ldapDN = new LdapName(subjectDN);
                for (Rdn rdn : ldapDN.getRdns()) {
                    if ("CN".equalsIgnoreCase(rdn.getType())) {
                        // Here, we assume the CN field represents the user's common name
                        return rdn.getValue().toString();
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error extracting user's CN from subject DN: " + e.getMessage());
        }
        return null;
    }

    /**
     * Extracts CAC info from x509 certificate.
     */
    public static CACInfo getCACInfo(RequiredActionContext context) {
        String subjectDN = getX509SubjectDN(context);
        String commonName = getX509CommonName(context);
        String email = getX509Email(context);
        return parseCACInfo(subjectDN, commonName, email);
    }

    /**
     * Extracts CAC info from x509 certificate.
     */
    public static CACInfo getCACInfo(final FormContext context) {
        if (context.getSession() == null || context.getHttpRequest() == null || context.getSession().getProvider(X509ClientCertificateLookup.class) == null) {
            return null;
        }
        String subjectDN = getX509SubjectDN(context);
        String commonName = getX509CommonName(context);
        String email = getX509Email(context);
        return parseCACInfo(subjectDN, commonName, email);
    }

    /**
     * Parses CAC info from subjectDN, commonName, and email.
     */
    public static CACInfo parseCACInfo(String subjectDN, String commonName, String email) {
        String firstName = null;
        String lastName = null;
        if (!isBlank(commonName)) {
            // Split by '.' and normalize/trim tokens, discarding empties
            String[] parts = Stream.of(commonName.split("\\."))
                    .map(p -> p == null ? null : p.trim())
                    .filter(p -> p != null && !p.isEmpty())
                    .toArray(String[]::new);

            if (parts.length >= 1) {
                boolean subjectIndicatesDoD = subjectDN != null && subjectDN.contains("OU=DoD");
                boolean hasEdiLikeSuffix = parts.length >= 3 && parts[parts.length - 1].matches("\\d{6,}");
                boolean useDoDMapping = subjectIndicatesDoD || hasEdiLikeSuffix;

                if (parts.length >= 2) {
                    if (useDoDMapping) {
                        // DoD profile CN format: LAST.FIRST[.MIDDLE][.EDI]
                        lastName = capitalize(parts[0]);
                        firstName = capitalize(parts[1]);
                    } else {
                        // Generic format: FIRST.LAST
                        firstName = capitalize(parts[0]);
                        lastName = capitalize(parts[1]);
                    }
                } else {
                    // Single-token CN: treat as first name only
                    firstName = capitalize(parts[0]);
                }
            }
        }
        return new CACInfo(subjectDN, firstName, lastName, email);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String capitalize(String s) {
        if (isBlank(s)) return s;
        String lower = s.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
