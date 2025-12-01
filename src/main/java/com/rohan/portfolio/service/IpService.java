package com.rohan.portfolio.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.AsnResponse;
import com.maxmind.geoip2.model.CityResponse;
import com.rohan.portfolio.auth.dto.IpResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

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

            // ASN lookup
            AsnResponse asnResponse = asnReader.asn(ip);

            // Extract data using new record API (v4.0+)
            return IpResponse.builder()
                    // IP
                    .ip(ipAddress)

                    // Continent
                    .continentCode(cityResponse.continent().code())
                    .continentNames(cityResponse.continent().names())

                    // Country
                    .countryIsoCode(cityResponse.country().isoCode())
                    .countryNames(cityResponse.country().names())
                    .countryIsInEuropeanUnion(String.valueOf(cityResponse.country().isInEuropeanUnion()))

                    // Location
                    .cityNames(cityResponse.city().names())
                    .cityName(cityResponse.city().name())
                    .postalCode(cityResponse.postal() != null ? cityResponse.postal().code() : null)
                    .latitude(cityResponse.location().latitude())
                    .longitude(cityResponse.location().longitude())
                    .accuracyRadius(cityResponse.location().accuracyRadius())
                    .timezone(cityResponse.location().timeZone())

                    // Subdivision/Region
                    .subdivisionIsoCode(cityResponse.mostSpecificSubdivision() != null ?
                            cityResponse.mostSpecificSubdivision().isoCode() : null)
                    .subdivisionNames(cityResponse.mostSpecificSubdivision() != null ?
                            cityResponse.mostSpecificSubdivision().names() : null)
                    .subdivisionName(cityResponse.mostSpecificSubdivision() != null ?
                            cityResponse.mostSpecificSubdivision().name() : null)

                    // ASN/Network
                    .autonomousSystemNumber(asnResponse.autonomousSystemNumber())
                    .autonomousSystemOrganization(asnResponse.autonomousSystemOrganization())
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
