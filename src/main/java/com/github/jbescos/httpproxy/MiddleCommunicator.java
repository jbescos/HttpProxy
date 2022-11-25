package com.github.jbescos.httpproxy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MiddleCommunicator {

    private static final Logger LOGGER = Logger.getLogger(MiddleCommunicator.class.getName());
    static final String TEXT_RESET  = "\u001B[0m";
    static final String TEXT_BLUE   = "\u001B[34m";
    static final String TEXT_RED    = "\u001B[31m";
    static final String TEXT_GREEN  = "\u001B[32m";
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final String HOST = "Host: ";
    private static final byte NEW_LINE = (byte) '\n';
    private final ExecutorService executor;
    private final Socket readerSocket;
    private final Socket writerSocket;
    private final boolean originToRemote;
    private final Reader reader;
    private final MiddleCommunicator callback;

    public MiddleCommunicator(ExecutorService executor, Socket readerSocket, Socket writerSocket, MiddleCommunicator callback) {
        this.executor = executor;
        this.readerSocket = readerSocket;
        this.writerSocket = writerSocket;
        this.originToRemote = callback != null;
        this.reader = originToRemote ? new OriginToRemoteReader() : new RemoteToOriginReader();
        this.callback = callback;
    }

    public void start() {
        executor.submit(reader);
    }

    public void stop(Socket socket, Exception exception) {
        if (!socket.isClosed()) {
            try {
                socket.close();
                if (exception == null) {
                    LOGGER.info(TEXT_GREEN + "Close: " + socket + TEXT_RESET);
                } else {
                    LOGGER.info(TEXT_RED + "Close: " + socket + ". Reason: " + exception + TEXT_RESET);
                }
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Cannot close " + socket + ": " + e.getMessage());
            }
        }
    }

    private abstract class Reader implements Runnable {

        @Override
        public void run() {
            // 1 MB
            byte[] buffer = new byte[BUFFER_SIZE];
            Exception exception = null;
            try {
                int read;
                OriginInfo originInfo = null;
                while ((read = readerSocket.getInputStream().read(buffer)) != -1) {
                    final int readB = read;
                    LOGGER.finest(() -> readerSocket + " read " + readB + " bytes");
                    LOGGER.finest(() -> new String(buffer, 0, readB));
                    if (originToRemote) {
                        if (originInfo == null) {
                            originInfo = getOriginInfo(buffer, read);
                            if (originInfo.respondOrigin()) {
                                // Respond origin
                                String response = "HTTP/1.0 200 Connection established\r\n\r\n";
                                writerSocket.connect(new InetSocketAddress(originInfo.host, originInfo.port));
                                LOGGER.info(() -> TEXT_BLUE + "Open: " + writerSocket + TEXT_RESET);
                                readerSocket.getOutputStream()
                                        .write(response.getBytes());
                                // Start listening from origin
                                callback.start();
                                readerSocket.getOutputStream().flush();
                            }
                        } else {
                            writerSocket.getOutputStream().write(buffer, 0, read);
                            writerSocket.getOutputStream().flush();
                        }
                    } else {
                        writerSocket.getOutputStream().write(buffer, 0, read);
                        writerSocket.getOutputStream().flush();
                    }
                }
            } catch (IOException e) {
                exception = e;
//                LOGGER.log(Level.SEVERE, e.getMessage(), e);
            } finally {
                stop(readerSocket, exception);
                stop(writerSocket, exception);
            }
        }
    }

    private OriginInfo getOriginInfo(byte[] buffer, int read) throws MalformedURLException {
        int readLines = 0;
        StringBuilder builder = new StringBuilder();
        OriginInfo request = new OriginInfo();
        for (int i = 0; i < read; i++) {
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
    
    // Make it easy to understand stacktraces
    private class OriginToRemoteReader extends Reader {
        @Override
        public void run() {
            super.run();
        }
    }

    private class RemoteToOriginReader extends Reader {
        @Override
        public void run() {
            super.run();
        }
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
