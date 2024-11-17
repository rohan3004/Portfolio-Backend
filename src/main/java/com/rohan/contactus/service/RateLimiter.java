package com.rohan.contactus.service;

import io.github.bucket4j.Bucket;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@Component
public class RateLimiter {

    private final Bucket bucket;

    public RateLimiter() {
//        Bandwidth limit = Bandwidth.classic(100, Refill.greedy(100,Duration.ofMinutes(1)));
//        this.bucket = Bucket.builder().addLimit(limit).build();
        this.bucket = Bucket.builder().addLimit(limit -> limit.capacity(100).refillGreedy(100,Duration.ofMinutes(1)))
                .build();
    }
    public boolean tryConsume() {
        return bucket.tryConsume(1);
    }

//    public String nomalizeToIpv4(String ipAddress) {
//        try{
//            InetAddress inetAddress = InetAddress.getByName(ipAddress);
//
//            if(inetAddress.getHostAddress().contains(".")) {
//                return inetAddress.getHostAddress();
//            }
//        }catch (UnknownHostException e){
//            return ipAddress;
//        }
//        return ipAddress;
//    }
}
