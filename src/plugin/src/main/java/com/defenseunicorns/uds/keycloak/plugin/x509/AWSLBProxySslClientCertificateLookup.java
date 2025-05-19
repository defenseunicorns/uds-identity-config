package com.defenseunicorns.uds.keycloak.plugin.x509;

import org.jboss.logging.Logger;
import org.keycloak.common.util.PemException;
import org.keycloak.common.util.PemUtils;
import org.keycloak.services.x509.NginxProxySslClientCertificateLookup;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Set;

public class AWSLBProxySslClientCertificateLookup extends NginxProxySslClientCertificateLookup {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    public AWSLBProxySslClientCertificateLookup(String sslClientCertHttpHeader, String sslCertChainHttpHeaderPrefix, int certificateChainLength, Set<X509Certificate> intermediateCerts, Set<X509Certificate> trustedRootCerts, boolean isTruststoreLoaded) {
        super(sslClientCertHttpHeader, sslCertChainHttpHeaderPrefix, certificateChainLength, intermediateCerts, trustedRootCerts, isTruststoreLoaded, true);
    }

    @Override
    protected X509Certificate decodeCertificateFromPem(String pem) throws PemException {
        // FIXME: Below logging statements are only for debugging and will be removed from the final Pull Request
        logger.tracef("!!! WARNING, if you see this line, this means you're testing an experimental Pull Request with AWS LB support\n");
        logger.tracef("Raw PEM from AWS LB: %s\n", pem);
        if (pem == null) {
            logger.warn("End user TLS Certificate is NULL!");
            return null;
        }

        pem = java.net.URLDecoder.decode(pem, StandardCharsets.UTF_8);
        logger.tracef("URL-decoded PEM from AWS LB: %s\n", pem);

        if (pem.startsWith(PemUtils.BEGIN_CERT)) {
            pem = pem.replace(PemUtils.BEGIN_CERT, "");
            pem = pem.replace(PemUtils.END_CERT, "");
        }

        pem = new String(java.util.Base64.getEncoder().encode(pem.getBytes(StandardCharsets.UTF_8)));
        logger.tracef("Base64-encoded PEM from AWS LB: %s\n", pem);

        return PemUtils.decodeCertificate(pem);
    }

    //TODO: Make this protected in NginxProxySslClientCertificateLookup
    private static String removeBeginEnd(String pem) {
        pem = pem.replace(PemUtils.BEGIN_CERT, "");
        pem = pem.replace(PemUtils.END_CERT, "");
        pem = pem.replace("\r\n", "");
        pem = pem.replace("\n", "");
        return pem.trim();
    }
}
