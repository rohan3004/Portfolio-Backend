package com.rohan.portfolio.auth.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IpResponse {
    // IP Details
    private String ip;
    private String error;

    // Country (from City/Country DB)
    private String continentCode;
    private String continentName;
    private String countryIsoCode;
    private String countryName;
    private String countryIsInEuropeanUnion;

    // Location (City DB)
    private String cityName;
    private String postalCode;
    private Double latitude;
    private Double longitude;
    private Integer accuracyRadius;
    private Integer metroCode;
    private String timezone;

    // Subdivision/Region (City DB)
    private String subdivisionIsoCode;
    private String subdivisionName;

    // Network/ISP (ASN DB)
    private Long autonomousSystemNumber;
    private String autonomousSystemOrganization;
    private String ipNetwork;

    // All Names in English (City DB)
    private Map<String, String> cityNames;
    private Map<String, String> subdivisionNames;
    private Map<String, String> countryNames;
    private Map<String, String> continentNames;
}
