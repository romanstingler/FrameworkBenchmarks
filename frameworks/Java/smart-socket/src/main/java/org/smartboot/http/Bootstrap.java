/*
 * Copyright (c) 2018, org.smartboot. All rights reserved.
 * project name: smart-socket
 * file name: HttpBootstrap.java
 * Date: 2018-01-28
 * Author: sandao
 */

package org.smartboot.http;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.smartboot.Message;
import org.smartboot.http.server.HttpBootstrap;
import org.smartboot.http.server.HttpRequest;
import org.smartboot.http.server.HttpResponse;
import org.smartboot.http.server.HttpServerHandler;
import org.smartboot.http.server.handler.HttpRouteHandler;

import javax.sql.DataSource;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Bootstrap {
    static byte[] body = "Hello, World!".getBytes();

    public static void main(String[] args) {
        ExecutorService executorService = new ThreadPoolExecutor(Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().availableProcessors(),
                0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(128), new ThreadPoolExecutor.CallerRunsPolicy());
        HttpRouteHandler routeHandle = new HttpRouteHandler();
        routeHandle
                .route("/plaintext", new HttpServerHandler() {


                    @Override
                    public void handle(HttpRequest request, HttpResponse response) throws IOException {
                        response.setContentLength(body.length);
                        response.setContentType("text/plain; charset=UTF-8");
                        response.write(body);
                    }
                })
                .route("/json", new HttpServerHandler() {
                    @Override
                    public void handle(HttpRequest request, HttpResponse response, CompletableFuture<Object> completableFuture) throws IOException {
                        executorService.execute(() -> {
                            try {
                                response.setContentType("application/json");
                                JsonUtil.writeJsonBytes(response, new Message("Hello, World!"));
                            } finally {
                                completableFuture.complete(null);
                            }
                        });
                    }
                });
        initDB(routeHandle,executorService);
        int cpuNum = Runtime.getRuntime().availableProcessors();
        // 定义服务器接受的消息类型以及各类消息对应的处理器
        HttpBootstrap bootstrap = new HttpBootstrap();
        bootstrap.configuration()
                .threadNum(cpuNum)
                .readBufferSize(1024 * 4)
                .writeBufferSize(1024 * 4)
                .readMemoryPool(16384 * 1024 * 4)
                .writeMemoryPool(10 * 1024 * 1024 * cpuNum, cpuNum);
        bootstrap.httpHandler(routeHandle).setPort(8080).start();
    }

    private static void initDB(HttpRouteHandler routeHandle,ExecutorService executorService) {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://tfb-database:5432/hello_world");
        config.setUsername("benchmarkdbuser");
        config.setPassword("benchmarkdbpass");
        config.setMaximumPoolSize(64);
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        DataSource dataSource = new HikariDataSource(config);
        routeHandle.route("/db", new SingleQueryHandler(dataSource,executorService))
                .route("/queries", new MultipleQueriesHandler(dataSource,executorService))
                .route("/updates", new UpdateHandler(dataSource,executorService));
//                .route("/fortunes", new FortunesHandler(dataSource));
    }
}
