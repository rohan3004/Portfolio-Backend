package com.rohan.contactus.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.model.CountryResponse;
import com.rohan.contactus.auth.dto.IpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.Inet4Address;

@Service
public class IpService {

    private DatabaseReader cityReader;
    private DatabaseReader countryReader;
    private DatabaseReader asnReader;

    @PostConstruct
    public void init() throws IOException {
        try {
            cityReader = new DatabaseReader.Builder(
                    getClass().getResourceAsStream("/geoip/GeoLite2-City.mmdb")
            ).build();
            countryReader = new DatabaseReader.Builder(
                    getClass().getResourceAsStream("/geoip/GeoLite2-Country.mmdb")
            ).build();
            asnReader = new DatabaseReader.Builder(
                    getClass().getResourceAsStream("/geoip/GeoLite2-ASN.mmdb")
            ).build();
        } catch (IOException e) {
            throw new RuntimeException("GeoIP initialization failed", e);
        }
    }

    public IpResponse lookup(String ipAddress) {
        try {
            InetAddress ip = InetAddress.getByName(ipAddress);

            // City lookup (most detailed)
            CityResponse cityResponse = cityReader.city(ip);

            // Country lookup (fallback/additional data)
            CountryResponse countryResponse = countryReader.country(ip);

            // ASN lookup
            AsnResponse asnResponse = asnReader.asn(ip);

            return IpResponse.builder()
                    // IP
                    .ip(ipAddress)

                    // Continent (City DB)
                    .continentCode(cityResponse.getContinent().getCode())
                    .continentNames(cityResponse.getContinent().getNames())

                    // Country (City DB primary, Country DB fallback)
                    .countryIsoCode(cityResponse.getCountry().getIsoCode())
                    .countryNames(cityResponse.getCountry().getNames())
                    .countryIsInEuropeanUnion(String.valueOf(cityResponse.getCountry().isInEuropeanUnion()))  // ← FIXED

                    // Location (City DB)
                    .cityNames(cityResponse.getCity().getNames())
                    .cityName(cityResponse.getCity().getName())
                    .postalCode(cityResponse.getPostal().getCode())
                    .latitude(cityResponse.getLocation().getLatitude())
                    .longitude(cityResponse.getLocation().getLongitude())
                    .accuracyRadius(cityResponse.getLocation().getAccuracyRadius())
                    .metroCode(cityResponse.getLocation().getMetroCode())
                    .timezone(cityResponse.getLocation().getTimeZone())

                    // Subdivision/Region (City DB)
                    .subdivisionIsoCode(cityResponse.getMostSpecificSubdivision().getIsoCode())
                    .subdivisionNames(cityResponse.getMostSpecificSubdivision().getNames())
                    .subdivisionName(cityResponse.getMostSpecificSubdivision().getName())

                    // ASN/Network (ASN DB)
                    .autonomousSystemNumber(asnResponse.getAutonomousSystemNumber())
                    .autonomousSystemOrganization(asnResponse.getAutonomousSystemOrganization())
                    .ipNetwork(ipAddress + "/" + (ip instanceof Inet6Address ? "128" : "32"))

                    .build();

        } catch (UnknownHostException e) {
            return IpResponse.builder()
                    .ip(ipAddress)
                    .error("Invalid IP address: " + e.getMessage())
                    .build();
        } catch (AddressNotFoundException e) {
            return IpResponse.builder()
                    .ip(ipAddress)
                    .error("IP not found in database")
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
        if (countryReader != null) countryReader.close();
        if (asnReader != null) asnReader.close();
    }
}
