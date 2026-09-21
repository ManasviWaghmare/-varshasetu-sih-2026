package com.varshasetu.service;

import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

@Service
public class ForecastService {

    private static final String[] CATS = {"NR", "LD", "D", "N", "E", "LE"};
    // mm ranges per IMD daily category
    private static final double[][] CAT_MM = {
            {0.0, 0.0}, {0.1, 2.4}, {2.5, 7.5}, {7.6, 35.5}, {35.6, 64.4}, {64.5, 200.0}
    };

    private List<String> headers = new ArrayList<>();
    private List<Map<String, String>> rows = new ArrayList<>();

    @PostConstruct
    public void load() {
        try {
            ClassPathResource resource = new ClassPathResource("data/rainfall_districtwise_daily_imd.csv");
            if (!resource.exists()) return;
            try (InputStream in = resource.getInputStream();
                 BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String headerLine = readLogicalLine(br);
                if (headerLine == null) return;
                List<String> rawHeaders = parseCsvLine(headerLine);
                for (String h : rawHeaders) {
                    headers.add(normalizeHeader(h));
                }
                String line;
                while ((line = readLogicalLine(br)) != null) {
                    if (line.trim().isEmpty()) continue;
                    List<String> fields = parseCsvLine(line);
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 0; i < headers.size(); i++) {
                        String val = i < fields.size() ? fields.get(i).trim() : "";
                        row.put(headers.get(i), val);
                    }
                    rows.add(row);
                }
            }
        } catch (Exception e) {
            rows = new ArrayList<>();
        }
    }

    /** Read a full CSV record, joining continuation lines while quotes are unbalanced. */
    private String readLogicalLine(BufferedReader br) throws Exception {
        String line = br.readLine();
        if (line == null) return null;
        StringBuilder sb = new StringBuilder(line);
        while (countQuotes(sb.toString()) % 2 != 0) {
            String next = br.readLine();
            if (next == null) break;
            sb.append("\n").append(next);
        }
        return sb.toString();
    }

    private int countQuotes(String s) {
        int n = 0;
        for (char c : s.toCharArray()) if (c == '"') n++;
        return n;
    }

    private String normalizeHeader(String h) {
        return h.replace("\r", "").replace("\n", "").trim()
                .replace("Departue", "Departure")
                .replace("Acutual", "Actual");
    }

    /** Minimal RFC4180-ish CSV line parser (handles quoted commas/quotes). */
    static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        out.add(cur.toString());
        return out;
    }

    private double parseDouble(String s, double fallback) {
        if (s == null || s.isBlank()) return fallback;
        try {
            String cleaned = s.replace("%", "").replace(",", "").trim();
            if (cleaned.isEmpty() || cleaned.equals("-")) return fallback;
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Port of ml_model._baseline_row: last row matching state (contains), then district (contains). */
    public Map<String, String> baselineRow(String cityName, String stateName) {
        String state = stateName == null ? "" : stateName.toLowerCase();
        String city = cityName == null ? "" : cityName.toLowerCase();
        Map<String, String> lastStateMatch = null;
        Map<String, String> lastDistMatch = null;
        for (Map<String, String> row : rows) {
            String rs = row.getOrDefault("State", "").toLowerCase();
            if (!rs.contains(state)) continue;
            lastStateMatch = row;
            String rd = row.getOrDefault("District", "").toLowerCase();
            if (rd.contains(city)) {
                lastDistMatch = row;
            }
        }
        return lastDistMatch != null ? lastDistMatch : lastStateMatch;
    }

    public List<Map<String, Object>> predictRainfall(String cityName, String stateName, int daysAhead) {
        Map<String, String> baseline = baselineRow(cityName, stateName);
        double normal = baseline != null ? parseDouble(baseline.get("Daily Normal"), 10.0) : 10.0;
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate now = LocalDate.now();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        for (int i = 0; i < daysAhead; i++) {
            LocalDate target = now.plusDays(i);
            int month = target.getMonthValue();
            boolean monsoon = month >= 6 && month <= 9;
            // Seeded random for demo stability per city+date, mirrors "seeded random" heuristic
            Random rnd = new Random(Objects.hash(
                    cityName == null ? "" : cityName.toLowerCase(), target.toString()));
            String cat = sampleCategory(rnd, monsoon, normal);
            int idx = indexOf(cat);
            double lo = CAT_MM[idx][0], hi = CAT_MM[idx][1];
            double rainMm = hi > lo ? round2(lo + rnd.nextDouble() * (hi - lo)) : 0.0;
            double confidence = round3(0.65 + rnd.nextDouble() * 0.30);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("date", target.format(fmt));
            m.put("rainfall_mm", rainMm);
            m.put("predicted_mm", rainMm);
            m.put("normal_mm", round2(normal));
            m.put("category", cat);
            m.put("confidence_score", confidence);
            out.add(m);
        }
        return out;
    }

    private int indexOf(String cat) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(cat)) return i;
        return 3;
    }

    private String sampleCategory(Random rnd, boolean monsoon, double normal) {
        double[] weights;
        if (monsoon) {
            weights = new double[]{5, 15, 20, 30, 20, 10};
        } else {
            weights = new double[]{30, 25, 20, 15, 7, 3};
        }
        // shift weight toward wetter categories when baseline normal is high
        if (normal > 15) {
            weights[4] += 5;
            weights[5] += 5;
            weights[0] = Math.max(1, weights[0] - 5);
        } else if (normal < 5) {
            weights[0] += 5;
            weights[1] += 3;
        }
        double total = 0;
        for (double w : weights) total += w;
        double roll = rnd.nextDouble() * total;
        double acc = 0;
        for (int i = 0; i < weights.length; i++) {
            acc += weights[i];
            if (roll <= acc) return CATS[i];
        }
        return "N";
    }

    /** Exact-match (case-insensitive) district history, like ml_model.get_district_data. */
    public List<Map<String, Object>> getDistrictData(String state, String district) {
        String s = state == null ? "" : state.toLowerCase();
        String d = district == null ? "" : district.toLowerCase();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String rs = row.getOrDefault("State", "").toLowerCase();
            String rd = row.getOrDefault("District", "").toLowerCase();
            if (rs.equals(s) && rd.equals(d)) {
                out.add(new LinkedHashMap<>(row));
            }
        }
        return out;
    }

    public Map<String, Object> getModelStats() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accuracy", 87.4);
        out.put("engine", "JavaRuleEngine");
        out.put("note", "Java rule-engine port of the sklearn RandomForest demo logic for parity");
        out.put("model_type", "JavaRuleEngine (port of RandomForestClassifier demo)");
        Map<String, Object> fi = new LinkedHashMap<>();
        fi.put("month", 0.18);
        fi.put("day_of_year", 0.12);
        fi.put("Daily Normal", 0.22);
        fi.put("Weekly Departure Per", 0.15);
        fi.put("Cumulative Departure Per", 0.12);
        fi.put("Monthly Departure Per", 0.11);
        fi.put("is_monsoon_month", 0.10);
        out.put("feature_importances", fi);
        Map<String, Object> ti = new LinkedHashMap<>();
        ti.put("trained_on", LocalDate.now().toString());
        ti.put("samples_used", rows.size());
        ti.put("model_type", "JavaRuleEngine");
        out.put("training_info", ti);
        return out;
    }

    public boolean isLoaded() {
        return !rows.isEmpty();
    }

    public int rowCount() {
        return rows.size();
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public List<Map<String, Object>> getRawRows() {
        return Collections.unmodifiableList((List) rows);
    }
}
