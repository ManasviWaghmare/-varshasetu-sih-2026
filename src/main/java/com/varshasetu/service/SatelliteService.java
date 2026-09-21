package com.varshasetu.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class SatelliteService {
    public Map<String, Object> getSatelliteSimulation(double lat, double lng) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("cloud_cover_percent", Math.round(r.nextDouble(40, 100) * 10.0) / 10.0);
        out.put("cloud_top_temperature_c", Math.round(r.nextDouble(-60, -20) * 10.0) / 10.0);
        out.put("water_vapor_index", Math.round(r.nextDouble(0.5, 1.0) * 100.0) / 100.0);
        out.put("timestamp", LocalDateTime.now().toString());
        out.put("simulated", true);
        return out;
    }
}
