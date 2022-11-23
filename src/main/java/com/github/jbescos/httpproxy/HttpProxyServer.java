package com.github.jbescos.httpproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HttpProxyServer {

    private final static Logger LOGGER = Logger.getLogger(HttpProxyServer.class.getName());
    private static final int TIMEOUT = 5000;
    private static final String HOST = "Host: ";
    private static final byte NEW_LINE = (byte) '\n';
    private final int port;
    private final AtomicInteger connectionsCount = new AtomicInteger(0);

    private HttpProxyServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        try (ServerSocket server = new ServerSocket(port)) {
            while (true) {
                Socket originConnection = server.accept();
                LOGGER.info("Opened origin: " + originConnection);
                connectionsCount(true);
                originConnection.setSoTimeout(TIMEOUT);
                CompletableFuture.runAsync(() -> {
                    OriginInfo originInfo = null;
                    byte[] bufferReadOrigin = new byte[1024 * 1024];
                    try {
                        int readOrigin;
                        Socket remoteConnection = new Socket();
                        while ((readOrigin = originConnection.getInputStream().read(bufferReadOrigin)) != -1) {
                            if (originInfo == null) {
                                // It is expected the first time it reads the buffer the host will be there
                                originInfo = getOriginInfo(bufferReadOrigin);
                                remoteConnection.connect(new InetSocketAddress(originInfo.host, originInfo.port));
                                remoteConnection.setSoTimeout(TIMEOUT);
                                LOGGER.info("Opened remote: " + remoteConnection);
                                connectionsCount(true);
                                CompletableFuture.runAsync(() -> {
                                    byte[] bufferReadRemote = new byte[1024 * 1024];
                                    try {
                                        int readRemote;
                                        while ((readRemote = remoteConnection.getInputStream().read(bufferReadRemote)) != -1) {
                                            originConnection.getOutputStream().write(bufferReadRemote, 0, readRemote);
                                            originConnection.getOutputStream().flush();
                                        }
                                    } catch (IOException e) {
                                        LOGGER.warning(e.getMessage());
                                    } finally {
                                        try {
                                            remoteConnection.close();
                                            LOGGER.info("Closed remote: " + remoteConnection);
                                            connectionsCount(false);
                                        } catch (IOException e) {
                                            LOGGER.log(Level.SEVERE, "Cannot close remote: " + remoteConnection, e);
                                        }
                                    }
                                });
                                if (originInfo.respondOrigin()) {
                                    // Respond origin
                                    originConnection.getOutputStream()
                                            .write((originInfo.protocol + " 200 Connection established\n\n").getBytes());
                                } else {
                                    remoteConnection.getOutputStream().write(bufferReadOrigin, 0, readOrigin);
                                }
                            } else {
                                remoteConnection.getOutputStream().write(bufferReadOrigin, 0, readOrigin);
                            }
                            remoteConnection.getOutputStream().flush();
                        }
                    } catch (IOException e) {
                        LOGGER.warning(e.getMessage());
                    } finally {
                        try {
                            originConnection.close();
                            connectionsCount(false);
                            LOGGER.info("Closed origin: " + originConnection);
                        } catch (IOException e) {
                            LOGGER.log(Level.SEVERE, "Cannot close origin: " + originConnection, e);
                        }
                    }
                });
            }
        }
    }

    private void connectionsCount(boolean increment) {
        LOGGER.info("Active connections: " + (increment ? connectionsCount.incrementAndGet() : connectionsCount.decrementAndGet()));
    }
    
    private OriginInfo getOriginInfo(byte[] buffer) throws MalformedURLException {
        int readLines = 0;
        StringBuilder builder = new StringBuilder();
        OriginInfo request = new OriginInfo();
        for (int i = 0; i < buffer.length; i++) {
            byte b = buffer[i];
            if (NEW_LINE == b) {
                String lineStr = builder.toString();
                if (readLines == 0) {
                    request.parseFirstLine(lineStr);
                } else if (lineStr.startsWith(HOST)) {
                    request.parseHost(lineStr);
                    return request;
                }
                readLines++;
                builder.setLength(0);
            } else {
                builder.append((char) b);
            }
        }
        return null;
    }

    public static HttpProxyServer build(int port) {
        return new HttpProxyServer(port);
    }

    private static class OriginInfo {
        private static final String CONNECT = "CONNECT";
        private String host;
        private int port = 80;
        private String protocol;
        private String method;

        // CONNECT host:port HTTP/1.1
        public void parseFirstLine(String line) {
            String[] parts = line.split(" ");
            this.method = parts[0].trim();
            this.protocol = parts[2].trim();
        }

        // Host: host:port
        public void parseHost(String line) {
            line = line.substring(HOST.length()).trim();
            String[] hostPort = line.split(":");
            this.host = hostPort[0];
            if (hostPort.length > 1) {
                this.port = Integer.parseInt(hostPort[1]);
            }
        }

        public boolean respondOrigin() {
            return CONNECT.equals(method);
        }
    }
}
