/* MapManager — Leaflet layers for VarshaSetu dashboard.
 * Contract:
 *  GET /api/stations -> {status, data:[{name,state,lat,lng}]}
 *  inundation data  -> {flood_risk_level, risk_level, estimated_water_depth_cm,
 *                       affected_area_percentage, risk_zones:[{zone_id,lat,lng,radius_km,estimated_depth_cm}]}
 */

class MapManager {
    constructor() {
        this.map = null;
        this.layers = {
            heatmap: null,
            inundation: null,
            cities: null,
            stations: null
        };
        this.onCitySelect = null; // callback assigned by dashboard.js
        this.colors = {
            RED: '#ef4444',
            ORANGE: '#f97316',
            YELLOW: '#f59e0b',
            GREEN: '#10b981'
        };
    }

    initMap(containerId) {
        const el = document.getElementById(containerId);
        if (!el) {
            console.error(`MapManager: container #${containerId} not found`);
            return;
        }
        if (this.map) {
            this.map.remove();
            this.map = null;
        }

        // Center on India
        this.map = L.map(containerId, { zoomControl: false }).setView([22.5, 82.0], 5);

        L.control.zoom({ position: 'bottomright' }).addTo(this.map);

        // Esri dark tiles
        L.tileLayer(
            'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}',
            {
                attribution: '&copy; Esri, HERE, Garmin, &copy; OpenStreetMap contributors, and the GIS User Community',
                maxZoom: 16
            }
        ).addTo(this.map);

        // Layer groups
        this.layers.inundation = L.layerGroup().addTo(this.map);
        this.layers.heatmap = L.layerGroup().addTo(this.map);
        this.layers.cities = L.layerGroup().addTo(this.map);
        this.layers.stations = L.layerGroup(); // off by default

        const overlays = {
            'Rainfall Heatmap': this.layers.heatmap,
            'Inundation Zones': this.layers.inundation,
            'Cities': this.layers.cities,
            'Weather Stations': this.layers.stations
        };
        L.control.layers(null, overlays, { position: 'topright' }).addTo(this.map);

        this.addLegend();
    }

    static _num(v, fallback) {
        const n = Number(v);
        return Number.isFinite(n) ? n : fallback;
    }

    static _validLatLng(lat, lng) {
        return Number.isFinite(lat) && Number.isFinite(lng) &&
            lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180;
    }

