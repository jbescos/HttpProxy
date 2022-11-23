package com.github.jbescos.httpproxy;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.LogManager;

public class Main {

    static {
        try (InputStream configFile = Main.class.getResourceAsStream("/logging.properties")) {
            LogManager.getLogManager().readConfiguration(configFile);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize the logger", e);
        }
    }

    public static void main(String[] args) throws Exception {
        int port = 18081;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        HttpProxyServer.build(port).run();
    }

}
