package com.rohan.contactus.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthCheckController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("server", "alive");
        String formattedDate =  Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.of("Asia/Kolkata")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"));
        response.put("timestamp", formattedDate);

        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        response.put("usedMemory", usedMemory);
        response.put("availableProcessors", runtime.availableProcessors());

        try(Connection conn = dataSource.getConnection()) {
            if(conn.isValid(2)) {
                response.put("database", "active");
            }else {
                response.put("database", "inactive");
            }
        }catch (SQLException e){
            response.put("database","inaccessible");
            response.put("dbError", e.getMessage());
        }
        response.put("version","v0.0.1");
        response.put("legal","Copyright © "+ ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).getYear() +" Rohan Chakravarty. All Rights Reserved.");
        HttpHeaders headers = new HttpHeaders();
        headers.add("Cache-Control", "no-cache, no-store, must-revalidate");
        headers.add("Pragma", "no-cache");
        headers.add("Expires", "0");

        return new ResponseEntity<>(response, headers, HttpStatus.OK);
    }
}
