/* Dashboard — wires map, charts, alerts, city detail panels.
 * API contract (GET /api/*):
 *  /stations, /weather/<city>, /forecast/<city>, /inundation/<city>,
 *  /alerts, /alerts/generate, /model/stats
 */

// Global state
let currentCity = null;
let allCities = [];
let activeAlerts = [];
let charts = {};

const API_BASE = '/api';

document.addEventListener('DOMContentLoaded', async () => {
    if (window.mapManager) {
        window.mapManager.initMap('map');
        window.mapManager.onCitySelect = selectCity;
    } else {
        console.error('mapManager not loaded; check map_layers.js script order');
    }

    initCharts();
    setupListeners();
    await loadInitialData();
    startIntervals();
});

function setText(id, value) {
    const el = document.getElementById(id);
    if (el) el.textContent = value;
}

function escHtml(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

function setupListeners() {
    const searchInput = document.getElementById('city-search');
    const cityList = document.getElementById('city-list');
    if (searchInput && cityList) {
        searchInput.addEventListener('input', (e) => {
            const term = e.target.value.trim().toLowerCase();
            cityList.innerHTML = '';
            if (!term) return;
            allCities
                .filter((c) => c.name && c.name.toLowerCase().includes(term))
                .slice(0, 20)
                .forEach((city) => {
                    const div = document.createElement('div');
                    div.className = 'city-list-item';
                    div.textContent = city.state ? `${city.name}, ${city.state}` : city.name;
                    div.onclick = () => {
                        searchInput.value = '';
                        cityList.innerHTML = '';
                        selectCity(city.name);
                    };
                    cityList.appendChild(div);
                });
        });
    }

    const refreshBtn = document.getElementById('refresh-alerts');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', async () => {
            try {
                await fetch(`${API_BASE}/alerts/generate`);
            } catch (e) {
                console.error('alert generate failed', e);
            }
            await loadAlerts();
            // keep map markers in sync with fresh alerts
            if (window.mapManager) window.mapManager.updateCityMarkers(allCities, activeAlerts);
        });
    }
}

async function loadInitialData() {
    try {
        const res = await fetch(`${API_BASE}/stations`);
        const json = await res.json();
        if (json.status === 'success' && Array.isArray(json.data)) {
            allCities = json.data;
            setText('total-cities', String(allCities.length));
        }

        await loadAlerts();

        if (window.mapManager) {
            window.mapManager.updateCityMarkers(allCities, activeAlerts);
            window.mapManager.updateStationMarkers(allCities);
            window.mapManager.updateRainfallHeatmap(allCities);
        }

        fetchModelStats();

        if (allCities.length > 0) {
            const def = allCities.find((c) => c.name === 'Mumbai') || allCities[0];
            selectCity(def.name);
        }
    } catch (e) {
        console.error('Error loading initial data', e);
    }
}

async function loadAlerts() {
    try {
        const res = await fetch(`${API_BASE}/alerts`);
        const json = await res.json();
        if (json.status === 'success' && Array.isArray(json.data)) {
            activeAlerts = json.data;
            setText('total-alerts', String(activeAlerts.length));
            renderAlertsList();
            updateAlertTicker();
            updateAlertChart();
        }
    } catch (e) {
        console.error('Error loading alerts', e);
    }
}

function renderAlertsList() {
    const list = document.getElementById('active-alerts-list');
    if (!list) return;
    list.innerHTML = '';

    if (activeAlerts.length === 0) {
        list.innerHTML = '<p class="text-secondary text-center py-4">No active alerts.</p>';
        return;
    }

    activeAlerts.forEach((alert) => {
        const level = String(alert.level || 'GREEN').toUpperCase();
        const div = document.createElement('div');
        div.className = `alert-card ${level}`;
        // FIX: API uses issued_at; fall back to timestamp
        const when = formatDate(alert.issued_at ?? alert.timestamp);
        div.innerHTML =
            `<div class="alert-header"><span>${escHtml(alert.city)}</span><span>${escHtml(when)}</span></div>` +
            `<div>${escHtml(alert.message)}</div>`;
        div.onclick = () => { if (alert.city) selectCity(alert.city); };
        list.appendChild(div);
    });
}

