package com.lxy.rpc.example.consumer;

import com.lxy.rpc.api.HelloService;
import com.lxy.rpc.core.client.RpcClientProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 性能压测客户端
 */
public class BenchmarkClient {
    // 日志
    private static final Logger logger = LoggerFactory.getLogger(BenchmarkClient.class);

    public static void main(String[] args) throws InterruptedException {
        // 压测参数
        final int concurrentThreads = 100; // 并发线程数
        final int totalRequests = 5000; // 总请求数
        final int warmupRequests = 200; // 预热请求数

        // 准备工作
        RpcClientProxy clientProxy = RpcClientProxy.builder().build();
        HelloService helloService = clientProxy.getProxy(HelloService.class);

//        // 注册钩子
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            logger.info("====[ConsumerApplication]: 正在通过hook关闭服务====");
//            clientProxy.shutdown();
//            logger.info("[ConsumerApplication]: 通过hook成功关闭服务");
//        }, "RpcClientProxyShutdownHook"));

        // 存储结果
        final AtomicInteger successCount = new AtomicInteger(0); // 成功请求数
        final AtomicInteger failCount = new AtomicInteger(0); // 失败请求数
        final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>(totalRequests)); // 响应时间

        // 线程池和同步工具
        ExecutorService executorService = Executors.newFixedThreadPool(concurrentThreads);
        CountDownLatch finishLatch = new CountDownLatch(totalRequests + warmupRequests);

        logger.info("--- Starting RPC Benchmark ---");
        logger.info("Concurrent Threads: {}", concurrentThreads);
        logger.info("Total Requests: {}", totalRequests);
        logger.info("Warmup Requests: {}", warmupRequests);

        // 预热阶段
        logger.info("--- Starting Warm-up Phase ---");
        for (int i = 0; i < warmupRequests; i++) {
            executorService.submit(() -> {
                try {
                    helloService.sayHello("预热");
                } catch (Throwable e) {
                    // 忽略预热异常
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // 等待预热完成，或者不等，直接开始正式测试
        Thread.sleep(5000);

        // 正式测试阶段
        logger.info("--- Starting Benchmark Phase ---");
        long benchmarkStartTime = System.currentTimeMillis();
        for (int i = 0; i < totalRequests; i++) {
            final int requestNum = i;
            executorService.submit(() -> {
                long callStartTime = 0;
                try {
                    callStartTime = System.currentTimeMillis();
                    String result = helloService.sayHello("benchmark-" + requestNum);
                    long callEndTime = System.currentTimeMillis();

                    if (result != null) {
                        responseTimes.add(callEndTime - callStartTime); // 只记录成功的耗时
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (Throwable e) {
                    failCount.incrementAndGet();
                    logger.error("Error during RPC call: {}", e.getMessage());
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // 等待所有请求完成
        finishLatch.await();
        long benchmarkEndTime = System.currentTimeMillis();
        long totalTimeTakenMs = benchmarkEndTime - benchmarkStartTime;

        // 计算并打印指标
        logger.info("--- Benchmark Results ---");
        printResults(totalRequests, totalTimeTakenMs, successCount.get(), failCount.get(), responseTimes);

        // 清理资源
        executorService.shutdown();
        clientProxy.shutdown();
    }

    private static void printResults(int totalRequests, long totalTimeTakenMs, int successCount, int failCount, List<Long> responseTimes) {
        if (totalTimeTakenMs == 0) totalTimeTakenMs = 1; // 防止除零错误

        double throughput = (double) successCount / (totalTimeTakenMs / 1000.0);
        double errorRate = (double) failCount / totalRequests * 100.0;

        long totalLatency = 0;
        for (long l: responseTimes) {
            totalLatency += l;
        }
        double avgLatency = responseTimes.isEmpty() ? 0 : (double) totalLatency / responseTimes.size();

        Collections.sort(responseTimes);
        long p95Latency = responseTimes.isEmpty() ? 0 : responseTimes.get((int) (responseTimes.size() * 0.95));
        long p99Latency = responseTimes.isEmpty() ? 0 : responseTimes.get((int) (responseTimes.size() * 0.99));
        long maxLatency = responseTimes.isEmpty() ? 0 : responseTimes.get(responseTimes.size() - 1);

        System.out.println("================== Benchmark Report ===================");
        System.out.printf("Total Requests: %d\n", totalRequests);
        System.out.printf("Total Time: %.2f s\n", totalTimeTakenMs / 1000.0);
        System.out.println("-------------------------------------------------------");
        System.out.printf("Throughput(QPS): %.2f req/s\n", throughput);
        System.out.printf("Success Count: %d\n", successCount);
        System.out.printf("Fail Count: %d\n", failCount);
        System.out.printf("Error Rate: %.2f %%\n", errorRate);
        System.out.println("-------------------------------------------------------");
        System.out.printf("Average Latency: %.2f ms\n", avgLatency);
        System.out.printf("P95 Latency: %d ms\n", p95Latency);
        System.out.printf("P99 Latency: %d ms\n", p99Latency);
        System.out.printf("Max Latency: %d ms\n", maxLatency);
        System.out.println("=======================================================");


    }
}
