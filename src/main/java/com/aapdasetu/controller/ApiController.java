package com.aapdasetu.controller;

import com.aapdasetu.model.ApiResponse;
import com.aapdasetu.model.City;
import com.aapdasetu.service.AlertService;
import com.aapdasetu.service.ArchitectureService;
import com.aapdasetu.service.CityService;
import com.aapdasetu.service.ForecastService;
import com.aapdasetu.service.InundationService;
import com.aapdasetu.service.RadarService;
import com.aapdasetu.service.SatelliteService;
import com.aapdasetu.service.WeatherService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ApiController {

    private final CityService cityService;
    private final WeatherService weatherService;
    private final ForecastService forecastService;
    private final InundationService inundationService;
    private final AlertService alertService;
    private final ArchitectureService architectureService;
    private final RadarService radarService;
    private final SatelliteService satelliteService;

    public ApiController(CityService cityService, WeatherService weatherService,
                         ForecastService forecastService, InundationService inundationService,
                         AlertService alertService, ArchitectureService architectureService,
                         RadarService radarService, SatelliteService satelliteService) {
        this.cityService = cityService;
        this.weatherService = weatherService;
        this.forecastService = forecastService;
        this.inundationService = inundationService;
        this.alertService = alertService;
        this.architectureService = architectureService;
        this.radarService = radarService;
        this.satelliteService = satelliteService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", "ok");
        out.put("cities", cityService.count());
        out.put("model_trained", forecastService.isLoaded());
        return out;
    }

    @GetMapping("/api/architecture")
    public ApiResponse<Map<String, Object>> architecture() {
        return ApiResponse.success(architectureService.getArchitecture());
    }

    @GetMapping("/api/stations")
    public ApiResponse<List<City>> stations() {
        return ApiResponse.success(cityService.getAll());
    }

    @GetMapping("/api/weather/{city}")
    public ResponseEntity<ApiResponse<?>> weather(@PathVariable String city) {
        City info = cityService.findByName(city);
        if (info == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("City not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                weatherService.getLiveWeather(info.getLat(), info.getLng())));
    }

    @GetMapping("/api/forecast/{city}")
    public ResponseEntity<ApiResponse<?>> forecast(@PathVariable String city) {
        City info = cityService.findByName(city);
        if (info == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("City not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                forecastService.predictRainfall(info.getName(), info.getState(), 5)));
    }

    @GetMapping("/api/inundation/{city}")
    public ResponseEntity<ApiResponse<?>> inundation(@PathVariable String city) {
        City info = cityService.findByName(city);
        if (info == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("City not found"));
        }
        List<Map<String, Object>> preds =
                forecastService.predictRainfall(info.getName(), info.getState(), 1);
        double rainMm = preds.isEmpty() ? 0 : toDouble(preds.get(0).get("rainfall_mm"), 0);
        return ResponseEntity.ok(ApiResponse.success(inundationService.predict(info, rainMm)));
    }

    @GetMapping("/api/alerts")
    public ApiResponse<List<Map<String, Object>>> alerts(
            @RequestParam(required = false) String level) {
        if (level != null && !level.isBlank()) {
            return ApiResponse.success(alertService.getByLevel(level));
        }
        return ApiResponse.success(alertService.getActive());
    }

    @GetMapping("/api/alerts/generate")
    public ApiResponse<Map<String, Object>> alertsGenerate() {
        List<Map<String, Object>> preds = new ArrayList<>();
        List<City> all = cityService.getAll();
        for (int i = 0; i < Math.min(15, all.size()); i++) {
            City c = all.get(i);
            List<Map<String, Object>> fc =
                    forecastService.predictRainfall(c.getName(), c.getState(), 1);
            if (fc.isEmpty()) continue;
            double rain = toDouble(fc.get(0).get("rainfall_mm"), 0);
            Map<String, Object> inun = inundationService.predict(c, rain);
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("city_name", c.getName());
            p.put("state_name", c.getState());
            p.put("predicted_rainfall_mm", rain);
            p.put("inundation_risk_level", inun.get("flood_risk_level"));
            p.put("population", c.getPopulation());
            preds.add(p);
        }
        List<Map<String, Object>> newAlerts = alertService.generate(preds);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generated_count", newAlerts.size());
        out.put("alerts", newAlerts);
        return ApiResponse.success(out);
    }

    @GetMapping("/api/historical/{city}")
    public ResponseEntity<ApiResponse<?>> historical(@PathVariable String city) {
        City info = cityService.findByName(city);
        if (info == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("City not found"));
        }
        List<Map<String, Object>> data =
                forecastService.getDistrictData(info.getState(), info.getName());
        return ResponseEntity.ok(ApiResponse.success(data.subList(0, Math.min(100, data.size()))));
    }

    @GetMapping("/api/district/{state}/{district}")
    public ApiResponse<List<Map<String, Object>>> district(
            @PathVariable String state, @PathVariable String district) {
        List<Map<String, Object>> data = forecastService.getDistrictData(state, district);
        return ApiResponse.success(data.subList(0, Math.min(100, data.size())));
    }

    @GetMapping("/api/model/stats")
    public ApiResponse<Map<String, Object>> modelStats() {
        return ApiResponse.success(forecastService.getModelStats());
    }

    @GetMapping("/api/radar/{city}")
    public ResponseEntity<ApiResponse<?>> radar(@PathVariable String city) {
        City info = cityService.findByName(city);
        if (info == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("City not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                radarService.getRadarSimulation(info.getLat(), info.getLng())));
    }

    @GetMapping("/api/satellite/{city}")
    public ResponseEntity<ApiResponse<?>> satellite(@PathVariable String city) {
        City info = cityService.findByName(city);
        if (info == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("City not found"));
        }
        return ResponseEntity.ok(ApiResponse.success(
                satelliteService.getSatelliteSimulation(info.getLat(), info.getLng())));
    }

    @GetMapping("/api/data-sources")
    public ApiResponse<Map<String, Object>> dataSources() {
        boolean csvExists = new ClassPathResource("data/rainfall_districtwise_daily_imd.csv").exists();
        String apiKey = System.getenv("OPENWEATHERMAP_API_KEY");
        List<Map<String, Object>> sources = new ArrayList<>();
        sources.add(source("IMD CSV", "historical", csvExists ? "active" : "missing"));
        sources.add(source("OpenWeatherMap API", "live",
                (apiKey != null && !apiKey.isBlank()) ? "active" : "simulated"));
        sources.add(source("IMD Radar (simulated)", "radar", "active"));
        sources.add(source("Satellite (simulated)", "satellite", "active"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("sources", sources);
        return ApiResponse.success(out);
    }

    private Map<String, Object> source(String name, String type, String status) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("type", type);
        m.put("status", status);
        return m;
    }

    private double toDouble(Object o, double fallback) {
        if (o == null) return fallback;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
