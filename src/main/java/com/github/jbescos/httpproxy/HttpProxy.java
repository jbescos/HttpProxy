package com.github.jbescos.httpproxy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class HttpProxy {
    
    private static final Logger LOGGER = Logger.getLogger(HttpProxy.class.getName());
    private static final int TIMEOUT = 5000;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private volatile boolean stop = false;

    public void start(int port) throws IOException {
        try (ServerSocket server = new ServerSocket(port)) {
            while (!stop) {
                Socket origin = server.accept();
                LOGGER.info(() -> "Open: " + origin);
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
