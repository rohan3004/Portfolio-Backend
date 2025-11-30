package com.rohan.contactus.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.rohan.contactus.auth.dto.IpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetAddress;

@Service
public class IpService {

    private DatabaseReader cityReader;
    private DatabaseReader asnReader;

    @PostConstruct
    public void init() throws IOException {
        cityReader = new DatabaseReader.Builder(
                getClass().getResourceAsStream("/geoip/GeoLite2-City.mmdb")
        ).build();
        asnReader = new DatabaseReader.Builder(
                getClass().getResourceAsStream("/geoip/GeoLite2-ASN.mmdb")
        ).build();
    }

    public IpResponse lookup(String ipAddress) {
        try {
            InetAddress ip = InetAddress.getByName(ipAddress);
            CityResponse city = cityReader.city(ip);
            AsnResponse asn = asnReader.asn(ip);

            return IpResponse.builder()
                    .ip(ipAddress)
                    .countryCode(city.getCountry().getIsoCode())
                    .countryName(city.getCountry().getName())
                    .region(city.getMostSpecificSubdivision().getName())
                    .city(city.getCity().getName())
                    .latitude(city.getLocation().getLatitude())
                    .longitude(city.getLocation().getLongitude())
                    .timezone(city.getLocation().getTimeZone())
                    .asn(asn.getAutonomousSystemNumber())
                    .isp(asn.getAutonomousSystemOrganization())
                    .build();
        } catch (Exception e) {
            return IpResponse.builder()
                    .ip(ipAddress)
                    .error("Lookup failed: " + e.getMessage())
                    .build();
        }
    }

    @PreDestroy
    public void close() throws IOException {
        if (cityReader != null) cityReader.close();
        if (asnReader != null) asnReader.close();
    }
}
