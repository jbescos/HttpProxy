package com.github.jbescos.httpproxy;

public class Main {

    public static void main(String[] args) throws Exception {
        int port = 18081;
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }
        HttpProxyServer.build(port).run();
        Thread.sleep(999999);
    }

}
