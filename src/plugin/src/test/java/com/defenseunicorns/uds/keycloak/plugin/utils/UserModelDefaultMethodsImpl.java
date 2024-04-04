package com.defenseunicorns.uds.keycloak.plugin.utils;

import org.keycloak.models.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class UserModelDefaultMethodsImpl extends UserModelDefaultMethods {

    String EMAIL = "test.user@test.mil";

    @Override
     public String getId() {
         return null;
     }

     @Override
     public String getUsername() {
         return null;
     }

     @Override
     public void setUsername(String username) { }

     @Override
     public boolean isEnabled() {
         return false;
     }

     @Override
     public void setEnabled(boolean enabled) { }

     @Override
     public void setSingleAttribute(String name, String value) { }

     @Override
     public void setAttribute(String name, List<String> values) { }

     @Override
     public void removeAttribute(String name) { }

     @Override
     public String getFirstAttribute(String name) {
         return null;
     }

     @Override
     public Stream<String> getAttributeStream(String name) {
         return null;
     }

     @Override
     public Map<String, List<String>> getAttributes() {
        return null;
     }

     @Override
     public Stream<String> getRequiredActionsStream() {
         return null;
     }

     @Override
     public void addRequiredAction(String action) { }

     @Override
     public void removeRequiredAction(String action) { }

     @Override
     public void addRequiredAction(RequiredAction action) { }

     @Override
     public void removeRequiredAction(RequiredAction action) { }

     @Override
     public boolean isEmailVerified() {
        return false;
    }

     @Override
     public void setEmailVerified(boolean verified) { }

     @Override
     public Stream<RoleModel> getRealmRoleMappingsStream() {
         return null;
     }

     @Override
     public Stream<RoleModel> getClientRoleMappingsStream(ClientModel app) {
         return null;
     }

     @Override
     public boolean hasRole(RoleModel role) {
         return false;
     }

     @Override
     public void grantRole(RoleModel role) { }

     @Override
     public Stream<RoleModel> getRoleMappingsStream() {
        return null;
     }

     @Override
     public void deleteRoleMapping(RoleModel role) { }

     @Override
     public String getFederationLink() {
        return null;
     }

     @Override
     public void setFederationLink(String link) { }

     @Override
     public String getServiceAccountClientLink() {
        return null;
     }

     @Override
     public void setServiceAccountClientLink(String clientInternalId) { }

     @Override
     public SubjectCredentialManager credentialManager() {
         return null;
     }

     @Override
     public Long getCreatedTimestamp() {
         return null;
     }

     @Override
     public void setCreatedTimestamp(Long timestamp) { }

     @Override
     public Stream<GroupModel> getGroupsStream() {
        return null;
     }

     @Override
     public void joinGroup(GroupModel group) { }

     @Override
     public void leaveGroup(GroupModel group) { }

     @Override
     public boolean isMemberOf(GroupModel group) {
         return false;
     }

     @Override
     public boolean equals(Object o) {
        return false;
     }

     @Override
     public int hashCode() {
        return 1;
     }

    @Override
    public String getFirstName() {
        return null;
    }

    @Override
    public void setFirstName(String firstName) { }

    @Override
    public String getLastName() {
        return null;
    }

    @Override
    public void setLastName(String lastName) { }

    @Override
    public String getEmail() {
        return EMAIL;
    }

    @Override
    public void setEmail(String email) { }

    @Override
    public String toString() {
        return null;
    }
}
