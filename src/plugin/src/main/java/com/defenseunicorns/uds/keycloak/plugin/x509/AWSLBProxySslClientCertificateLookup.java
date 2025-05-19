package com.defenseunicorns.uds.keycloak.plugin.x509;

import org.jboss.logging.Logger;
import org.keycloak.common.util.PemException;
import org.keycloak.common.util.PemUtils;
import org.keycloak.services.x509.NginxProxySslClientCertificateLookup;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Base64;
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

        // The Mime Base64 handles line breaks correctly
        pem = new String(Base64.getMimeEncoder().encode(pem.getBytes(StandardCharsets.UTF_8)));
        logger.tracef("Base64-encoded PEM from AWS LB: %s\n", pem);

        // We could create the certificate directly here but it's better to use PemUtils and stay consistent with the rest of the codebase
        return PemUtils.decodeCertificate(pem);
    }
}
