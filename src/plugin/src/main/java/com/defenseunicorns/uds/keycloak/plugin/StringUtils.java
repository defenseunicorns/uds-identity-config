package com.defenseunicorns.uds.keycloak.plugin;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class StringUtils {

    private StringUtils() {}

    public static List<String> parseCommaSeparatedStringToList(String input) {
        return Arrays.stream(input.split(","))
                     .map(String::trim)
                     .filter(s -> !s.isEmpty())
                     .collect(Collectors.toList());
    }

    public static boolean isNotBlank(String input) {
        return input != null && !input.isBlank();
    }

}
