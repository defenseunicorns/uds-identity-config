package com.defenseunicorns.uds.keycloak.plugin.x509;

import org.jboss.logging.Logger;
import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.services.x509.NginxProxySslClientCertificateLookupFactory;
import org.keycloak.services.x509.X509ClientCertificateLookup;
import org.keycloak.truststore.TruststoreProvider;
import org.keycloak.truststore.TruststoreProviderFactory;

import java.lang.invoke.MethodHandles;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * The "awslb" Proxy provider for Keycloak to recognize CAC certificates while using AWS Load Balancer.
 *
 * The implementation decodes the certificate from the "x-amzn-mtls-clientcert" HTTP Header and assumes they are
 * Base64 encoded first and then URL-encoded.
 *
 * A typical configuration involves setting the following environmental variables:
 *   * `KC_SPI_X509CERT_LOOKUP_PROVIDER` to `awslb`
 *   * (optional) `KC_SPI_X509CERT_LOOKUP_AWSLB_SSL_CLIENT_CERT` to the name of the HTTP Header to use instead of `x-amzn-mtls-clientcert`
 *
 */
public class AWSLBProxySslClientCertificateLookupFactory extends NginxProxySslClientCertificateLookupFactory {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    private static final String AWS_LB_CERT_HEADER = "x-amzn-mtls-clientcert";

    private static final String PROVIDER = "awslb";

    private static final String CERT_CHAIN_PREFIX = PROVIDER;

    //TODO: Change this to protected in NginxProxySslClientCertificateLookupFactory
    private volatile boolean isTruststoreLoaded;
    //TODO: Change this to protected in NginxProxySslClientCertificateLookupFactory
    private Set<X509Certificate> trustedRootCerts;
    //TODO: Change this to protected in NginxProxySslClientCertificateLookupFactory
    private Set<X509Certificate> intermediateCerts;

    @Override
    public void init(Config.Scope config) {
        super.init(config);
        if (config.getBoolean(CERT_IS_URL_ENCODED) != null) {
            logger.warn("The " + CERT_IS_URL_ENCODED + " setting is ignored by the " + PROVIDER + " provider");
        }

        if (config.getBoolean(HTTP_HEADER_CERT_CHAIN_PREFIX) != null) {
            logger.warn("The " + HTTP_HEADER_CERT_CHAIN_PREFIX + " setting was provided and will be used instead of " + CERT_CHAIN_PREFIX);
        } else {
            sslChainHttpHeaderPrefix = CERT_CHAIN_PREFIX;
        }

        if (config.get(HTTP_HEADER_CLIENT_CERT) != null) {
            logger.warn("The " + HTTP_HEADER_CLIENT_CERT + " setting was provided and will be used instead of " + AWS_LB_CERT_HEADER);
        } else {
            sslClientCertHttpHeader = config.get(HTTP_HEADER_CLIENT_CERT, AWS_LB_CERT_HEADER);
        }

        this.isTruststoreLoaded = false;
        this.trustedRootCerts = ConcurrentHashMap.newKeySet();
        this.intermediateCerts = ConcurrentHashMap.newKeySet();
    }

    @Override
    public X509ClientCertificateLookup create(KeycloakSession session) {
        // This needs to be explicitly called to load the trust stores
        super.create(session);
        if (trustProxyVerification) {
            return new AWSLBProxyTrustedClientCertificateLookup(sslClientCertHttpHeader, sslChainHttpHeaderPrefix, certificateChainLength);
        } else {
            return new AWSLBProxySslClientCertificateLookup(sslClientCertHttpHeader, sslChainHttpHeaderPrefix, certificateChainLength, intermediateCerts, trustedRootCerts, isTruststoreLoaded);
        }
    }

    @Override
    public String getId() {
        return PROVIDER;
    }

    //TODO: Change this to protected in NginxProxySslClientCertificateLookupFactory
    private void loadKeycloakTrustStore(KeycloakSession kcSession) {
        if (isTruststoreLoaded) {
            return;
        }
        synchronized (this) {
            if (isTruststoreLoaded) {
                return;
            }
            logger.debug(" Loading Keycloak truststore ...");
            KeycloakSessionFactory factory = kcSession.getKeycloakSessionFactory();
            TruststoreProviderFactory truststoreFactory = (TruststoreProviderFactory) factory.getProviderFactory(TruststoreProvider.class, "file");
            TruststoreProvider provider = truststoreFactory.create(kcSession);

            if (provider != null && provider.getTruststore() != null) {
                Set<X509Certificate> rootCertificates = provider.getRootCertificates().entrySet().stream().flatMap(t -> t.getValue().stream()).collect(Collectors.toSet());
                Set<X509Certificate> intermediateCertficiates = provider.getIntermediateCertificates().entrySet().stream().flatMap(t -> t.getValue().stream()).collect(Collectors.toSet());

                trustedRootCerts.addAll(rootCertificates);
                intermediateCerts.addAll(intermediateCertficiates);
                logger.debug("Keycloak truststore loaded for NGINX x509cert-lookup provider.");

                isTruststoreLoaded = true;
            }
        }
    }
}
