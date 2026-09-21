package com.aapdasetu.service;

import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AlertService {
    private final List<Map<String, Object>> alerts =
            Collections.synchronizedList(new ArrayList<>());

    public List<Map<String, Object>> generate(List<Map<String, Object>> predictions) {
        List<Map<String, Object>> fresh = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        String issued = now.format(fmt);
        String validUntil = now.plusDays(1).format(fmt);
        synchronized (alerts) {
            for (Map<String, Object> pred : predictions) {
                double rain = toDouble(pred.get("predicted_rainfall_mm"), 0);
                String risk = String.valueOf(pred.getOrDefault("inundation_risk_level", "low"));
                String level;
                if (rain >= 115 || risk.equals("severe") || risk.equals("extreme")) {
                    level = "RED";
                } else if (rain >= 64 || risk.equals("high")) {
                    level = "ORANGE";
                } else if (rain >= 15 || risk.equals("moderate")) {
                    level = "YELLOW";
                } else {
                    level = "GREEN";
                }
                if (level.equals("GREEN")) continue;
                String city = String.valueOf(pred.get("city_name"));
                Map<String, Object> a = new LinkedHashMap<>();
                a.put("id", UUID.randomUUID().toString());
                a.put("city", city);
                a.put("state", pred.get("state_name"));
                a.put("level", level);
                a.put("message", message(level, rain, risk, city));
                a.put("issued_at", issued);
                a.put("timestamp", issued);
                a.put("valid_until", validUntil);
                a.put("rainfall_predicted", rain);
                a.put("inundation_risk", risk);
                a.put("affected_population", toLong(pred.get("population"), 0));
                fresh.add(a);
            }
            alerts.addAll(fresh);
        }
        return fresh;
    }

    private String message(String level, double rain, String risk, String city) {
        if (level.equals("RED")) {
            return String.format("CRITICAL: Extreme rainfall (%.1fmm) in %s. Inundation risk %s. Act now.",
                    rain, city, risk);
        }
        if (level.equals("ORANGE")) {
            return String.format("WARNING: Heavy rainfall (%.1fmm) in %s. Inundation risk %s. Stay prepared.",
                    rain, city, risk);
        }
        if (level.equals("YELLOW")) {
            return String.format("WATCH: Moderate rainfall (%.1fmm) in %s. Risk %s. Stay updated.",
                    rain, city, risk);
        }
        return "Normal conditions.";
    }

    public List<Map<String, Object>> getActive() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        List<Map<String, Object>> out = new ArrayList<>();
        synchronized (alerts) {
            for (Map<String, Object> a : alerts) {
                String validUntil = String.valueOf(a.get("valid_until"));
                if (validUntil.compareTo(now) > 0) {
                    out.add(a);
                }
            }
        }
        return out;
    }

    public List<Map<String, Object>> getByLevel(String level) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> a : getActive()) {
            if (String.valueOf(a.get("level")).equalsIgnoreCase(level)) {
                out.add(a);
            }
        }
        return out;
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

    private long toLong(Object o, long fallback) {
        if (o == null) return fallback;
        if (o instanceof Number) return ((Number) o).longValue();
        try {
            return Long.parseLong(String.valueOf(o));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
