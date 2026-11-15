package com.example.GreenArt.controller;

import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.example.GreenArt.service.NasaPowerService;

@RestController
public class NasaPowerController {
    private final NasaPowerService nasaPowerService;

    public NasaPowerController(NasaPowerService nasaPowerService) {
        this.nasaPowerService = nasaPowerService;
    }

    @GetMapping("/api/climate/nasa-power")
    public JsonNode getClimateData(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        return nasaPowerService.getClimateData(
                latitude,
                longitude,
                startDate,
                endDate
        );
    }
}
