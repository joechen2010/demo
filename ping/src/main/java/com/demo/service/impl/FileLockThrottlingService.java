package com.demo.service.impl;

import com.demo.service.ThrottlingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component("filelockThrottlingService")
public class FileLockThrottlingService implements ThrottlingService, InitializingBean, Runnable, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(FileLockThrottlingService.class);

    @Value("${lock.file.path:rate_limit.lock}")
    private String lockFilePath;

    @Value("${counter.file.path:rate_limit.counter}")
    private String counterFilePath;

    private FileLock fileLock;
    private FileChannel channel;

    @Value("${throttle.permitsPerSecond:2}")
    private int permitsPerSecond = 2;

    private ScheduledExecutorService resetExecutorService = Executors.newScheduledThreadPool(1);

    @Override
    public void afterPropertiesSet() throws Exception {
        createFile(lockFilePath);
        createFile(counterFilePath);
        resetExecutorService.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        setRequestCount(0);
    }

    @Override
    public boolean tryAcquire() {
        try (RandomAccessFile lockFile = new RandomAccessFile(lockFilePath, "rw")) {
            channel = lockFile.getChannel();
            fileLock = channel.tryLock();
            if (fileLock == null) {
                return false;
            }
            int requestCount = getRequestCount();
            logger.debug("Request count: {} in current second", requestCount);
            if (requestCount < permitsPerSecond) {
                setRequestCount(requestCount + 1);
                return true;
            }
        } catch (Exception e) {
            logger.error("Failed to acquire file lock", e);
            release();
        }
        return false;
    }

    @Override
    public void release() {
        closeChannelAndRelease(channel, fileLock);
        fileLock = null;
        channel = null;
    }

    private int getRequestCount() throws IOException {
        try (RandomAccessFile countFile = new RandomAccessFile(counterFilePath, "rw")) {
            if (countFile.length() == 0) {
                return 0;
            }
            return Integer.parseInt(countFile.readLine());
        } catch (Exception e) {
            return permitsPerSecond;
        }
    }

    private synchronized void setRequestCount(int count){
        FileChannel counterChannel = null;
        FileLock counterLock = null;
        try (RandomAccessFile counterFile = new RandomAccessFile(counterFilePath, "rw")) {
            counterChannel = counterFile.getChannel();
            counterLock = counterChannel.tryLock();
            if (counterLock == null) {
                logger.error("Failed to acquire lock on counter file");
            }
            counterFile.setLength(0);
            counterFile.writeBytes(String.valueOf(count));
        }catch (Exception e){
            logger.error("Failed to set counter", e);
        }finally {
            closeChannelAndRelease(counterChannel, counterLock);
        }
    }

    private void closeChannelAndRelease(FileChannel channel, FileLock fileLock){
        if (channel == null || !channel.isOpen()) {
            return;
        }
        if(fileLock != null){
            try {
                fileLock.release();
            } catch (IOException e) {
                logger.error("Failed to release lock", e);
            }
        }
        try {
            channel.close();
        } catch (IOException e) {
            logger.error("Failed to close channel", e);
        }
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
