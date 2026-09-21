package com.aapdasetu.service;

import com.aapdasetu.model.City;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InundationService {

    /** Exact port of ml_model.predict_inundation. */
    public Map<String, Object> predict(City city, double predictedRainfallMm) {
        double elev = city.getElevation();
        double drainage = city.getDrainageCapacityIndex();
        double floodRisk = city.getFloodRiskRating();
        double riskScore = predictedRainfallMm * 0.5 + floodRisk * 10 - elev * 0.1 - drainage * 50;

        ThreadLocalRandom r = ThreadLocalRandom.current();
        String level;
        double depth;
        double area;
        if (riskScore > 100) {
            level = "extreme";
            depth = r.nextDouble(100, 300);
            area = r.nextDouble(40, 80);
        } else if (riskScore > 70) {
            level = "severe";
            depth = r.nextDouble(50, 100);
            area = r.nextDouble(20, 40);
        } else if (riskScore > 40) {
            level = "high";
            depth = r.nextDouble(20, 50);
            area = r.nextDouble(10, 20);
        } else if (riskScore > 20) {
            level = "moderate";
            depth = r.nextDouble(5, 20);
            area = r.nextDouble(2, 10);
        } else {
            level = "low";
            depth = r.nextDouble(0, 5);
            area = r.nextDouble(0, 2);
        }

        List<Map<String, Object>> zones = new ArrayList<>();
        if (level.equals("moderate") || level.equals("high")
                || level.equals("severe") || level.equals("extreme")) {
            int n = r.nextInt(1, 5);
            for (int i = 0; i < n; i++) {
                Map<String, Object> z = new LinkedHashMap<>();
                z.put("zone_id", "Z-" + (i + 1));
                z.put("lat", round5(city.getLat() + r.nextDouble(-0.05, 0.05)));
                z.put("lng", round5(city.getLng() + r.nextDouble(-0.05, 0.05)));
                z.put("radius_km", round2(r.nextDouble(1.0, 5.0)));
                z.put("estimated_depth_cm", round1(depth * r.nextDouble(0.8, 1.2)));
                zones.add(z);
            }
        }

        String riskLevel = (level.equals("severe") || level.equals("high")) ? level.toUpperCase() : level;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("flood_risk_level", level);
        out.put("risk_level", riskLevel);
        out.put("estimated_water_depth_cm", round1(depth));
        out.put("affected_area_percentage", round1(area));
        out.put("risk_score", round2(riskScore));
        out.put("risk_zones", zones);
        out.put("predicted_rainfall_mm", predictedRainfallMm);
        return out;
    }

    private double round1(double v) { return Math.round(v * 10.0) / 10.0; }
    private double round2(double v) { return Math.round(v * 100.0) / 100.0; }
    private double round5(double v) { return Math.round(v * 100000.0) / 100000.0; }
}
