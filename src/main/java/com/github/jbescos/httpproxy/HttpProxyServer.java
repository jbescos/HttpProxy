package com.github.jbescos.httpproxy;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;

public class HttpProxyServer {

    private static final int TIMEOUT = 10000;
    private static final String HOST = "Host: ";
    private static final byte NEW_LINE = (byte) '\n';
    private final int port;
    private volatile boolean stop = false;

    private HttpProxyServer(int port) {
        this.port = port;
    }

    public void run() throws Exception {
        try (ServerSocket server = new ServerSocket(port)) {
            while (!stop) {
                Socket originConnection = server.accept();
                System.out.println("Opened origin: " + originConnection);
                Socket remoteConnection = new Socket();
                originConnection.setSoTimeout(TIMEOUT);
                CompletableFuture.runAsync(() -> {
                    OriginInfo originInfo = null;
                    byte[] bufferReadOrigin = new byte[1024 * 1024];
                    try {
                        BufferedInputStream readerOrigin = new BufferedInputStream(originConnection.getInputStream());
                        int readOrigin;
                        while ((readOrigin = readerOrigin.read(bufferReadOrigin)) != -1) {
                            if (originInfo == null) {
                                // It is expected the first time it reads the buffer the host will be there
                                originInfo = getOriginInfo(bufferReadOrigin);
                                System.out.println("Opened remote: " + originConnection);
                                remoteConnection.connect(new InetSocketAddress(originInfo.host, originInfo.port), TIMEOUT);
                                CompletableFuture.runAsync(() -> {
                                    byte[] bufferReadRemote = new byte[1024 * 1024];
                                    try {
                                        BufferedInputStream readerRemote = new BufferedInputStream(
                                                remoteConnection.getInputStream());
                                        int readRemote;
                                        while ((readRemote = readerRemote.read(bufferReadRemote)) != -1) {
                                            originConnection.getOutputStream().write(bufferReadRemote, 0, readRemote);
                                            originConnection.getOutputStream().flush();
                                        }
                                    } catch (IOException e) {
                                        throw new IllegalStateException("Stop remote", e);
                                    }
                                }).whenComplete((Void, t) -> {
                                    try {
//                                        t.printStackTrace();
                                        System.out.println("Closed remote: " + originConnection);
                                        remoteConnection.close();
                                    } catch (IOException e) {
                                        e.printStackTrace();
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
                        throw new IllegalStateException("Stop origin", e);
                    }
                }).whenComplete((Void, t) -> {
                    try {
//                        t.printStackTrace();
                        System.out.println("Closed origin: " + originConnection);
                        originConnection.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
            }
        }
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
