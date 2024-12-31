package com.demo.service;

public interface ThrottlingService {

    boolean tryAcquire();

}
