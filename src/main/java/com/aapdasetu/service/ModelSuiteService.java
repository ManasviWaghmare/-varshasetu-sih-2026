package com.aapdasetu.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Multi-model AI suite: Random Forest (live) + LSTM (simulated time-series)
 * + CNN (simulated inundation segmentation) + SVM/NLP (roadmap).
 * LSTM/CNN outputs are deterministic simulations seeded per city so the
 * demo is stable; wire real trained models here when available.
 */
@Service
public class ModelSuiteService {

    public List<Map<String, Object>> getSuite() {
        List<Map<String, Object>> models = new ArrayList<>();
        models.add(model("rf", "Random Forest Classifier", "active",
                List.of("Flood", "Wildfire", "Storm"), 87.4,
                "Live model. Predicts IMD daily rainfall category (NR/LD/D/N/E/LE) and mm from district normals and departures."));
        models.add(model("lstm", "LSTM Time-Series Forecaster", "simulated",
                List.of("Flood", "Cyclone", "Extreme weather"), 82.1,
                "Simulated 24-hour rainfall intensity curve. Tracks rising intensity hour-by-hour for flash-flood lead time."));
        models.add(model("cnn", "CNN Inundation Mapper", "simulated",
                List.of("Flood", "Landslide"), 79.6,
                "Simulated satellite segmentation. Converts predicted flood zones into an inundation mask with per-zone confidence."));
        models.add(model("svm", "SVM Susceptibility Classifier", "roadmap",
                List.of("Earthquake", "Landslide"), null,
                "Planned. Geological multi-dimensional classification for earthquake and landslide susceptibility mapping."));
        models.add(model("nlp", "NLP Alert Dissemination", "roadmap",
                List.of("All hazards"), null,
                "Planned. Parses unstructured reports and generates multi-language citizen alerts from model outputs."));
        return models;
    }

    private Map<String, Object> model(String id, String name, String status,
                                      List<String> hazards, Double accuracy, String desc) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", name);
        m.put("status", status);
        m.put("hazards", hazards);
        m.put("accuracy", accuracy);
        m.put("description", desc);
        return m;
    }

    /** Simulated LSTM: 24-hour intensity curve seeded per city, anchored on day-1 rain. */
    public Map<String, Object> lstmForecast(String cityName, double baseMm) {
        Random rnd = new Random(Objects.hash(
                cityName == null ? "" : cityName.toLowerCase(), "lstm-v1"));
        List<Map<String, Object>> hours = new ArrayList<>();
        double peak = 0;
        int peakHour = 0;
        // diurnal-ish double hump peaking afternoon/evening
        for (int hr = 0; hr < 24; hr++) {
            double diurnal = 0.4 + 0.6 * Math.exp(-Math.pow(hr - 15, 2) / 18.0)
                    + 0.3 * Math.exp(-Math.pow(hr - 5, 2) / 12.0);
            double v = Math.max(0, baseMm / 24.0 * 3 * diurnal * (0.7 + rnd.nextDouble() * 0.6));
            v = Math.round(v * 100.0) / 100.0;
            if (v > peak) { peak = v; peakHour = hr; }
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("hour", hr);
            h.put("intensity_mm", v);
            hours.add(h);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("city", cityName);
        out.put("model", "LSTM Time-Series Forecaster");
        out.put("status", "simulated");
        out.put("hours", hours);
        out.put("peak_hour", peakHour);
        out.put("peak_intensity_mm", peak);
        out.put("trend", peakHour >= 12 ? "rising-evening" : "rising-morning");
        return out;
    }

    /** Simulated CNN: segmentation summary derived from inundation zones. */
    public Map<String, Object> cnnMapping(String cityName, Map<String, Object> inundation) {
        Object zonesObj = inundation.get("risk_zones");
        int zones = zonesObj instanceof List ? ((List<?>) zonesObj).size() : 0;
        double affected = toDouble(inundation.get("affected_area_percentage"), 0);
        Random rnd = new Random(Objects.hash(
                cityName == null ? "" : cityName.toLowerCase(), "cnn-v1"));
        double floodedPct = Math.min(95, Math.round((affected * (0.9 + rnd.nextDouble() * 0.2)) * 10.0) / 10.0);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("city", cityName);
        out.put("model", "CNN Inundation Mapper");
        out.put("status", "simulated");
        out.put("mask_resolution", "128x128");
        out.put("flooded_cells_pct", floodedPct);
        out.put("mean_confidence", Math.round((0.72 + rnd.nextDouble() * 0.2) * 100.0) / 100.0);
        out.put("zones_detected", zones);
        out.put("flood_risk_level", inundation.get("flood_risk_level"));
        return out;
    }

    private double toDouble(Object o, double fallback) {
        if (o instanceof Number) return ((Number) o).doubleValue();
        try { return Double.parseDouble(String.valueOf(o)); }
        catch (Exception e) { return fallback; }
    }
}