function updateAlertTicker() {
    const ticker = document.getElementById('ticker-content');
    if (!ticker) return;
    if (activeAlerts.length === 0) {
        ticker.innerHTML = 'All monitoring stations reporting normal conditions. No heavy rainfall alerts active.';
        return;
    }
    const items = [...activeAlerts, ...activeAlerts]; // duplicate for seamless scroll
    ticker.innerHTML = items.map((a) => {
        const level = String(a.level || '').toUpperCase();
        const icon = level === 'RED' ? '&#128308;' : level === 'ORANGE' ? '&#128992;' : level === 'YELLOW' ? '&#128993;' : '&#128994;';
        return `<span class="ticker-item ${level}">${icon} [${escHtml(level)}] ${escHtml(a.city)}: ${escHtml(a.message)}</span>`;
    }).join('');
}

async function fetchModelStats() {
    try {
        const res = await fetch(`${API_BASE}/model/stats`);
        const json = await res.json();
        if (json.status === 'success' && json.data) {
            // FIX: API shape is {accuracy} possibly nested; coerce number safely
            const acc = Number(json.data.accuracy ?? 94.5) || 94.5;
            setText('confidence-text', `${acc}%`);
            const fill = document.getElementById('confidence-fill');
            if (fill) fill.style.strokeDasharray = `${acc}, 100`;
            setText('model-stats', `Accuracy: ${acc}% | Sources: NWP, Radar, IMD`);
        }
    } catch (e) { /* keep defaults */ }
}

async function selectCity(cityName) {
    if (!cityName) return;
    currentCity = cityName;

    const cityData = allCities.find((c) => c.name === cityName);
    if (cityData) {
        setText('selected-city-name', cityData.name);
        setText('selected-city-state', cityData.state || 'India');
        if (window.mapManager) window.mapManager.highlightCity(cityName);
    } else {
        setText('selected-city-name', cityName);
    }

    try {
        const [weatherRes, forecastRes, inundationRes] = await Promise.all([
            fetch(`${API_BASE}/weather/${encodeURIComponent(cityName)}`).catch(() => null),
            fetch(`${API_BASE}/forecast/${encodeURIComponent(cityName)}`).catch(() => null),
            fetch(`${API_BASE}/inundation/${encodeURIComponent(cityName)}`).catch(() => null)
        ]);

        if (weatherRes && weatherRes.ok) {
            const wJson = await weatherRes.json().catch(() => null);
            if (wJson && wJson.status === 'success' && wJson.data) {
                const w = wJson.data;
                // FIX: coerce with ?? fallbacks so missing keys don't render "undefined"
                setText('current-temp', `${w.temperature ?? '--'}°C`);
                setText('current-humidity', `${w.humidity ?? '--'}%`);
                setText('current-pressure', `${w.pressure ?? '--'} hPa`);
                setText('current-wind', `${w.wind_speed ?? '--'} km/h`);
                setText('current-rain', `${w.rainfall_1h ?? 0} mm`);
                const rainNum = Number(w.rainfall_1h);
                if (Number.isFinite(rainNum)) {
                    const maxEl = document.getElementById('max-rain');
                    if (maxEl) {
                        const curMax = parseFloat(maxEl.textContent) || 0;
                        if (rainNum > curMax) maxEl.textContent = `${rainNum} mm`;
                    }
                }
            }
        }

        if (forecastRes && forecastRes.ok) {
            const fJson = await forecastRes.json().catch(() => null);
            if (fJson && fJson.status === 'success' && Array.isArray(fJson.data)) {
                updateRainfallChart(fJson.data);
            }
        }

        if (inundationRes && inundationRes.ok) {
            const iJson = await inundationRes.json().catch(() => null);
            if (iJson && iJson.status === 'success' && iJson.data) {
                updateInundationPanel(iJson.data);
                if (window.mapManager) window.mapManager.updateInundationZones(iJson.data);
            }
        } else if (window.mapManager) {
            window.mapManager.clearInundationZones();
        }
    } catch (e) {
        console.error('Error fetching city data', e);
    }
}
window.selectCity = selectCity;

