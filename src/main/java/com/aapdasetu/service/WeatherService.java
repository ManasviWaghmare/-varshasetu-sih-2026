package com.aapdasetu.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WeatherService {
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> getLiveWeather(double lat, double lng) {
        String apiKey = System.getenv("OPENWEATHERMAP_API_KEY");
        if (apiKey != null && !apiKey.isBlank()) {
            try {
                SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                factory.setConnectTimeout(5000);
                factory.setReadTimeout(5000);
                RestTemplate restTemplate = new RestTemplate(factory);
                String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat
                        + "&lon=" + lng + "&appid=" + apiKey + "&units=metric";
                String body = restTemplate.getForObject(url, String.class);
                JsonNode root = mapper.readTree(body);
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("temperature", root.path("main").path("temp").asDouble());
                out.put("humidity", root.path("main").path("humidity").asDouble());
                out.put("pressure", root.path("main").path("pressure").asDouble());
                out.put("wind_speed", root.path("wind").path("speed").asDouble());
                double rain1h = root.path("rain").path("1h").asDouble(0.0);
                out.put("rainfall_1h", rain1h);
                String desc = "";
                if (root.has("weather") && root.path("weather").isArray()
                        && root.path("weather").size() > 0) {
                    desc = root.path("weather").get(0).path("description").asText("");
                }
                out.put("description", desc);
                out.put("simulated", false);
                return out;
            } catch (Exception e) {
                // fall through to simulation
            }
        }
        return simulated();
    }

    private Map<String, Object> simulated() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("temperature", round1(r.nextDouble(22.0, 35.0)));
        out.put("humidity", round1(r.nextDouble(60, 95)));
        out.put("pressure", round1(r.nextDouble(990, 1015)));
        out.put("wind_speed", round1(r.nextDouble(2.0, 15.0)));
        double rain = r.nextDouble() > 0.5 ? round1(r.nextDouble(0, 20)) : 0;
        // match Flask: int 0 when no rain
        out.put("rainfall_1h", rain == 0 ? 0 : rain);
        out.put("description", "simulated");
        out.put("simulated", true);
        return out;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
