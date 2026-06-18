/*
 * Copyright 2026 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.truststore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildBcfksScriptTest {

    private static final String TRUSTSTORE_PASSWORD = "keycloakchangeit";
    private static final String JVM_CACERTS_PASSWORD = "changeit";

    @TempDir
    Path tempDir;

    @Test
    void importsJvmDefaultsDodCertsPemBundleKubernetesCaAndOpenShiftCaIntoBcfksTruststore() throws Exception {
        Path dodCerts = Files.createDirectory(tempDir.resolve("dod"));
        Path udsBundle = Files.createDirectories(tempDir.resolve("ca-certs"));
        Path serviceAccount = Files.createDirectories(tempDir.resolve("service-account"));
        Path truststore = tempDir.resolve("keycloak-truststore.bcfks");

        createCertificate(dodCerts.resolve("dod-root.pem"), "dod-root");
        Path udsA = createCertificate(tempDir.resolve("uds-a.pem"), "uds-a");
        Path udsB = createCertificate(tempDir.resolve("uds-b.pem"), "uds-b");
        Files.writeString(udsBundle.resolve("bundle.pem"), Files.readString(udsA) + Files.readString(udsB));
        createCertificate(serviceAccount.resolve("ca.crt"), "kube-ca");
        createCertificate(serviceAccount.resolve("service-ca.crt"), "openshift-service-ca");

        BuildResult result = runBuildWithDefaultTruststorePaths(
                truststore,
                dodCerts,
                udsBundle + " " + serviceAccount.resolve("ca.crt") + " " + serviceAccount.resolve("service-ca.crt")
        );

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Built BCFKS truststore with 5 additional certificate(s)"), result.output());
        assertTrue(isBcfks(truststore), "expected " + truststore + " to be a BCFKS keystore");
        assertEquals(6, trustedCertificateEntries(truststore));
    }

    @Test
    void splitsEveryCertificateInPemBundleIntoSeparateTruststoreEntries() throws Exception {
        Path dodCerts = Files.createDirectory(tempDir.resolve("dod"));
        Path udsBundle = Files.createDirectory(tempDir.resolve("ca-certs"));
        Path truststore = tempDir.resolve("keycloak-truststore.bcfks");

        Path first = createCertificate(tempDir.resolve("first.pem"), "first");
        Path second = createCertificate(tempDir.resolve("second.pem"), "second");
        Path third = createCertificate(tempDir.resolve("third.pem"), "third");
        Files.writeString(
                udsBundle.resolve("bundle.pem"),
                Files.readString(first) + Files.readString(second) + Files.readString(third)
        );

        BuildResult result = runBuild(truststore, dodCerts, udsBundle.toString());

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(4, trustedCertificateEntries(truststore));
    }

    @Test
    void skipsMissingTruststorePathsWithoutFailingTheBuild() throws Exception {
        Path dodCerts = Files.createDirectory(tempDir.resolve("dod"));
        Path truststore = tempDir.resolve("keycloak-truststore.bcfks");
        createCertificate(dodCerts.resolve("dod-root.pem"), "dod-root");

        BuildResult result = runBuild(
                truststore,
                dodCerts,
                tempDir.resolve("does-not-exist") + " " + tempDir.resolve("also-missing")
        );

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.output().contains("Truststore path not present, skipping:"), result.output());
        assertEquals(2, trustedCertificateEntries(truststore));
    }

    @Test
    void rebuildsFromDodCertificatesOnlyWhenNoExtraTruststorePathsHaveCertificates() throws Exception {
        Path dodCerts = Files.createDirectory(tempDir.resolve("dod"));
        Path emptyTruststorePath = Files.createDirectory(tempDir.resolve("empty-ca-certs"));
        Path truststore = tempDir.resolve("keycloak-truststore.bcfks");
        createCertificate(dodCerts.resolve("dod-1.pem"), "dod-1");
        createCertificate(dodCerts.resolve("dod-2.pem"), "dod-2");

        BuildResult result = runBuild(truststore, dodCerts, emptyTruststorePath.toString());

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(3, trustedCertificateEntries(truststore));
    }

    @Test
    void createsTruststoreParentDirectoryWhenItDoesNotExist() throws Exception {
        Path dodCerts = Files.createDirectory(tempDir.resolve("dod"));
        Path emptyTruststorePath = Files.createDirectory(tempDir.resolve("empty-ca-certs"));
        Path truststore = tempDir.resolve("missing-parent").resolve("data").resolve("keycloak-truststore.bcfks");
        createCertificate(dodCerts.resolve("dod-root.pem"), "dod-root");

        BuildResult result = runBuild(truststore, dodCerts, emptyTruststorePath.toString());

        assertEquals(0, result.exitCode(), result.output());
        assertTrue(Files.exists(truststore), "expected " + truststore + " to be created");
        assertEquals(2, trustedCertificateEntries(truststore));
    }

    @Test
    void failsWhenNoTruststorePasswordIsProvided() throws Exception {
        Path dodCerts = Files.createDirectory(tempDir.resolve("dod"));
        Path truststore = tempDir.resolve("keycloak-truststore.bcfks");

        BuildResult result = runBuild(truststore, dodCerts, "", null, PasswordSource.none());

        assertEquals(1, result.exitCode(), result.output());
        assertTrue(
                result.output().contains("ERROR: set TRUSTSTORE_PASSWORD_FILE, KC_TRUSTSTORE_PASSWORD, or TRUSTSTORE_PASSWORD"),
                result.output()
        );
    }

    @Test
    void readsTruststorePasswordFromMountedSecretFileWhenProvided() throws Exception {
        Path dodCerts = Files.createDirectory(tempDir.resolve("dod"));
        Path emptyTruststorePath = Files.createDirectory(tempDir.resolve("empty-ca-certs"));
        Path truststore = tempDir.resolve("keycloak-truststore.bcfks");
        createCertificate(dodCerts.resolve("dod-root.pem"), "dod-root");

        Path passwordFile = tempDir.resolve("truststore-password.txt");
        Files.writeString(passwordFile, TRUSTSTORE_PASSWORD);

        BuildResult result = runBuild(
                truststore,
                dodCerts,
                emptyTruststorePath.toString(),
                null,
                PasswordSource.mountedFile(passwordFile)
        );

        assertEquals(0, result.exitCode(), result.output());
        assertEquals(2, trustedCertificateEntries(truststore));
    }

    private BuildResult runBuild(Path truststore, Path dodCertsDir, String truststorePaths) throws Exception {
        return runBuild(truststore, dodCertsDir, truststorePaths, null, PasswordSource.keycloakEnv());
    }

    private BuildResult runBuildWithDefaultTruststorePaths(
            Path truststore,
            Path dodCertsDir,
            String defaultTruststorePaths
    ) throws Exception {
        return runBuild(truststore, dodCertsDir, null, defaultTruststorePaths, PasswordSource.keycloakEnv());
    }

    private BuildResult runBuild(
            Path truststore,
            Path dodCertsDir,
            String truststorePaths,
            String defaultTruststorePaths,
            PasswordSource passwordSource
    ) throws Exception {
        Path script = buildScript();
        Path bcfipsJar = bcfipsJar();
        Path jvmCacerts = createJvmCacerts();
        ProcessBuilder process = processBuilder("sh", script.toString());
        Map<String, String> environment = process.environment();
        environment.remove("TRUSTSTORE_PASSWORD");
        environment.remove("TRUSTSTORE_PASSWORD_FILE");
        environment.remove("KC_TRUSTSTORE_PASSWORD");
        environment.remove("TRUSTSTORE_PATHS");
        environment.remove("DEFAULT_TRUSTSTORE_PATHS");
        environment.remove("JVM_CACERTS");
        environment.put("TRUSTSTORE", truststore.toString());
        passwordSource.apply(environment);
        environment.put("DOD_CERTS_DIR", dodCertsDir.toString());
        if (truststorePaths != null) {
            environment.put("TRUSTSTORE_PATHS", truststorePaths);
        }
        if (defaultTruststorePaths != null) {
            environment.put("DEFAULT_TRUSTSTORE_PATHS", defaultTruststorePaths);
        }
        environment.put("BCFIPS_JAR", bcfipsJar.toString());
        environment.put("JVM_CACERTS", jvmCacerts.toString());
        environment.put("JVM_CACERTS_PASSWORD", JVM_CACERTS_PASSWORD);
        process.redirectErrorStream(true);

        ProcessResult result = run(process);
        return new BuildResult(result.exitCode(), result.output());
    }

    private Path createCertificate(Path output, String commonName) throws Exception {
        Path keyStore = tempDir.resolve(commonName + ".p12");
        ProcessResult genKeyPair = run(processBuilder(
                "keytool",
                "-genkeypair",
                "-alias", "x",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-dname", "CN=" + commonName,
                "-validity", "2",
                "-keystore", keyStore.toString(),
                "-storetype", "pkcs12",
                "-storepass", "changeit",
                "-keypass", "changeit"
        ));
        assertEquals(0, genKeyPair.exitCode(), genKeyPair.output());

        ProcessResult exportCert = run(processBuilder(
                "keytool",
                "-exportcert",
                "-rfc",
                "-alias", "x",
                "-keystore", keyStore.toString(),
                "-storepass", "changeit",
                "-file", output.toString()
        ));
        assertEquals(0, exportCert.exitCode(), exportCert.output());
        return output;
    }

    private Path createJvmCacerts() throws Exception {
        Path output = tempDir.resolve("jvm-cacerts.p12");
        Files.deleteIfExists(output);
        Path jvmRoot = createCertificate(tempDir.resolve("jvm-root.pem"), "jvm-root");
        ProcessResult importCert = run(processBuilder(
                "keytool",
                "-importcert",
                "-noprompt",
                "-alias", "jvm-root",
                "-file", jvmRoot.toString(),
                "-keystore", output.toString(),
                "-storetype", "pkcs12",
                "-storepass", JVM_CACERTS_PASSWORD
        ));
        assertEquals(0, importCert.exitCode(), importCert.output());
        return output;
    }

    private boolean isBcfks(Path truststore) throws Exception {
        ProcessResult result = keytoolList(truststore);
        assertEquals(0, result.exitCode(), result.output());
        return result.output().contains("Keystore type: BCFKS");
    }

    private long trustedCertificateEntries(Path truststore) throws Exception {
        ProcessResult result = keytoolList(truststore);
        assertEquals(0, result.exitCode(), result.output());
        return result.output().lines()
                .filter(line -> line.contains("trustedCertEntry"))
                .count();
    }

    private ProcessResult keytoolList(Path truststore) throws Exception {
        return run(processBuilder(
                "keytool",
                "-list",
                "-keystore", truststore.toString(),
                "-storetype", "bcfks",
                "-providername", "BCFIPS",
                "-providerclass", "org.bouncycastle.jcajce.provider.BouncyCastleFipsProvider",
                "-providerpath", bcfipsJar().toString(),
                "-storepass", TRUSTSTORE_PASSWORD
        ));
    }

    private Path buildScript() throws Exception {
        URL resource = getClass().getClassLoader().getResource("truststore/build_bcfks.sh");
        assertNotNull(resource, "build_bcfks.sh must be copied into test resources");
        return Path.of(resource.toURI());
    }

    private Path bcfipsJar() {
        return Arrays.stream(System.getProperty("java.class.path").split(System.getProperty("path.separator")))
                .map(Path::of)
                .filter(path -> {
                    Path fileName = path.getFileName();
                    return fileName != null && fileName.toString().startsWith("bc-fips-")
                            && fileName.toString().endsWith(".jar");
                })
                .sorted(Comparator.comparing(Path::toString).reversed())
                .findFirst()
                .orElseThrow(() -> new AssertionError("Unable to find bc-fips jar on the Maven test classpath"));
    }

    private ProcessBuilder processBuilder(String... command) {
        return new ProcessBuilder(command).directory(tempDir.toFile()).redirectErrorStream(true);
    }

    private ProcessResult run(ProcessBuilder processBuilder) throws IOException, InterruptedException {
        Process process = processBuilder.start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        return new ProcessResult(exitCode, output);
    }

    private record ProcessResult(int exitCode, String output) {
    }

    private record BuildResult(int exitCode, String output) {
    }

    @FunctionalInterface
    private interface PasswordSource {
        static PasswordSource keycloakEnv() {
            return environment -> environment.put("KC_TRUSTSTORE_PASSWORD", TRUSTSTORE_PASSWORD);
        }

        static PasswordSource mountedFile(Path passwordFile) {
            return environment -> environment.put("TRUSTSTORE_PASSWORD_FILE", passwordFile.toString());
        }

        static PasswordSource none() {
            return environment -> {
            };
        }

        void apply(Map<String, String> environment);
    }
}
