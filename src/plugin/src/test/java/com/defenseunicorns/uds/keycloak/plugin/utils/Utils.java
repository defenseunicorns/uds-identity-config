package com.defenseunicorns.uds.keycloak.plugin.utils;

import org.keycloak.authentication.FormContext;
import org.powermock.api.mockito.PowerMockito;

import com.defenseunicorns.uds.keycloak.plugin.X509Tools;

import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;

public class Utils {

    public static void setupX509Mocks() {

        PowerMockito.mockStatic(X509Tools.class);
        PowerMockito.when(X509Tools.getX509Username(any(FormContext.class))).thenReturn("thing");

    }

    public static void setupFileMocks() throws Exception {
        final String fileContent = "x509:\n" +
                "  userIdentityAttribute: \"usercertificate\"\n" +
                "  userActive509Attribute: \"activecac\"\n" +
                "  autoJoinGroup:\n" +
                "    - \"/test-group\"\n" +
                "  requiredCertificatePolicies:\n" +
                "    - \"2.16.840.1.101.2.1.11.36\"\n" +
                "    - \"2.16.840.1.114028.10.1.5\"\n" +
                "groupProtectionIgnoreClients:\n" +
                "  - \"test-client\"\n" +
                "noEmailMatchAutoJoinGroup:\n" +
                "  - \"/randos-test-group\"\n" +
                "emailMatchAutoJoinGroup:\n" +
                "  - description: Test thing 1\n" +
                "    groups:\n" +
                "      - \"/test-group-1-a\"\n" +
                "      - \"/test-group-1-b\"\n" +
                "    domains:\n" +
                "      - \".gov\"\n" +
                "      - \".mil\"\n" +
                "      - \"@afit.edu\"\n" +
                "  - description: Test thing 2\n" +
                "    groups:\n" +
                "      - \"/test-group-2-a\"\n" +
                "    domains:\n" +
                "      - \"@unicorns.com\"\n" +
                "      - \"@merica.test\"";

        File fileMock = PowerMockito.mock(File.class);
        FileInputStream fileInputStreamMock = PowerMockito.mock(FileInputStream.class);

        ByteArrayInputStream inputStream = new ByteArrayInputStream(fileContent.getBytes(StandardCharsets.UTF_8));

        PowerMockito.whenNew(File.class).withAnyArguments().thenReturn(fileMock);
        PowerMockito.whenNew(FileInputStream.class).withAnyArguments().thenReturn(fileInputStreamMock);
        PowerMockito.when(fileMock.exists()).thenReturn(true);
        PowerMockito.when(fileMock.isFile()).thenReturn(true);
        PowerMockito.when(fileMock.canRead()).thenReturn(true);
        PowerMockito.when(fileInputStreamMock.read(any(byte[].class))).thenReturn(-1).thenReturn(0);
        PowerMockito.when(fileInputStreamMock.read(any(byte[].class), any(int.class), any(int.class))).thenReturn(-1).thenReturn(0);
        PowerMockito.when(fileInputStreamMock.available()).thenReturn(inputStream.available());
        PowerMockito.when(fileInputStreamMock.read()).thenAnswer(invocation -> inputStream.read());
        PowerMockito.when(fileInputStreamMock.read(any(byte[].class), any(int.class), any(int.class))).thenAnswer(invocation -> inputStream.read((byte[]) invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));


        List<String> yamlLines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                yamlLines.add(line);
            }
        }
    }

    public static X509Certificate buildTestCertificate() throws Exception {
        // exported certificate from login.dso.mil
        String cert = "-----BEGIN CERTIFICATE-----\n"
            + "MIIH1TCCBr2gAwIBAgIQJI3UKNbRu9YSNYT1XVNjsjANBgkqhkiG9w0BAQsFADCB\n"
            + "ujELMAkGA1UEBhMCVVMxFjAUBgNVBAoTDUVudHJ1c3QsIEluYy4xKDAmBgNVBAsT\n"
            + "H1NlZSB3d3cuZW50cnVzdC5uZXQvbGVnYWwtdGVybXMxOTA3BgNVBAsTMChjKSAy\n"
            + "MDEyIEVudHJ1c3QsIEluYy4gLSBmb3IgYXV0aG9yaXplZCB1c2Ugb25seTEuMCwG\n"
            + "A1UEAxMlRW50cnVzdCBDZXJ0aWZpY2F0aW9uIEF1dGhvcml0eSAtIEwxSzAeFw0y\n"
            + "MjAxMTAyMTU5MjVaFw0yMzAxMTMyMTU5MjVaMHMxCzAJBgNVBAYTAlVTMREwDwYD\n"
            + "VQQIEwhDb2xvcmFkbzEZMBcGA1UEBxMQQ29sb3JhZG8gU3ByaW5nczEeMBwGA1UE\n"
            + "ChMVRGVwYXJ0bWVudCBvZiBEZWZlbnNlMRYwFAYDVQQDEw1sb2dpbi5kc28ubWls\n"
            + "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAymUXk7STDlepS5HJu0ca\n"
            + "B57S5dfLp7zxYmcsGjo10YkHy3m9LASQCTyiioDrlwo2b+n8oZ7esGLv3RgggMwf\n"
            + "xvLVyx1+lZDswxdQoXmjArTdbqpcSoq3Y1rvVp33/jGb3slBjQtcMt2QvaFv3fxy\n"
            + "cwwINvJFEqsQS7zGUgpolJ3smKdcVpUSGZmzpYposuDlPUGeOJaQRMAACW5arWiT\n"
            + "VkDhJD+OVOYEHW8uCQfghD3JJXu6Xp9SwlWe6UNOdxo9cq3s/XE4ZwEgffdLXP2A\n"
            + "wuJF/7B7CFdZjIMptmOODyCeatC344iyubU0MiGCOm4W4wn0pQ0XJtAzWeYFKATL\n"
            + "9BquNOzPUR6pMSFMvIEiS96zbVFuOYt2XKgPryWEYji3Oky082WWYOcXt0NnqnCj\n"
            + "SafVU+2fQi4jQ0att5YXagEEPz83lQZdSKb2+grDeFg78VrEZAe+Y0mVu4/G93he\n"
            + "UOqfZ9jdCnFXq8sEMG9bJJFKeOXkb1Da8Y0amfOw4hFd4UslrbvC5ZCUZNh6roOk\n"
            + "8kast9QWtWFIGPC3f+Uq3gvx3GBHzIG9QPOq1CjSSAF3tWKuMTxK4zaS33mriJo0\n"
            + "Dv1CMX3FCmjT/qG3422guBL02hbGHveDSWk0/saY7ZWFifxnvKEdOi4ItnpMuQhE\n"
            + "zx6/+t7FWuzBTPAeVqV1l2sCAwEAAaOCAxswggMXMAwGA1UdEwEB/wQCMAAwHQYD\n"
            + "VR0OBBYEFCLwpnkje7QKLWok+nWIeBEnIGfmMB8GA1UdIwQYMBaAFIKicHTdvFM/\n"
            + "z3vU981/p2DGCky/MGgGCCsGAQUFBwEBBFwwWjAjBggrBgEFBQcwAYYXaHR0cDov\n"
            + "L29jc3AuZW50cnVzdC5uZXQwMwYIKwYBBQUHMAKGJ2h0dHA6Ly9haWEuZW50cnVz\n"
            + "dC5uZXQvbDFrLWNoYWluMjU2LmNlcjAzBgNVHR8ELDAqMCigJqAkhiJodHRwOi8v\n"
            + "Y3JsLmVudHJ1c3QubmV0L2xldmVsMWsuY3JsMCcGA1UdEQQgMB6CDWxvZ2luLmRz\n"
            + "by5taWyCDWxvZ2luLmRzb3AuaW8wDgYDVR0PAQH/BAQDAgWgMB0GA1UdJQQWMBQG\n"
            + "CCsGAQUFBwMBBggrBgEFBQcDAjBMBgNVHSAERTBDMDcGCmCGSAGG+mwKAQUwKTAn\n"
            + "BggrBgEFBQcCARYbaHR0cHM6Ly93d3cuZW50cnVzdC5uZXQvcnBhMAgGBmeBDAEC\n"
            + "AjCCAYAGCisGAQQB1nkCBAIEggFwBIIBbAFqAHYAtz77JN+cTbp18jnFulj0bF38\n"
            + "Qs96nzXEnh0JgSXttJkAAAF+RgDXCAAABAMARzBFAiEAjJj5KvxiOvlcsc326ttI\n"
            + "snrtX3qeg1NKaXg253zxuyICIB6uaYu7qZvww1unlvhIkcm2WG5LdjBgf0w7ueqA\n"
            + "57pgAHcAs3N3B+GEUPhjhtYFqdwRCUp5LbFnDAuH3PADDnk2pZoAAAF+RgDXLQAA\n"
            + "BAMASDBGAiEApZarmlBvc3HEtu+GbctG2TcPlN9rodFSr8cfQ4nak9MCIQCrKiN3\n"
            + "JFjK8CM6xACN27pJyPFsRh0nzrjadwcdTud/PgB3AOg+0No+9QY1MudXKLyJa8kD\n"
            + "08vREWvs62nhd31tBr1uAAABfkYA1uQAAAQDAEgwRgIhAKJNhQx+xc/bgSvGEsAv\n"
            + "kjeguXlN+GU3uKRL9daPvXwEAiEAhkjxQx8I40hAN9mQ37Tw9lmKazdvkIeforcF\n"
            + "5tqxzN4wDQYJKoZIhvcNAQELBQADggEBAGSn1AAnLNs/EECk5tBBlE+r8rktCQBo\n"
            + "zA2AbX0EvNMrJWb6M6iB9bIlXYAByFRfPG4UgRQoaqoAwtX4mnF9S3sEweCNgOqQ\n"
            + "rdzi9e9ePvHGZcRUKnizFm0FpAJ2NiywezpWX+9muSpl1e9TZy6fBEPyk2M1xScw\n"
            + "h7ffh5F4gt4OBQ31F2FIIcd5ud+5rsI5QGq2+fUeiOxJ6n2yAjr6ywF0lz8semer\n"
            + "OOSFtSTn1ZG4EryV5/79iAkftQWdmz/4dtWhj+Nyufq891unwuFL3oDRBT/21JIk\n"
            + "N8iM5Bydlmc/qlTTYu4bN9pVxEyPZT06Q8wnmEPbaOBRnc0NE9yRkTc=\n"
            + "-----END CERTIFICATE-----";
        ByteArrayInputStream in = new ByteArrayInputStream(cert.getBytes());
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        return (X509Certificate) cf.generateCertificate(in);
    }

    public static MultivaluedMap<String, String> formDataUtil(Map<String, List<String>> formDataMap) {
        // Create a MultivaluedMap instance
        MultivaluedMap<String, String> formData = new MultivaluedHashMap<>();

        // Populate the MultivaluedMap with data from your Map
        for (Map.Entry<String, List<String>> entry : formDataMap.entrySet()) {
            for (String value : entry.getValue()) {
                formData.add(entry.getKey(), value);
            }
        }

        return formData;
    }
}
