package com.varshasetu.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class RadarService {
    public Map<String, Object> getRadarSimulation(double lat, double lng) {
        return getRadarSimulation(lat, lng, 10);
    }

    public Map<String, Object> getRadarSimulation(double lat, double lng, int size) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<List<Double>> grid = new ArrayList<>();
        int mid = size / 2;
        for (int row = 0; row < size; row++) {
            List<Double> line = new ArrayList<>();
            for (int col = 0; col < size; col++) {
                double dist = Math.sqrt(Math.pow(row - mid, 2) + Math.pow(col - mid, 2));
                double dbz = Math.max(0, 50 - dist * 8 + r.nextDouble(-10, 10));
                line.add(Math.round(dbz * 10.0) / 10.0);
            }
            grid.add(line);
        }
        Map<String, Object> center = new LinkedHashMap<>();
        center.put("lat", lat);
        center.put("lng", lng);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("center", center);
        out.put("dbz_grid", grid);
        out.put("unit", "dBZ");
        out.put("timestamp", LocalDateTime.now().toString());
        out.put("simulated", true);
        return out;
    }
}
