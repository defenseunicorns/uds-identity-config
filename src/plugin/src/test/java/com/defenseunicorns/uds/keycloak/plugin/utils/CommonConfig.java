package com.defenseunicorns.uds.keycloak.plugin.utils;

import org.keycloak.models.GroupModel;
import org.keycloak.models.RealmModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.keycloak.models.utils.KeycloakModelUtils.findGroupByPath;
import org.keycloak.models.KeycloakSession;

/**
 * This class is used for mocking yaml configs, most methods here will have mocked returns 
 * throughout the plugin unit tests.
 */
public final class CommonConfig {

    /**
     * common config.
     */
    private static CommonConfig instance;
    /**
     * yaml config variable.
     */
    private final YAMLConfig config;
    /**
     * List of GroupModel for auto join group x509.
     */
    private final List<GroupModel> autoJoinGroupX509;
    /**
     * List of GroupModel for no email matchauto join group.
     */
    private final List<GroupModel> noEmailMatchAutoJoinGroup;

    private CommonConfig(final KeycloakSession session, final RealmModel realm) {

        config = new YAMLConfig();

        autoJoinGroupX509 = convertPathsToGroupModels(session, realm, config.getX509().getAutoJoinGroup());
        noEmailMatchAutoJoinGroup = convertPathsToGroupModels(session, realm, config.getNoEmailMatchAutoJoinGroup());

        config.getEmailMatchAutoJoinGroup().forEach(match -> {
            boolean hasInvalidDomain = match.getDomains().stream()
                    .anyMatch(domain -> domain.matches("^[^\\.\\@][\\w\\-\\.]+$"));
            if (hasInvalidDomain) {
                match.setDomains(new ArrayList<>());
            } else {
                match.setGroupModels(convertPathsToGroupModels(session, realm, match.getGroups()));
            }
        });
    }

    /**
     * get common config instance.
     * @param session
     * @param realm
     * @return CommonConfig
     */
    public static CommonConfig getInstance(final KeycloakSession session, final RealmModel realm) {
        return instance;
    }

    private List<GroupModel> convertPathsToGroupModels(
        final KeycloakSession session,
        final RealmModel realm,
        final List<String> paths) {

        return paths
                .stream()
                .map(group -> findGroupByPath(session, realm, group))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * get email match auto join group.
     * @return Stream<YAMLConfigEmailAutoJoin>
     */
    public Stream<YAMLConfigEmailAutoJoin> getEmailMatchAutoJoinGroup() {
        return config
                .getEmailMatchAutoJoinGroup()
                .stream()
                .filter(group -> !group.getDomains().isEmpty());
    }

    /**
     * get user identity attribute.
     * @return String
     */
    public String getUserIdentityAttribute() {
        return config.getX509().getUserIdentityAttribute();
    }

    /**
     * get user active 509 attribute.
     * @return String
     */
    public String getUserActive509Attribute() {
        return config.getX509().getUserActive509Attribute();
    }

    /**
     * get auto join group x509.
     * @return Stream<GroupModel>
     */
    public Stream<GroupModel> getAutoJoinGroupX509() {
        return autoJoinGroupX509.stream();
    }

    /**
     * get required certificate policies.
     * @return Stream<String>
     */
    public Stream<String> getRequiredCertificatePolicies() {
        return config.getX509().getRequiredCertificatePolicies().stream();
    }

    /**
     * get no email match auto join group.
     * @return Stream<GroupModel>
     */
    public Stream<GroupModel> getNoEmailMatchAutoJoinGroup() {
        return noEmailMatchAutoJoinGroup.stream();
    }

    /**
     * get ignored group proetection clients.
     * @return List<String>
     */
    public List<String> getIgnoredGroupProtectionClients() {
        return config.getGroupProtectionIgnoreClients();
    }
}