function updateInundationPanel(data) {
    const badge = document.getElementById('risk-badge');
    // FIX: handle both flood_risk_level (lowercase) and risk_level (upper)
    const raw = data.flood_risk_level ?? data.risk_level ?? 'low';
    const level = String(raw).toLowerCase();
    const label = String(raw).toUpperCase();
    if (badge) {
        badge.textContent = label;
        badge.className = `risk-level-badge ${level}`;
    }

    const depth = Number(data.estimated_water_depth_cm) || 0;
    setText('est-depth', `${depth}cm`);

    // depth bar, max 500cm
    const pct = Math.min(100, Math.max(0, (depth / 500) * 100));
    const bar = document.getElementById('depth-bar');
    if (bar) {
        bar.style.width = `${pct}%`;
        bar.style.background = (level === 'severe' || level === 'extreme' || level === 'high')
            ? 'linear-gradient(90deg, #f97316, #ef4444)'
            : 'linear-gradient(90deg, #4facfe, #00f2fe)';
    }

    const area = Number(data.affected_area_percentage) || 0;
    setText('affected-area', `${area}%`);

    const list = document.getElementById('risk-zones-list');
    if (list) {
        list.innerHTML = '';
        if (Array.isArray(data.risk_zones) && data.risk_zones.length > 0) {
            data.risk_zones.forEach((z) => {
                const row = document.createElement('div');
                row.className = 'zone-item';
                row.innerHTML = `<span>Zone ${escHtml(z.zone_id ?? '?')}</span><span>Depth: ${escHtml(z.estimated_depth_cm ?? '?')}cm</span>`;
                list.appendChild(row);
            });
        } else {
            list.innerHTML = '<div class="text-secondary">No specific risk zones identified.</div>';
        }
    }

    const advisory = document.getElementById('evacuation-advisory');
    if (advisory) {
        if (level === 'severe' || level === 'extreme') {
            advisory.innerHTML = '<strong>EVACUATION ADVISED:</strong> Move to higher ground immediately. Follow local authority guidelines.';
            advisory.style.borderLeftColor = '#ef4444';
            advisory.style.color = '#ef4444';
        } else if (level === 'high') {
            advisory.innerHTML = '<strong>WARNING:</strong> Prepare for possible evacuation. Secure belongings.';
            advisory.style.borderLeftColor = '#f97316';
            advisory.style.color = '#f97316';
        } else {
            advisory.innerHTML = 'Normal conditions. No evacuation needed.';
            advisory.style.borderLeftColor = '#10b981';
            advisory.style.color = '#e5e7eb';
        }
    }
}