    static _escapeAttr(s) {
        return String(s == null ? '' : s).replace(/\\/g, '\\\\').replace(/'/g, "\\'");
    }

    static _escapeHtml(s) {
        return String(s == null ? '' : s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }

    updateRainfallHeatmap(stations) {
        if (!this.layers.heatmap) return;
        this.layers.heatmap.clearLayers();
        if (!Array.isArray(stations) || stations.length === 0) return;
        if (typeof L.heatLayer !== 'function') return; // leaflet.heat not loaded

        const heatData = [];
        stations.forEach((st, i) => {
            const lat = MapManager._num(st && st.lat, NaN);
            const lng = MapManager._num(st && st.lng, NaN);
            if (!MapManager._validLatLng(lat, lng)) return; // FIX: skip instead of random coords
            // FIX: old code read st.rain_today which never exists -> always random.
            // Use real rainfall fields when present, else a stable deterministic fallback.
            let intensity = 0.3 + ((i * 37) % 50) / 100; // deterministic 0.3..0.8
            const rain = st.rainfall_1h ?? st.rainfall_mm ?? st.rain_today ?? st.predicted_mm;
            const rainNum = Number(rain);
            if (Number.isFinite(rainNum)) {
                intensity = Math.max(0.05, Math.min(1, rainNum / 150));
            }
            heatData.push([lat, lng, intensity]);
        });

        if (heatData.length === 0) return;

        const heatLayer = L.heatLayer(heatData, {
            radius: 25,
            blur: 15,
            maxZoom: 10,
            minOpacity: 0.35,
            gradient: {
                0.2: 'green',
                0.4: 'yellow',
                0.6: 'orange',
                0.8: 'red',
                1.0: 'darkred'
            }
        });
        this.layers.heatmap.addLayer(heatLayer);
    }

    updateInundationZones(data) {
        if (!this.layers.inundation) return;
        this.layers.inundation.clearLayers();
        if (!data || !Array.isArray(data.risk_zones)) return;

        data.risk_zones.forEach((zone) => {
            const lat = MapManager._num(zone && zone.lat, NaN);
            const lng = MapManager._num(zone && zone.lng, NaN);
            if (!MapManager._validLatLng(lat, lng)) return;
            const depth = MapManager._num(zone.estimated_depth_cm, 0);
            const radiusKm = MapManager._num(zone.radius_km, 2);
            if (radiusKm <= 0) return;

            // depth -> color/opacity
            const opacity = Math.min(0.8, Math.max(0.25, 0.3 + depth / 500));
            let color = '#3b82f6';
            if (depth > 200) color = '#1d4ed8';
            if (depth > 400) color = '#1e3a8a';

            const circle = L.circle([lat, lng], {
                color,
                fillColor: color,
                fillOpacity: opacity,
                radius: radiusKm * 1000,
                weight: 1
            });

            circle.bindPopup(
                '<div style="text-align:center;">' +
                '<strong>Inundation Zone ' + MapManager._escapeHtml(zone.zone_id || '') + '</strong><br>' +
                'Depth: ' + depth + ' cm<br>' +
                'Radius: ' + radiusKm + ' km</div>'
            );
            this.layers.inundation.addLayer(circle);
        });
    }

    updateCityMarkers(cities, alertsData) {
        if (!this.layers.cities) return;
        this.layers.cities.clearLayers();
        if (!Array.isArray(cities)) return;
        if (!Array.isArray(alertsData)) alertsData = [];

        const alertMap = {};
        alertsData.forEach((a) => {
            if (a && a.city) alertMap[a.city] = String(a.level || 'GREEN').toUpperCase();
        });

        cities.forEach((city) => {
            if (!city || !city.name) return;
            // FIX: normalize level to uppercase; default GREEN
            const rawLevel = alertMap[city.name] || 'GREEN';
            const level = this.colors[rawLevel] ? rawLevel : 'GREEN';
            const color = this.colors[level];

            const lat = MapManager._num(city.lat, NaN);
            const lng = MapManager._num(city.lng, NaN);
            if (!MapManager._validLatLng(lat, lng)) return; // FIX: skip instead of random coords

            const marker = L.circleMarker([lat, lng], {
                radius: level === 'RED' ? 10 : 6,
                fillColor: color,
                color: '#fff',
                weight: 1.5,
                opacity: 1,
                fillOpacity: 0.8
            });

            const safeName = MapManager._escapeAttr(city.name);
            const safeLabel = MapManager._escapeHtml(city.name);
            marker.bindPopup(
                '<div><h4 style="margin:0 0 4px;">' + safeLabel + '</h4>' +
                '<p style="margin:0;">Alert Level: <strong style="color:' + color + '">' + level + '</strong></p>' +
                '<button onclick="window.mapManager.triggerCitySelect(\'' + safeName + '\')" ' +
                'style="margin-top:6px;padding:3px 8px;background:#4facfe;border:none;color:white;border-radius:3px;cursor:pointer;">' +
                'View Details</button></div>'
            );
            marker.cityName = city.name;
            this.layers.cities.addLayer(marker);
        });
    }

    updateStationMarkers(stations) {
        if (!this.layers.stations) return;
        this.layers.stations.clearLayers();
        if (!Array.isArray(stations)) return;

        stations.forEach((st) => {
            if (!st || !st.name) return;
            const lat = MapManager._num(st.lat, NaN);
            const lng = MapManager._num(st.lng, NaN);
            if (!MapManager._validLatLng(lat, lng)) return;
            const marker = L.circleMarker([lat, lng], {
                radius: 3,
                fillColor: '#9ca3af',
                color: '#fff',
                weight: 1,
                fillOpacity: 0.8
            });
            marker.bindPopup('<p style="margin:0;">Station: ' + MapManager._escapeHtml(st.name) + '</p>');
            this.layers.stations.addLayer(marker);
        });
    }

    highlightCity(cityName) {
        if (!this.map || !this.layers.cities || !cityName) return;
        let targetMarker = null;
        this.layers.cities.eachLayer((marker) => {
            if (marker.cityName === cityName) targetMarker = marker;
        });
        if (targetMarker) {
            this.map.flyTo(targetMarker.getLatLng(), 9, { duration: 1.5 });
            // open popup after fly starts (popup opens at marker regardless of animation)
            setTimeout(() => { try { targetMarker.openPopup(); } catch (e) { /* noop */ } }, 400);
        }
    }

    clearInundationZones() {
        if (this.layers.inundation) this.layers.inundation.clearLayers();
    }

    addLegend() {
        if (!this.map) return;
        const legend = L.control({ position: 'bottomleft' });
        legend.onAdd = function () {
            const div = L.DomUtil.create('div', 'info legend map-legend');
            div.innerHTML =
                '<div style="font-weight:bold;margin-bottom:8px;">Map Legend</div>' +
                '<div class="legend-item"><div class="legend-color" style="background:#ef4444"></div> RED Alert</div>' +
                '<div class="legend-item"><div class="legend-color" style="background:#f97316"></div> ORANGE Alert</div>' +
                '<div class="legend-item"><div class="legend-color" style="background:#f59e0b"></div> YELLOW Alert</div>' +
                '<div class="legend-item"><div class="legend-color" style="background:#10b981"></div> GREEN Alert</div>' +
                '<hr style="border-color:rgba(255,255,255,0.1);margin:8px 0;">' +
                '<div class="legend-item"><div class="legend-color" style="background:linear-gradient(to right, lightblue, darkblue)"></div> Inundation Depth</div>';
            return div;
        };
        legend.addTo(this.map);
    }

    triggerCitySelect(cityName) {
        if (typeof this.onCitySelect === 'function') {
            this.onCitySelect(cityName);
        }
    }
}

window.mapManager = new MapManager();
