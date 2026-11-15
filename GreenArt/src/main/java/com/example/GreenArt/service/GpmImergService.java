package com.example.GreenArt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GpmImergService {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE; // yyyyMMdd

    private final RestClient restClient;

    public GpmImergService(@Value("${EARTHDATA_TOKEN:}") String token) {
        if (token.isBlank()) {
            throw new IllegalStateException("EARTHDATA_TOKEN is not set");
        }
        this.restClient = RestClient.builder()
                .baseUrl("https://gpm1.gesdisc.eosdis.nasa.gov")
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }

    public Map<String, Object> getRainfall(
            double latitude,
            double longitude,
            String startDate,
            String endDate) {

        // IMERG grid: 0.1 degree squares, lon index first, then lat
        int lonIndex = (int) Math.floor((longitude + 180) / 0.1);
        int latIndex = (int) Math.floor((latitude + 90) / 0.1);

        Map<String, Double> daily = new LinkedHashMap<>();
        LocalDate end = LocalDate.parse(endDate, DAY);
        for (LocalDate d = LocalDate.parse(startDate, DAY); !d.isAfter(end); d = d.plusDays(1)) {
            daily.put(d.format(DAY), getDayValue(d, lonIndex, latIndex));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "NASA GPM IMERG Final Daily V07 (GPM_3IMERGDF)");
        result.put("resolution", "0.1 degree (~10 km grid cell)");
        result.put("gridCellCenter", Map.of(
                "latitude", round(-89.95 + latIndex * 0.1),
                "longitude", round(-179.95 + lonIndex * 0.1)));
        result.put("units", "mm/day");
        result.put("precipitation", daily);
        return result;
    }

    private Double getDayValue(LocalDate day, int lonIndex, int latIndex) {
        String d = day.format(DAY);
        String url = "/opendap/GPM_L3/GPM_3IMERGDF.07/" + day.getYear() + "/"
                + String.format("%02d", day.getMonthValue())
                + "/3B-DAY.MS.MRG.3IMERG." + d + "-S000000-E235959.V07B.nc4.ascii"
                + "?precipitation[0][" + lonIndex + "][" + latIndex + "]";

        String text = restClient.get().uri(url).retrieve().body(String.class);

        // The value is after the last comma on the "precipitation.precipitation..." line
        for (String line : text.split("\n")) {
            if (line.startsWith("precipitation.precipitation")) {
                double value = Double.parseDouble(line.substring(line.lastIndexOf(',') + 1).trim());
                return value < 0 ? null : round(value); // negative = NASA "no data"
            }
        }
        throw new IllegalStateException("Unexpected NASA response for " + d + ": " + text);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
