package com.rohan.contactus.auth.dto;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class IpResponse {
    private String ip;
    private String countryCode;
    private String countryName;
    private String region;
    private String city;
    private Double latitude;
    private Double longitude;
    private String timezone;
    private Long asn;
    private String isp;
    private String error;
}
