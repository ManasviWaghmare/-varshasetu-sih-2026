/* Architecture section — renders /api/architecture into #arch-flow
 * and /api/data-sources status into #data-sources-grid.
 * Graceful fallback: if fetch fails, static HTML is left untouched.
 */

document.addEventListener('DOMContentLoaded', () => {
    renderArchitecture();
    renderDataSources();
});

function escHtmlArch(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;')
        .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

async function renderArchitecture() {
    const flow = document.getElementById('arch-flow');
    if (!flow) return;
    const hadStatic = flow.children.length > 0;

    try {
        const res = await fetch('/api/architecture');
        if (!res.ok) return; // fallback: leave static HTML
        const json = await res.json();
        if (json.status !== 'success' || !json.data || !Array.isArray(json.data.layers)) return;

        flow.innerHTML = '';
        json.data.layers.forEach((layer, idx) => {
            const card = document.createElement('div');
            card.className = `arch-layer arch-${escHtmlArch(layer.color || 'blue')}`;

            const header = document.createElement('div');
            header.className = 'arch-layer-header';
            header.textContent = layer.name || `Layer ${idx + 1}`;
            card.appendChild(header);

            const items = document.createElement('div');
            items.className = 'arch-layer-items';
            (layer.items || []).forEach((item) => {
                const it = document.createElement('div');
                it.className = 'arch-item';
                const code = item.endpoint || item.module || item.element || '';
                it.innerHTML =
                    `<div class="arch-item-name">${escHtmlArch(item.name)}</div>` +
                    (item.desc ? `<div class="arch-item-desc">${escHtmlArch(item.desc)}</div>` : '') +
                    (code ? `<code class="arch-item-code">${escHtmlArch(code)}</code>` : '');
                items.appendChild(it);
            });
            card.appendChild(items);
            flow.appendChild(card);

            if (idx < json.data.layers.length - 1) {
                const arrow = document.createElement('div');
                arrow.className = 'arch-arrow';
                arrow.setAttribute('aria-hidden', 'true');
                arrow.textContent = '→';
                flow.appendChild(arrow);
            }
        });
    } catch (e) {
        // Graceful fallback: leave static HTML as-is
        console.warn('architecture fetch failed, keeping static HTML', e);
        if (!hadStatic) {
            flow.innerHTML = '<p class="text-secondary">Architecture unavailable.</p>';
        }
    }
}

async function renderDataSources() {
    const grid = document.getElementById('data-sources-grid');
    if (!grid) return;
    try {
        const res = await fetch('/api/data-sources');
        if (!res.ok) return;
        const json = await res.json();
        if (json.status !== 'success' || !json.data || !Array.isArray(json.data.sources)) return;

        grid.innerHTML = '';
        json.data.sources.forEach((src) => {
            const status = String(src.status || 'unknown').toLowerCase();
            const active = status === 'active';
            const dot = active ? '#10b981' : (status === 'simulated' ? '#f59e0b' : '#ef4444');
            const card = document.createElement('div');
            card.className = 'data-source-card';
            card.innerHTML =
                `<span class="status-dot" style="background:${dot}"></span>` +
                `<span class="data-source-name">${escHtmlArch(src.name)}</span>` +
                `<span class="data-source-type">${escHtmlArch(src.type || '')}</span>` +
                `<span class="data-source-status">${escHtmlArch(src.status)}</span>`;
            grid.appendChild(card);
        });
    } catch (e) {
        console.warn('data-sources fetch failed', e);
    }
}
