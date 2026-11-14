
package com.example.GreenArt.service;

import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class NasaPowerService {


    private final RestClient restClient;

    public NasaPowerService() {
        this.restClient = RestClient.create(
                "https://power.larc.nasa.gov"
        );
    }

    public JsonNode getClimateData(
            double latitude,
            double longitude,
            String startDate,
            String endDate) {

        String url = UriComponentsBuilder
                .fromPath("/api/temporal/daily/point")
                .queryParam("parameters",
                        "T2M,T2M_MAX,T2M_MIN,PRECTOTCORR")
                .queryParam("community", "AG")
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("start", startDate)
                .queryParam("end", endDate)
                .queryParam("format", "JSON")
                .build()
                .encode()
                .toUriString();

        return restClient.get()
                .uri(url)
                .retrieve()
                .body(JsonNode.class);
    }
}
