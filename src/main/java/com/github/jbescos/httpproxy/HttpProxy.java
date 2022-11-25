package com.github.jbescos.httpproxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class HttpProxy {
    
    private static final Logger LOGGER = Logger.getLogger(HttpProxy.class.getName());
    // 10 minutes
    private static final int TIMEOUT = 600000;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean stop = false;

    public void start(int port) throws IOException {
        LOGGER.info("Listening connections in port: " + port);
        try (ServerSocket server = new ServerSocket(port)) {
            while (!stop) {
                Socket origin = server.accept();
                LOGGER.info(() -> MiddleCommunicator.TEXT_BLUE + "Open: " + origin + MiddleCommunicator.TEXT_RESET);
                origin.setSoTimeout(TIMEOUT);
                Socket remote = new Socket();
                remote.setSoTimeout(TIMEOUT);
                MiddleCommunicator remoteToOrigin = new MiddleCommunicator(executor, remote, origin, null);
                MiddleCommunicator originToRemote = new MiddleCommunicator(executor, origin, remote, remoteToOrigin);
                originToRemote.start();
            }
        }
    }
    
    public void stop() {
        stop = true;
        executor.shutdown();
    }
}
