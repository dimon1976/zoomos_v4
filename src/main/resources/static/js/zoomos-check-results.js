(function () {
'use strict';

const RUN_ID   = document.getElementById('run-id').value;
const BASE_URL = document.getElementById('base-url').value;

const STATUS_ORDER = ['CRITICAL','WARNING','TREND','IN_PROGRESS','OK'];
const STATUS_LABEL = { CRITICAL:'КРИТИЧНО', WARNING:'ПРЕДУПРЕЖДЕНИЕ', TREND:'ТРЕНД', IN_PROGRESS:'В процессе', OK:'OK' };

const REASON_ICON = {
    NOT_FOUND:'fa-times-circle', DEADLINE_MISSED:'fa-times-circle', STALLED:'fa-pause-circle',
    STOCK_ZERO:'fa-arrow-down', STOCK_DROP:'fa-arrow-down', STOCK_TREND_DOWN:'fa-arrow-down',
    CITIES_MISSING:'fa-exclamation-circle', ACCOUNT_MISSING:'fa-exclamation-circle', CATEGORY_MISSING:'fa-exclamation-circle',
    ERROR_GROWTH:'fa-exclamation-triangle', SPEED_TREND:'fa-tachometer-alt', SPEED_SPIKE:'fa-tachometer-alt',
    IN_PROGRESS_OK:'fa-hourglass-half', IN_PROGRESS_RISK:'fa-exclamation-circle',
};

const REASON_SORT_ORDER = {
    NOT_FOUND:1, STALLED:2, DEADLINE_MISSED:3, IN_PROGRESS_RISK:3,
    STOCK_ZERO:4, STOCK_DROP:5, CITIES_MISSING:6, ACCOUNT_MISSING:7, CATEGORY_MISSING:8,
    ERROR_GROWTH:1, STOCK_TREND_DOWN:2, SPEED_SPIKE:1, SPEED_TREND:2,
};

const REASON_SPARKLINE_COLOR = {
    STOCK_ZERO:'#dc3545', STOCK_DROP:'#dc3545', STOCK_TREND_DOWN:'#dc3545',
    ERROR_GROWTH:'#fd7e14', SPEED_SPIKE:'#0d6efd', SPEED_TREND:'#0d6efd',
};

const MONTHS_RU = ['янв','фев','мар','апр','мая','июн','июл','авг','сен','окт','ноя','дек'];

// ── State ────────────────────────────────────────────────────────────────
let allResults  = [];
let activeFilters = new Set(['CRITICAL','WARNING']);
let activeType  = 'ALL';
let searchText  = '';

// ── Helpers ──────────────────────────────────────────────────────────────
function statusPriority(s) { return STATUS_ORDER.indexOf(s); }

function fmtDate(iso) {
    if (!iso) return '—';
    try {
        const d = new Date(iso);
        const dd = String(d.getDate()).padStart(2,'0');
        const mm = String(d.getMonth()+1).padStart(2,'0');
        return dd+'.'+mm+' '+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0');
    } catch { return iso; }
}

function fmtDateShort(iso) {
    if (!iso) return '';
    try {
        const d = new Date(iso + 'T00:00:00');
        return d.getDate() + '\u00a0' + MONTHS_RU[d.getMonth()];
    } catch { return iso; }
}

function esc(s) {
    if (!s) return '';
    return String(s).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}
function fmt(v, fallback) { return (v!==null && v!==undefined) ? v : (fallback !== undefined ? fallback : '—'); }
function deltaHtml(cur,bl) {
    if (cur===null||bl===null||bl===0) return '';
    const d=Math.round((cur-bl)/bl*100);
    const cls=d>=0?'text-success':'text-danger';
    return '<span class="metric-delta '+cls+'">'+(d>=0?'+':'')+d+'%</span>';
}

function getFirstReason(r) {
    const all = [...(r.statusReasons||[]), ...(r.cityResults||[]).flatMap(cr=>cr.issues||[])];
    all.sort((a,b)=>(statusPriority(a.level)-statusPriority(b.level))||((REASON_SORT_ORDER[a.reason]||99)-(REASON_SORT_ORDER[b.reason]||99)));
    // Никогда не показывать TREND в шапке если есть CRITICAL/WARNING
    const hasCritOrWarn = all.some(i => i.level==='CRITICAL' || i.level==='WARNING');
    return all.find(i => hasCritOrWarn ? (i.level==='CRITICAL'||i.level==='WARNING') : true) || null;
}

function sparklineColor(r) {
    const first = getFirstReason(r);
    if (!first) return '#6c757d';
    return REASON_SPARKLINE_COLOR[first.reason]||'#6c757d';
}

function sortByReasonPriority(a, b) {
    const sp = statusPriority(a.status) - statusPriority(b.status);
    if (sp !== 0) return sp;
    const ra = getFirstReason(a), rb = getFirstReason(b);
    const rp = (REASON_SORT_ORDER[ra?.reason]||99)-(REASON_SORT_ORDER[rb?.reason]||99);
    if (rp !== 0) return rp;
    return (a.siteName||'').localeCompare(b.siteName||'');
}

// ── Render site ──────────────────────────────────────────────────────────
function renderSiteResult(r) {
    const status = r.status;
    const cities = r.cityResults || [];
    const multi  = cities.length > 1;

    const cityTag    = multi ? cities.length+' городов' : (r.cityName || cities[0]?.cityName || '');
    const accountTag = (!multi && r.accountName)
        ? '<span class="account-tag" title="'+esc(r.accountName)+'">'+esc(r.accountName)+'</span>' : '';
    const firstR  = getFirstReason(r);
    const typeTag = '<span class="badge bg-light text-secondary border" style="font-size:.68rem">'+(r.checkType||'')+'</span>';

    let bodyHtml = '';
    if (multi) {
        bodyHtml += renderCitiesTable(cities);
        if (r.statusReasons && r.statusReasons.length) {
            bodyHtml += renderIssueRows(r.statusReasons);
        }
    } else {
        const allIssues = [...(cities[0]?.issues||[]), ...(r.statusReasons||[])];
        bodyHtml += renderIssueRows(allIssues);
        bodyHtml += renderMetrics(r, status);
    }

    const historyHref = '/zoomos/check/history?shop='+esc(r.siteName);
    const historyUrl  = BASE_URL ? BASE_URL+'/shops-parser/'+esc(r.siteName)+'/parsing-history' : '#';
    bodyHtml += '<div class="d-flex gap-2 mt-3">'
        +'<a href="'+historyHref+'" class="btn btn-xs btn-outline-secondary" style="font-size:.78rem;padding:2px 10px">'
        +'<i class="fas fa-history me-1"></i>История</a>'
        +(BASE_URL?'<a href="'+historyUrl+'" target="_blank" class="btn btn-xs btn-outline-secondary" style="font-size:.78rem;padding:2px 10px">'
        +'<i class="fas fa-external-link-alt me-1"></i>Ссылка</a>':'')
        +'</div>';

    return '<div class="site-group border-'+status+'" data-status="'+status
        +'" data-type="'+(r.checkType||'')+'" data-site="'+esc(r.siteName).toLowerCase()+'">'
        +'<div class="group-header" onclick="toggleGroup(this)">'
        +'<span class="status-pill pill-'+status+'"></span>'
        +'<span class="status-badge badge-'+status+'">'+STATUS_LABEL[status]+'</span>'
        +'<span class="site-name">'+esc(r.siteName)+'</span>'
        +(cityTag?'<span class="city-tag">'+esc(cityTag)+'</span>':'')
        +accountTag+typeTag
        +(firstR?'<span class="first-reason">'+esc(firstR.shortLabel||firstR.message)+'</span>':'')
        +'<button class="expand-btn ms-auto" tabindex="-1" aria-hidden="true"><i class="fas fa-chevron-down"></i></button>'
        +'</div>'
        +'<div class="group-body">'+bodyHtml+'</div>'
        +'</div>';
}

function renderIssueRows(issues, cardStatus) {
    if (!issues || !issues.length) return '';
    const seen = new Set();
    const unique = issues.filter(r => { if(seen.has(r.reason)) return false; seen.add(r.reason); return true; });

    const main  = unique.filter(r => r.level==='CRITICAL'||r.level==='WARNING'||r.level==='IN_PROGRESS');
    const trend = unique.filter(r => r.level==='TREND');

    function rowHtml(r, dimmed) {
        const icon = REASON_ICON[r.reason]||'fa-circle';
        return '<div class="issue-row issue-'+(r.level||'CRITICAL')+'"'+(dimmed?' style="color:#6c757d"':'')+'>'+
            '<i class="fas '+icon+' issue-icon"></i><span>'+esc(r.message)+'</span></div>';
    }

    let html = main.length ? '<div class="mb-2">'+main.map(r=>rowHtml(r,false)).join('')+'</div>' : '';

    // TREND с разделителем и пометкой "Дополнительно:" если есть основные
    if (trend.length) {
        const hasPrimary = main.length > 0;
        const separator = hasPrimary
            ? '<div class="d-flex align-items-center gap-2 my-1" style="color:#adb5bd;font-size:.75rem">'
              +'<hr style="flex:1;margin:0;border-color:#dee2e6"><span>Дополнительно</span>'
              +'<hr style="flex:1;margin:0;border-color:#dee2e6"></div>'
            : '';
        html += separator + '<div class="mb-2">'+trend.map(r=>rowHtml(r, hasPrimary)).join('')+'</div>';
    }

    return html;
}

function renderMetrics(r, status) {
    const s = r.latestStat;
    if (!s) return '';
    const inStockDelta = deltaHtml(s.inStock, r.baselineInStock);
    const baselineStr  = r.baselineInStock!==null ? ' <span class="metric-delta">база: '+Math.round(r.baselineInStock)+'</span>' : '';
    const updStr = s.updatedTime
        ? '<div class="metric-label">Обновлено</div><div class="metric-value" style="font-size:.8rem">'+fmtDate(s.updatedTime)+'</div>' : '';

    let progressBlock = '';
    if (s.isFinished === false) {
        const pct = s.completionPercent || 0;
        const estStr  = r.estimatedFinish ? fmtDate(r.estimatedFinish) : '—';
        const reliable = r.estimatedFinishReliable !== false;
        progressBlock = '<div class="mt-2 p-2 rounded" style="background:#f0f0ff;border:1px solid #dde;">'
            +'<div class="d-flex align-items-center justify-content-between mb-1">'
            +'<span class="small fw-semibold" style="color:var(--c-in-progress)"><i class="fas fa-spinner fa-spin me-1"></i>В процессе</span>'
            +'<span class="small">'+pct+'%</span></div>'
            +'<div class="mini-progress"><div class="mini-progress-fill" style="width:'+pct+'%"></div></div>'
            +'<div class="mt-1 small text-muted">Ожидаемое завершение: <strong>'+estStr+'</strong>'
            +(!reliable?'<span class="text-warning ms-1">(прогноз неточный)</span>':'')
            +'</div></div>';
    }

    return '<div class="metrics-grid">'
        +'<div class="metric-cell"><div class="metric-label">В наличии</div>'
        +'<div class="metric-value">'+fmt(s.inStock)+' '+inStockDelta+'</div>'+baselineStr+'</div>'
        +'<div class="metric-cell"><div class="metric-label">Товаров</div><div class="metric-value">'+fmt(s.totalProducts)+'</div></div>'
        +'<div class="metric-cell"><div class="metric-label">Ошибок</div>'
        +'<div class="metric-value '+((s.errorCount||0)>0?'text-danger':'')+'">'+fmt(s.errorCount,0)+'</div></div>'
        +'<div class="metric-cell"><div class="metric-label">Завершено</div>'
        +'<div class="metric-value">'+(s.completionPercent!==null?s.completionPercent+'%':'—')+'</div></div>'
        +'<div class="metric-cell"><div class="metric-label">Время</div>'
        +'<div class="metric-value">'+(s.parsingDurationMinutes!==null?s.parsingDurationMinutes+' мин':'—')+'</div></div>'
        +(updStr?'<div class="metric-cell">'+updStr+'</div>':'')
        +'</div>'
        +progressBlock
        +renderSparkline(r, status);
}

function renderCitiesTable(cities) {
    const rows = cities.map(cr => {
        const lbl = STATUS_LABEL[cr.status]||cr.status;
        const fi  = cr.issues && cr.issues.length ? esc(cr.issues[0].message) : '';
        return '<tr><td>'+esc(cr.cityName||cr.cityId||'—')+'</td>'
            +'<td><span class="status-badge badge-'+cr.status+'" style="font-size:.68rem">'+lbl+'</span></td>'
            +'<td class="text-end">'+fmt(cr.inStock)+'</td>'
            +'<td class="text-muted" style="font-size:.78rem">'+fi+'</td></tr>';
    }).join('');
    return '<table class="table table-sm cities-table mb-2"><thead><tr>'
        +'<th>Город</th><th>Статус</th><th class="text-end">В наличии</th><th>Причина</th>'
        +'</tr></thead><tbody>'+rows+'</tbody></table>';
}

// ── Sparkline ─────────────────────────────────────────────────────────────
const sparkCharts = new Map();

function renderSparkline(r, status) {
    if (!r.inStockHistory || r.inStockHistory.length < 2) return '';
    const color  = sparklineColor(r);
    const safeId = 'spark-'+(r.siteName+'-'+(r.cityId||'')).replace(/[^a-zA-Z0-9]/g,'_');
    const data   = JSON.stringify(r.inStockHistory).replace(/'/g,"&#39;");
    const bl     = (r.baselineInStock!==null && r.baselineInStock!==undefined) ? String(Math.round(r.baselineInStock)) : '';
    const first  = r.inStockHistory[0]?.date||'';
    const last   = r.inStockHistory[r.inStockHistory.length-1]?.date||'';
    const label  = (first&&last) ? 'inStock за '+fmtDateShort(first)+' — '+fmtDateShort(last) : 'inStock';
    return '<div class="mt-2 p-2 rounded" style="border:1px solid #dee2e6;display:inline-block;">'
        +'<div class="metric-label mb-1">'+esc(label)+'</div>'
        +'<canvas id="'+safeId+'" width="280" height="100"'
        +' data-sparkline=\''+data+'\''
        +' data-color="'+color+'" data-baseline="'+bl+'"></canvas>'
        +'</div>';
}

function initSparklines(container) {
    container.querySelectorAll('canvas[data-sparkline]').forEach(canvas => {
        const id = canvas.id;
        if (sparkCharts.has(id)) return;
        try {
            const points = JSON.parse(canvas.dataset.sparkline||'[]');
            const color  = canvas.dataset.color||'#0d6efd';
            const blVal  = canvas.dataset.baseline ? parseFloat(canvas.dataset.baseline) : null;
            const ctx    = canvas.getContext('2d');
            const datasets = [{
                data: points.map(p=>p.inStock),
                borderColor:color, borderWidth:2, fill:false,
                pointRadius:3, pointBackgroundColor:color, tension:0.3
            }];
            if (blVal !== null) {
                datasets.push({
                    data: points.map(()=>blVal),
                    borderColor:'#adb5bd', borderWidth:1,
                    borderDash:[4,4], pointRadius:0, fill:false
                });
            }
            sparkCharts.set(id, new Chart(ctx, {
                type:'line',
                data:{ labels:points.map(p=>p.date), datasets },
                options:{
                    responsive:false, animation:false,
                    plugins:{ legend:{display:false}, tooltip:{enabled:false} },
                    scales:{ x:{display:false}, y:{display:false} }
                }
            }));
        } catch(e) {}
    });
}

// ── OK section ────────────────────────────────────────────────────────────
function renderOkSection(results) {
    const okList = results.filter(r => r.status === 'OK');
    if (!okList.length) return;
    document.getElementById('ok-section').classList.remove('d-none');
    document.getElementById('ok-count').textContent = okList.length;
    const tbody = document.getElementById('ok-table-body');
    tbody.innerHTML = okList.map(r => {
        const inStock = r.latestStat ? fmt(r.latestStat.inStock) : '—';
        return '<tr><td>'+esc(r.siteName)+'</td><td class="text-muted">'+esc(r.cityName||'')+'</td><td class="text-end">'+inStock+'</td></tr>';
    }).join('');
}

function updateCounters(results) {
    const counts = { CRITICAL:0, WARNING:0, TREND:0, IN_PROGRESS:0, OK:0 };
    results.forEach(r => { if (counts[r.status]!==undefined) counts[r.status]++; });
    Object.entries(counts).forEach(([s,n]) => {
        const el = document.getElementById('cnt-'+s);
        if (el) el.textContent = n;
    });
}

// ── Main render ───────────────────────────────────────────────────────────
function render() {
    const list   = document.getElementById('results-list');
    const search = searchText.toLowerCase();
    const visible = allResults.filter(r => {
        if (r.status === 'OK') return false;
        if (activeFilters.size && !activeFilters.has(r.status)) return false;
        if (activeType !== 'ALL' && r.checkType !== activeType) return false;
        if (search && !r.siteName.toLowerCase().includes(search)) return false;
        return true;
    });
    visible.sort(sortByReasonPriority);
    if (!visible.length) {
        list.innerHTML = '<div class="empty-state"><i class="fas fa-check-circle fa-2x text-success mb-3 d-block"></i>'
            +(allResults.length ? 'Нет проблем по выбранным фильтрам' : 'Нет данных')+'</div>';
        return;
    }
    list.innerHTML = visible.map(renderSiteResult).join('');
}

// ── Toggle handlers ───────────────────────────────────────────────────────
window.toggleGroup = function(header) {
    const body    = header.nextElementSibling;
    const btn     = header.querySelector('.expand-btn');
    const opening = !body.classList.contains('open');
    body.classList.toggle('open');
    btn.classList.toggle('open');
    if (opening) requestAnimationFrame(() => initSparklines(body));
};

window.toggleOk = function(btn) {
    const list = document.getElementById('ok-list');
    list.classList.toggle('d-none');
    btn.textContent = btn.textContent.replace(/[▾▴]/, list.classList.contains('d-none') ? '▾' : '▴');
};

// ── Filters (Task 3: режим одиночного выбора) ─────────────────────────────
function updateFilterButtons() {
    document.querySelectorAll('#filter-bar .filter-btn').forEach(btn => {
        const f = btn.dataset.filter;
        if (f === 'ALL') {
            btn.classList.toggle('active', activeFilters.size === 0);
        } else {
            btn.classList.toggle('active', activeFilters.has(f));
        }
    });
}

document.getElementById('filter-bar').addEventListener('click', e => {
    const btn = e.target.closest('.filter-btn');
    if (!btn) return;
    const f = btn.dataset.filter;
    if (f === 'ALL') {
        activeFilters.clear();
    } else if (e.ctrlKey || e.metaKey) {
        if (activeFilters.has(f)) activeFilters.delete(f);
        else activeFilters.add(f);
    } else {
        if (activeFilters.size === 1 && activeFilters.has(f)) activeFilters.clear();
        else { activeFilters.clear(); activeFilters.add(f); }
    }
    updateFilterButtons();
    render();
});

document.getElementById('type-filter').addEventListener('click', e => {
    const btn = e.target.closest('button[data-type]');
    if (!btn) return;
    activeType = btn.dataset.type;
    document.querySelectorAll('#type-filter button').forEach(b => b.classList.toggle('active', b === btn));
    render();
});

document.getElementById('site-search').addEventListener('input', e => {
    searchText = e.target.value;
    render();
});

// ── Fetch ─────────────────────────────────────────────────────────────────
fetch('/zoomos/check/analyze/' + RUN_ID)
    .then(r => { if (!r.ok) throw new Error('HTTP '+r.status); return r.json(); })
    .then(results => {
        document.getElementById('loading').classList.add('d-none');
        allResults = results;
        updateCounters(results);
        renderOkSection(results);
        updateFilterButtons();
        render();
    })
    .catch(err => {
        console.error('analyze fetch failed:', err);
        document.getElementById('loading').classList.add('d-none');
        document.getElementById('error-msg').classList.remove('d-none');
    });

})();
