package com.aapdasetu.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class City {
    private String name;
    private String state;
    private double lat;
    private double lng;
    private double elevation;

    @JsonProperty("flood_risk_rating")
    private double floodRiskRating;

    @JsonProperty("drainage_capacity_index")
    private double drainageCapacityIndex;

    private long population;

    @JsonProperty("is_coastal")
    private boolean coastal;

    private String country;

    @JsonProperty("historical_flood_years")
    private List<Integer> historicalFloodYears;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public double getElevation() { return elevation; }
    public void setElevation(double elevation) { this.elevation = elevation; }

    public double getFloodRiskRating() { return floodRiskRating; }
    public void setFloodRiskRating(double floodRiskRating) { this.floodRiskRating = floodRiskRating; }

    public double getDrainageCapacityIndex() { return drainageCapacityIndex; }
    public void setDrainageCapacityIndex(double drainageCapacityIndex) { this.drainageCapacityIndex = drainageCapacityIndex; }

    public long getPopulation() { return population; }
    public void setPopulation(long population) { this.population = population; }

    public boolean isCoastal() { return coastal; }
    public void setCoastal(boolean coastal) { this.coastal = coastal; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public List<Integer> getHistoricalFloodYears() { return historicalFloodYears; }
    public void setHistoricalFloodYears(List<Integer> historicalFloodYears) { this.historicalFloodYears = historicalFloodYears; }
}
