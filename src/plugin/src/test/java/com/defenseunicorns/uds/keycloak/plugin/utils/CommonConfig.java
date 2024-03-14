package com.defenseunicorns.uds.keycloak.plugin.utils;

import org.apache.commons.io.FilenameUtils;
import org.keycloak.models.GroupModel;
import org.keycloak.models.RealmModel;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.System.exit;
import static org.keycloak.models.utils.KeycloakModelUtils.findGroupByPath;
import org.keycloak.models.KeycloakSession;


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

        config = loadConfigFile();

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
        if (instance == null) {
            instance = new CommonConfig(session, realm);
        }

        return instance;
    }

    private YAMLConfig loadConfigFile() {
        String configFilePath = FilenameUtils.normalize(System.getenv("CUSTOM_REGISTRATION_CONFIG"));
        File file = NewObjectProvider.getFile(configFilePath);
        YAMLConfig yamlConfig;

        try (
                FileInputStream fileInputStream = NewObjectProvider.getFileInputStream(file);
            ) {
            Yaml yaml = NewObjectProvider.getYaml();
            yamlConfig = yaml.load(fileInputStream);
        } catch (IOException e) {
            exit(1);
            return null;
        }

        return yamlConfig;
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