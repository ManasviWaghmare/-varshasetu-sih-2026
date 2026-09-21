package com.aapdasetu.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArchitectureService {

    /** Mirrors architecture.py ARCHITECTURE (single source of truth). */
    public Map<String, Object> getArchitecture() {
        Map<String, Object> arch = new LinkedHashMap<>();
        arch.put("title", "Architecture Overview");
        arch.put("flows", List.of("Data Sources", "Backend - Python Flask", "Frontend - Vanilla HTML/CSS/JS"));

        List<Map<String, Object>> layers = new ArrayList<>();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", "data-sources");
        data.put("name", "Data Sources");
        data.put("color", "orange");
        data.put("items", List.of(
                item("OpenWeatherMap API", "Live temperature, humidity, pressure, wind",
                        "/api/weather/<city>", "openweathermap", null, null),
                item("Simulated IMD Radar Data", "10x10 reflectivity (dBZ) grid simulation",
                        "/api/radar/<city>", "radar", null, null),
                item("Simulated Satellite Data", "Cloud cover, cloud-top temp, water vapour",
                        "/api/satellite/<city>", "satellite", null, null)));
        layers.add(data);

        Map<String, Object> backend = new LinkedHashMap<>();
        backend.put("id", "backend");
        backend.put("name", "Backend - Python Flask");
        backend.put("color", "red");
        List<Map<String, Object>> bItems = new ArrayList<>();
        bItems.add(moduleItem("Data Ingestion Layer",
                "data_sources.py — live + simulated + IMD CSV fusion", "data_sources.py"));
        bItems.add(moduleItem("AI/ML Prediction Engine",
                "ml_model.py — RandomForest on IMD district data", "ml_model.py"));
        bItems.add(endpointItem("Rainfall Forecast Module",
                "5-day category + mm prediction with confidence", "/api/forecast/<city>"));
        bItems.add(endpointItem("Inundation Prediction Module",
                "Flood depth, affected %, risk zones from rain + terrain", "/api/inundation/<city>"));
        bItems.add(endpointItem("Alert Generation System",
                "alert_system.py — IMD-style GREEN/YELLOW/ORANGE/RED", "/api/alerts"));
        bItems.add(endpointItem("REST API Endpoints",
                "14 JSON endpoints consumed by the dashboard", "/api/stations"));
        backend.put("items", bItems);
        layers.add(backend);

        Map<String, Object> frontend = new LinkedHashMap<>();
        frontend.put("id", "frontend");
        frontend.put("name", "Frontend - Vanilla HTML/CSS/JS");
        frontend.put("color", "pink");
        List<Map<String, Object>> fItems = new ArrayList<>();
        fItems.add(elementItem("Interactive Map Dashboard",
                "Leaflet.js + OpenStreetMap, city markers", "#map"));
        fItems.add(elementItem("Rainfall Heatmap Layer",
                "Leaflet.heat intensity overlay", "heatmap layer"));
        fItems.add(elementItem("Inundation Zones Layer",
                "Blue polygons, opacity = flood depth", "inundation layer"));
        fItems.add(elementItem("Alert Panel & Notifications",
                "Ticker + side panel + IMD colour badges", "#active-alerts-list"));
        fItems.add(elementItem("Historical Analytics Charts",
                "Chart.js — trends, comparison, distribution", "#analytics"));
        frontend.put("items", fItems);
        layers.add(frontend);

        arch.put("layers", layers);
        return arch;
    }

    private Map<String, Object> item(String name, String desc, String endpoint,
                                     String statusKey, String module, String element) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("desc", desc);
        if (endpoint != null) m.put("endpoint", endpoint);
        if (statusKey != null) m.put("status_key", statusKey);
        if (module != null) m.put("module", module);
        if (element != null) m.put("element", element);
        return m;
    }

    private Map<String, Object> moduleItem(String name, String desc, String module) {
        return item(name, desc, null, null, module, null);
    }

    private Map<String, Object> endpointItem(String name, String desc, String endpoint) {
        return item(name, desc, endpoint, null, null, null);
    }

    private Map<String, Object> elementItem(String name, String desc, String element) {
        return item(name, desc, null, null, null, element);
    }
}
