package com.example.GreenArt.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * NASA SMAP L4 soil moisture (SPL4SMGP.008, 9 km) through NASA AppEEARS.
 * AppEEARS is slow: we submit a task, wait until it is done, then download the CSV.
 */
@Service
public class SmapService {

    private static final String PRODUCT = "SPL4SMGP.008";
    private static final DateTimeFormatter IN = DateTimeFormatter.BASIC_ISO_DATE;           // yyyyMMdd
    private static final DateTimeFormatter APPEEARS = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    private final String username;
    private final String password;

    // Does NOT follow redirects (we handle the download redirect ourselves)
    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://appeears.earthdatacloud.nasa.gov/api")
            .requestFactory(new JdkClientHttpRequestFactory())
            .build();

    public SmapService(@Value("${EARTHDATA_USERNAME:}") String username,
                       @Value("${EARTHDATA_PASSWORD:}") String password) {
        this.username = username;
        this.password = password;
    }

    public Map<String, Object> getSoilMoisture(
            double latitude,
            double longitude,
            String startDate,
            String endDate,
            String taskId) throws InterruptedException {

        if (username.isBlank() || password.isBlank()) {
            throw new IllegalStateException("EARTHDATA_USERNAME / EARTHDATA_PASSWORD are not set");
        }

        // 1. Log in -> get an AppEEARS token
        String token = restClient.post().uri("/login")
                .headers(h -> h.setBasicAuth(username, password))
                .retrieve().body(JsonNode.class)
                .get("token").asString();
        String bearer = "Bearer " + token;

        // 2. Submit a point task (skipped if we already have a taskId)
        if (taskId == null || taskId.isBlank()) {
            taskId = submitTask(bearer, latitude, longitude, startDate, endDate);
        }

        // 3. Wait until done (check every 10 s, max 10 minutes)
        for (int i = 0; ; i++) {
            String status = restClient.get().uri("/task/{id}", taskId)
                    .header("Authorization", bearer)
                    .retrieve().body(JsonNode.class)
                    .get("status").asString();
            if (status.equals("done")) break;
            if (status.equals("error")) throw new IllegalStateException("AppEEARS task failed: " + taskId);
            if (i >= 60) throw new IllegalStateException("AppEEARS task still not done after 10 min: " + taskId);
            Thread.sleep(10_000);
        }

        return downloadResults(bearer, taskId);
    }

    private String submitTask(String bearer, double latitude, double longitude,
                              String startDate, String endDate) {
        Map<String, Object> task = Map.of(
                "task_type", "point",
                "task_name", "atlasagri-smap",
                "params", Map.of(
                        "dates", List.of(Map.of(
                                "startDate", LocalDate.parse(startDate, IN).format(APPEEARS),
                                "endDate", LocalDate.parse(endDate, IN).format(APPEEARS))),
                        "layers", List.of(
                                Map.of("product", PRODUCT, "layer", "Geophysical_Data_sm_surface"),
                                Map.of("product", PRODUCT, "layer", "Geophysical_Data_sm_rootzone")),
                        "coordinates", List.of(Map.of(
                                "id", "farm", "latitude", latitude, "longitude", longitude))));

        return restClient.post().uri("/task")
                .header("Authorization", bearer)
                .body(task)
                .retrieve().body(JsonNode.class)
                .get("task_id").asString();
    }

    private Map<String, Object> downloadResults(String bearer, String taskId) {
        // 4. Find the results CSV and download it
        JsonNode files = restClient.get().uri("/bundle/{id}", taskId)
                .header("Authorization", bearer)
                .retrieve().body(JsonNode.class)
                .get("files");
        String fileId = null;
        for (JsonNode f : files) {
            if (f.get("file_name").asString().endsWith("results.csv")) {
                fileId = f.get("file_id").asString();
            }
        }
        if (fileId == null) throw new IllegalStateException("No results CSV in AppEEARS task " + taskId);

        ResponseEntity<Void> redirect = restClient.get().uri("/bundle/{id}/{file}", taskId, fileId)
                .header("Authorization", bearer)
                .retrieve().toBodilessEntity();
        URI location = redirect.getHeaders().getLocation();
        String csv = (location != null)
                ? RestClient.create().get().uri(location).retrieve().body(String.class) // no token on the redirect
                : restClient.get().uri("/bundle/{id}/{file}", taskId, fileId)
                        .header("Authorization", bearer).retrieve().body(String.class);

        // 5. Read the CSV and average the values per day
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("source", "NASA SMAP L4 (" + PRODUCT + ") via AppEEARS");
        result.put("resolution", "9 km grid cell (value covers the whole cell, not the exact farm)");
        result.put("units", "m3/m3 (share of soil volume that is water)");
        result.put("appeearsTaskId", taskId);
        result.put("soilMoisture", parseCsv(csv));
        return result;
    }

    private Map<String, Map<String, Double>> parseCsv(String csv) {
        String[] lines = csv.split("\\r?\\n");
        List<String> header = Arrays.asList(lines[0].split(","));
        int dateCol = header.indexOf("Date");
        int surfaceCol = -1, rootCol = -1;
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).endsWith("sm_surface")) surfaceCol = i;
            if (header.get(i).endsWith("sm_rootzone")) rootCol = i;
        }

        Map<String, double[]> sums = new TreeMap<>(); // date -> [surfaceSum, rootSum, count]
        for (int r = 1; r < lines.length; r++) {
            String[] c = lines[r].split(",");
            if (c.length <= Math.max(surfaceCol, rootCol)) continue;
            double surface = Double.parseDouble(c[surfaceCol]);
            double root = Double.parseDouble(c[rootCol]);
            if (surface < 0 || root < 0) continue; // negative = NASA "no data"
            double[] s = sums.computeIfAbsent(c[dateCol].substring(0, 10), k -> new double[3]);
            s[0] += surface;
            s[1] += root;
            s[2]++;
        }

        Map<String, Map<String, Double>> daily = new LinkedHashMap<>();
        sums.forEach((date, s) -> daily.put(date, Map.of(
                "surface", round(s[0] / s[2]),
                "rootzone", round(s[1] / s[2]))));
        return daily;
    }

    private static double round(double v) {
        return Math.round(v * 1000) / 1000.0;
    }
}
