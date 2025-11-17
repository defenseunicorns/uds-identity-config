package com.defenseunicorns.uds.keycloak.plugin;

/**
 * Data record for transferring CAC parsed data into Keycloak Form.
 *
 * @param subjectDN (Optional) extracted Subject DN from CAC certificate
 * @param firstName (Optional) extracted First Name from CAC certificate
 * @param lastName (Optional) extracted Last Name from CAC certificate
 * @param email (Optional) extracted Email from CAC certificate
 */
public record CACInfo(
        String subjectDN,
        String firstName,
        String lastName,
        String email) {
}
