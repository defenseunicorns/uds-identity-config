package com.defenseunicorns.uds.keycloak.plugin.x509;

import org.jboss.logging.Logger;
import org.keycloak.common.util.PemException;
import org.keycloak.common.util.PemUtils;
import org.keycloak.services.x509.NginxProxyTrustedClientCertificateLookup;

import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;

public class AWSLBProxyTrustedClientCertificateLookup extends NginxProxyTrustedClientCertificateLookup {

    private static final Logger logger = Logger.getLogger(MethodHandles.lookup().lookupClass());

    public AWSLBProxyTrustedClientCertificateLookup(String sslCientCertHttpHeader, String sslCertChainHttpHeaderPrefix, int certificateChainLength) {
        super(sslCientCertHttpHeader, sslCertChainHttpHeaderPrefix, certificateChainLength, true);
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
}
