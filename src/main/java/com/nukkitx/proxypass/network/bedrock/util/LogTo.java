package com.nukkitx.proxypass.network.bedrock.util;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum LogTo {
    @JsonProperty("file")
    FILE,
    @JsonProperty("console")
    CONSOLE,
    @JsonProperty("both")
    BOTH;

    public boolean logToFile;
    public boolean logToConsole;

    static {
        FILE.logToFile = true;
        CONSOLE.logToFile = false;
        BOTH.logToFile = true;

        FILE.logToConsole = false;
        CONSOLE.logToConsole = true;
        BOTH.logToConsole = true;
    }
}
