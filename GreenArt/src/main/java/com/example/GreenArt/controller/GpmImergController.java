package com.example.GreenArt.controller;

import com.example.GreenArt.service.GpmImergService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class GpmImergController {
    private final GpmImergService gpmImergService;

    public GpmImergController(GpmImergService gpmImergService) {
        this.gpmImergService = gpmImergService;
    }

    @GetMapping("/api/climate/gpm-rainfall")
    public Map<String, Object> getRainfall(
            @RequestParam double latitude,
            @RequestParam double longitude,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        return gpmImergService.getRainfall(latitude, longitude, startDate, endDate);
    }
}
