/*
 * Copyright 2024 Defense Unicorns
 * SPDX-License-Identifier: AGPL-3.0-or-later OR LicenseRef-Defense-Unicorns-Commercial
 */

package com.defenseunicorns.uds.keycloak.plugin.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Groups {
    @JsonProperty("anyOf")
    public String[] anyOf;
}
