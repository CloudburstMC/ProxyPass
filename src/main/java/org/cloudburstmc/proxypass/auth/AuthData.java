package org.cloudburstmc.proxypass.auth;

import lombok.Value;

import java.util.UUID;

@Value
public class AuthData {
    String displayName;
    UUID identity;
    String xuid;
}
