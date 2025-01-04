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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Component("filelockThrottlingService")
public class FileLockThrottlingService implements ThrottlingService, InitializingBean, Runnable, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(FileLockThrottlingService.class);
    private static final ReentrantLock counterReentrantLock = new ReentrantLock();
    public static final String SPLIT = ":";

    @Value("${lock.file.path:rate_limit.lock}")
    private String lockFilePath;

    // store the reset time and request count,format: timestamp:count
    @Value("${counter.file.path:rate_limit.counter}")
    private String counterFilePath;

    @Value("${throttle.permitsPerSecond:2}")
    private int permitsPerSecond = 2;

    private ScheduledExecutorService resetExecutorService = Executors.newScheduledThreadPool(1);

    @Override
    public void afterPropertiesSet() throws Exception {
        createFile(lockFilePath);
        createFile(counterFilePath);
        resetExecutorService.scheduleAtFixedRate(this, 0, 250, TimeUnit.MILLISECONDS);
    }


    @Override
    public void run() {
        try (RandomAccessFile counterFile = new RandomAccessFile(counterFilePath, "rw");
             FileChannel counterChannel = counterFile.getChannel();
             FileLock counterLock = counterChannel.tryLock()) {
             if(counterLock == null){
                 logger.debug("Failed to acquire lock for counter file, skip reset");
                 return;
             }
            String content = counterFile.readLine();
            String lastResetTime = StringUtils.isEmpty(content) || !content.contains(SPLIT) ? null
                    : content.split(SPLIT)[0];
            if (StringUtils.isEmpty(lastResetTime)
                    || System.currentTimeMillis() - Long.parseLong(lastResetTime) >= 1000) {
                counterFile.setLength(0);
                String newValue = System.currentTimeMillis() + SPLIT + "0";
                counterFile.writeBytes(newValue);
                logger.debug("Reset counter from {} to {}", content, newValue);
            }
        }catch (Exception e){
            logger.error("Failed to set counter", e);
        }
    }

    @Override
    public boolean tryAcquire() {
        logger.debug("=== Try to acquire file lock ===");
        try (RandomAccessFile lockFile = new RandomAccessFile(lockFilePath, "rw");
             FileChannel channel = lockFile.getChannel();
             FileLock fileLock = channel.tryLock()) {
            if (fileLock == null) {
                logger.debug("Failed to acquire file lock, request rejected");
                return false;
            }
            counterReentrantLock.lock();
            try {
                // get current request count
                int requestCount = getRequestCount();
                logger.debug("Request counter is {} in current second", requestCount);
                if (requestCount < permitsPerSecond) {
                    return setRequestCount(requestCount + 1);
                }
            } finally {
                counterReentrantLock.unlock();
            }
        } catch (Exception e) {
            logger.error("Failed to acquire file lock", e);
        }
        return false;
    }

    private int getRequestCount(){
        try (RandomAccessFile counterFile = new RandomAccessFile(counterFilePath, "rw");
             FileChannel counterChannel = counterFile.getChannel();
             FileLock counterLock = counterChannel.tryLock()) {
            // new file, means no request so far
            if (counterFile.length() == 0) {
                return 0;
            }
            if (counterLock != null) {
                String content = counterFile.readLine();
                String counter = content.contains(SPLIT) ? content.split(SPLIT)[1] : content;
                logger.debug("Current request count is {}", content);
                return Integer.parseInt(counter);
            }
        }catch (Exception e){
            logger.error("Failed to get counter", e);
        }
        //we reject this acquire due to lock fail
        return Integer.MAX_VALUE;
    }

    public boolean setRequestCount(int count){
        try (RandomAccessFile counterFile = new RandomAccessFile(counterFilePath, "rw");
             FileChannel counterChannel = counterFile.getChannel();
             FileLock counterLock = counterChannel.tryLock()) {
            if (counterLock == null) {
                return false;
            }
            String content = counterFile.readLine();
            String newValue = "";
            if(!StringUtils.isEmpty(content) && content.contains(SPLIT)){
                String resetTime = content.split(SPLIT)[0];
                newValue = resetTime + SPLIT + count;
            }else{
                // should not happen, but just in case
                newValue = String.valueOf(count);
            }
            counterFile.setLength(0);
            counterFile.writeBytes(newValue);
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