function initCharts() {
    if (typeof Chart === 'undefined') {
        console.error('Chart.js not loaded');
        return;
    }
    Chart.defaults.color = '#9ca3af';
    if (Chart.defaults.font) Chart.defaults.font.family = 'Inter';

    const c1 = document.getElementById('rainfallTrendChart');
    if (c1) {
        charts.trend = new Chart(c1.getContext('2d'), {
            type: 'line',
            data: {
                labels: ['Day 1', 'Day 2', 'Day 3', 'Day 4', 'Day 5'],
                datasets: [
                    { label: 'Predicted (mm)', data: [0, 0, 0, 0, 0], borderColor: '#4facfe', tension: 0.4 },
                    { label: 'Normal (mm)', data: [10, 10, 10, 10, 10], borderColor: '#6b7280', borderDash: [5, 5] }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    y: { grid: { color: 'rgba(255,255,255,0.05)' } },
                    x: { grid: { display: false } }
                },
                plugins: { legend: { position: 'top', labels: { boxWidth: 10 } } }
            }
        });
    }

    const c2 = document.getElementById('districtComparisonChart');
    if (c2) {
        charts.district = new Chart(c2.getContext('2d'), {
            type: 'bar',
            data: {
                labels: ['Dist A', 'Dist B', 'Dist C', 'Dist D', 'Dist E'],
                datasets: [{
                    label: 'Departure %',
                    data: [45, 30, 15, -10, -25],
                    // FIX: support both Chart.js v2 (raw) and v3+ (parsed) contexts
                    backgroundColor: (ctx) => {
                        const v = ctx.raw ?? (ctx.parsed && (ctx.parsed.x ?? ctx.parsed.y));
                        return v > 0 ? '#10b981' : '#ef4444';
                    }
                }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false,
                scales: {
                    x: { grid: { color: 'rgba(255,255,255,0.05)' } },
                    y: { grid: { display: false } }
                },
                plugins: { legend: { display: false } }
            }
        });
    }

    const c3 = document.getElementById('alertDistributionChart');
    if (c3) {
        charts.alerts = new Chart(c3.getContext('2d'), {
            type: 'doughnut',
            data: {
                labels: ['RED', 'ORANGE', 'YELLOW', 'GREEN'],
                datasets: [{
                    data: [0, 0, 0, 1],
                    backgroundColor: ['#ef4444', '#f97316', '#f59e0b', '#10b981'],
                    borderWidth: 0
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '70%',
                plugins: { legend: { position: 'right', labels: { boxWidth: 12 } } }
            }
        });
    }
}

function updateRainfallChart(forecastData) {
    if (!charts.trend || !Array.isArray(forecastData)) return;
    // FIX: API uses rainfall_mm with predicted_mm as alias
    const labels = forecastData.map((d) => String(d.date || '').slice(5) || String(d.date || ''));
    const pred = forecastData.map((d) => Number(d.rainfall_mm ?? d.predicted_mm ?? 0) || 0);
    const norm = forecastData.map((d) => Number(d.normal_mm ?? 10) || 0);
    charts.trend.data.labels = labels;
    charts.trend.data.datasets[0].data = pred;
    charts.trend.data.datasets[1].data = norm;
    charts.trend.update();
}

function updateAlertChart() {
    if (!charts.alerts) return;
    const counts = { RED: 0, ORANGE: 0, YELLOW: 0, GREEN: 0 };
    activeAlerts.forEach((a) => {
        const lv = String(a.level || '').toUpperCase();
        if (counts[lv] !== undefined) counts[lv]++;
    });
    if (activeAlerts.length === 0) counts.GREEN = allCities.length || 10;
    charts.alerts.data.datasets[0].data = [counts.RED, counts.ORANGE, counts.YELLOW, counts.GREEN];
    charts.alerts.update();
}

function startIntervals() {
    // Clock (1s)
    setInterval(() => {
        const now = new Date();
        setText('real-time-clock', now.toLocaleTimeString('en-US', { hour12: false }));
        setText('current-date', now.toLocaleDateString('en-US', { weekday: 'long', year: 'numeric', month: 'short', day: 'numeric' }));
    }, 1000);

    // Data refresh (30s)
    setInterval(async () => {
        await loadAlerts();
        if (window.mapManager) window.mapManager.updateCityMarkers(allCities, activeAlerts);
        if (currentCity) selectCity(currentCity);
    }, 30000);
}

function formatDate(dateStr) {
    if (!dateStr) return '';
    try {
        const d = new Date(dateStr);
        if (isNaN(d.getTime())) return String(dateStr);
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch (e) {
        return String(dateStr);
    }
}
