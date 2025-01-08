package com.demo.service.impl;

import com.demo.service.ThrottlingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

@Component("filelockThrottlingService")
public class FileLockThrottlingService implements ThrottlingService, InitializingBean, Runnable, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(FileLockThrottlingService.class);
    public static final String SPLIT = ":";

    //content format: resetTime:requestCount,  eg: 1736307118857:2
    @Value("${lock.file.path:rate_limit.lock}")
    private String lockFilePath;

    @Value("${throttle.permitsPerSecond:2}")
    private int permitsPerSecond = 2;

    @Value("${acquire.timeout:500}")
    private int timeout = 500;
    @Value("${acquire.backoff:100}")
    private int backoff = 50;

    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final ScheduledExecutorService resetExecutorService = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService resetWorker = Executors.newSingleThreadExecutor();


    @Override
    public void afterPropertiesSet() throws Exception {
        createFile(lockFilePath);
        resetExecutorService.scheduleAtFixedRate(this, 0, 100 , TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        // execute in async just make sure there is no delay for each taks
        resetWorker.execute(this::resetCounter);
    }

    @Override
    public boolean tryAcquire() {
        logger.debug("=== Try to acquire file lock ===");
        return acquireFileLockAndProcess(file -> {
            int requestCount = getRequestCount(file);
            logger.debug("Request counter is {} in current second", requestCount);
            if (requestCount < permitsPerSecond) {
                return setRequestCount(file, requestCount + 1);
            }
            return false;
        });
    }

    public void resetCounter() {
        logger.debug("=== Bging reset request counter ===");
        acquireFileLockAndProcess(file -> {
            try {
                String content = file.readLine();
                String lastResetTime = StringUtils.isEmpty(content) || !content.contains(SPLIT) ? null : content.split(SPLIT)[0];
                long currentTimeMillis = System.currentTimeMillis();
                if (StringUtils.isEmpty(lastResetTime) || currentTimeMillis - Long.parseLong(lastResetTime) >= 1000) {
                    file.setLength(0);
                    String newValue = currentTimeMillis + SPLIT + "0";
                    file.writeBytes(newValue);
                    logger.debug("Reset counter from {} to {}", content, newValue);
                }
                return true;
            }catch (Exception e){
                return false;
            }
        });
        logger.debug("=== Finished reset request counter ===");
    }

    private boolean acquireFileLockAndProcess(Function<RandomAccessFile, Boolean> processFunction) {
        FileLock fileLock = null;
        // use lock to avoid unnecessary try lock the file in single instance, and it will throw OverlappingFileLockException
        reentrantLock.lock();
        try (RandomAccessFile lockFile = new RandomAccessFile(lockFilePath, "rw");
             FileChannel channel = lockFile.getChannel()) {
            long startTime = System.currentTimeMillis();
            while (fileLock == null && System.currentTimeMillis() - startTime <= timeout) {
                fileLock = channel.tryLock();
                if (fileLock == null) {
                    TimeUnit.MILLISECONDS.sleep(backoff);
                }
            }
            if (fileLock == null) {
                logger.debug("Failed to acquire file lock within the timeout period.");
                return false;
            }
            return processFunction.apply(lockFile);
        } catch (Exception e) {
            logger.error("Failed to acquire file lock", e);
        } finally {
            reentrantLock.unlock();
            if (fileLock != null && fileLock.isValid()) {
                try {
                    fileLock.release();
                } catch (IOException e) {
                    logger.error("Failed to release file lock", e);
                }
            }
        }
        return false;
    }

    private int getRequestCount(RandomAccessFile lockFile){
        try{
            String content = lockFile.readLine();
            String counter = content.contains(SPLIT) ? content.split(SPLIT)[1] : content;
            logger.debug("Current request count is {}", content);
            return Integer.parseInt(counter);
        }catch (Exception e){
            logger.error("Failed to get counter", e);
        }
        return Integer.MAX_VALUE;
    }

    public boolean setRequestCount(RandomAccessFile lockFile, int count){
        try {
            lockFile.seek(0);
            String content = lockFile.readLine();
            String newValue = "";
            if(!StringUtils.isEmpty(content) && content.contains(SPLIT)){
                String resetTime = content.split(SPLIT)[0];
                newValue = resetTime + SPLIT + count;
            }else{
                // should not happen, but just in case
                newValue = System.currentTimeMillis() + SPLIT +count;
            }
            lockFile.setLength(0);
            lockFile.writeBytes(newValue);
            logger.debug("Request counter is set to {} from {}", newValue, content);
            return true;
        }catch (Exception e){
            logger.error("Failed to set counter", e);
        }
        return false;
    }

    private static void createFile(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            try {
                if (!file.createNewFile()) {
                    logger.error("Failed to create file {}", filePath);
                }
            } catch (Exception e) {
                logger.error("Failed to create lock file {}", filePath, e);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        resetExecutorService.shutdown();
    }
}
