package com.varshasetu.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.varshasetu.model.City;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class CityService {
    private List<City> cities = new ArrayList<>();

    @PostConstruct
    public void load() {
        try {
            ClassPathResource resource = new ClassPathResource("data/world_cities.json");
            try (InputStream in = resource.getInputStream()) {
                ObjectMapper mapper = new ObjectMapper();
                cities = mapper.readValue(in, new TypeReference<List<City>>() {});
            }
        } catch (Exception e) {
            cities = new ArrayList<>();
        }
    }

    public List<City> getAll() {
        return Collections.unmodifiableList(cities);
    }

    public City findByName(String name) {
        if (name == null) return null;
        String lower = name.toLowerCase();
        for (City c : cities) {
            if (c.getName() != null && c.getName().toLowerCase().equals(lower)) {
                return c;
            }
        }
        return null;
    }

    public int count() {
        return cities.size();
    }
}
