package com.example.GreenArt.controller;

import com.example.GreenArt.service.SmapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SmapController {
    private final SmapService smapService;

    public SmapController(SmapService smapService) {
        this.smapService = smapService;
    }

    @GetMapping("/api/climate/smap-soil-moisture")
    public Map<String, Object> getSoilMoisture(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam(required = false) String taskId) throws InterruptedException {

        return smapService.getSoilMoisture(latitude, longitude, startDate, endDate, taskId);
    }
}
