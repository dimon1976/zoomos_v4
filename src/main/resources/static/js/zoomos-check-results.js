(function () {
'use strict';

const RUN_ID    = document.getElementById('run-id').value;
const BASE_URL  = document.getElementById('base-url').value;
const SHOP_NAME = document.getElementById('shop-name')?.value || '';
const DATE_FROM = document.getElementById('date-from')?.value || '';
const DATE_TO   = document.getElementById('date-to')?.value   || '';

const STATUS_ORDER = ['CRITICAL','WARNING','TREND','IN_PROGRESS','OK'];
const STATUS_LABEL = { CRITICAL:'КРИТИЧНО', WARNING:'ПРЕДУПРЕЖДЕНИЕ', TREND:'ТРЕНД', IN_PROGRESS:'В процессе', OK:'OK' };

const REASON_ICON = {
    NOT_FOUND:'fa-times-circle', DEADLINE_MISSED:'fa-times-circle', STALLED:'fa-pause-circle',
    STOCK_ZERO:'fa-arrow-down', STOCK_DROP:'fa-arrow-down', STOCK_TREND_DOWN:'fa-arrow-down',
    NO_PRODUCTS:'fa-box-open',
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

const CONFIG_ISSUE_LABELS = {
    MATCHING_ERRORS: 'Некорректные ссылки',
    KNOWN_ISSUE:     'Известная проблема',
    NOT_RELEVANT:    'Данные не актуальны',
    OTHER:           'Другое',
};

// ── State ────────────────────────────────────────────────────────────────
let allResults  = [];
let activeFilters = new Set(['CRITICAL','WARNING']);
let activeType  = 'ALL';
let searchText  = '';

const CHECKED_PREFIX = 'zoomos.checked.' + RUN_ID + '.';
function isChecked(siteName)          { return localStorage.getItem(CHECKED_PREFIX + siteName) === '1'; }
function isCityChecked(sn, cId)       { return localStorage.getItem(CHECKED_PREFIX + sn + '.' + cId) === '1'; }
function setChecked(sn, v)            { if (v) localStorage.setItem(CHECKED_PREFIX + sn, '1'); else localStorage.removeItem(CHECKED_PREFIX + sn); }
function setCityChecked(sn, cId, v)   { if (v) localStorage.setItem(CHECKED_PREFIX + sn + '.' + cId, '1'); else localStorage.removeItem(CHECKED_PREFIX + sn + '.' + cId); }
function getShowOkCities(siteName) {
    return localStorage.getItem('zoomos.showOkCities.' + siteName) === 'true';
}

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

function toExportDate(isoDate) {
    if (!isoDate) return '';
    const p = isoDate.split('-');
    return p.length === 3 ? p[2]+'.'+p[1]+'.'+p[0] : isoDate;
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

function getHeaderLabel(r) {
    const all = [...(r.statusReasons||[]), ...(r.cityResults||[]).flatMap(cr=>cr.issues||[])];
    const seen = new Set();
    const critLabels = all
        .filter(i => i.level === 'CRITICAL')
        .map(i => i.shortLabel || i.message)
        .filter(lbl => { if (seen.has(lbl)) return false; seen.add(lbl); return true; });
    if (critLabels.length === 1) return critLabels[0];
    if (critLabels.length === 2) return critLabels[0] + ', ' + critLabels[1];
    if (critLabels.length >= 3)  return critLabels[0] + ' и ещё ' + (critLabels.length - 1);
    // Fallback: первая не-TREND причина из уже собранного массива
    all.sort((a,b)=>(statusPriority(a.level)-statusPriority(b.level))||((REASON_SORT_ORDER[a.reason]||99)-(REASON_SORT_ORDER[b.reason]||99)));
    const hasCritOrWarn = all.some(i => i.level==='CRITICAL' || i.level==='WARNING');
    const first = all.find(i => hasCritOrWarn ? (i.level==='CRITICAL'||i.level==='WARNING') : true) || null;
    return first ? (first.shortLabel || first.message) : null;
}

window.copySiteName = function(e, el, siteName) {
    e.stopPropagation();
    navigator.clipboard.writeText(siteName).then(() => {
        const orig = el.textContent;
        el.textContent = 'Скопировано!';
        el.style.color = '#198754';
        setTimeout(() => { el.textContent = orig; el.style.color = ''; }, 1500);
    });
};

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

    const cityLabel = r.cityId && r.cityName ? r.cityId + ' — ' + r.cityName : (r.cityName || cities[0]?.cityName || '');
    const cityTag   = multi ? cities.length+' городов' : cityLabel;
    const accountTag = (!multi && r.accountName)
        ? '<span class="account-tag" title="'+esc(r.accountName)+'">'+esc(r.accountName)+'</span>' : '';
    const headerLabel = getHeaderLabel(r);
    const typeTag = '<span class="badge bg-light text-secondary border" style="font-size:.68rem">'+(r.checkType||'')+'</span>';
    const ignoreTag  = r.ignoreStock  ? '<span class="city-tag" style="color:#adb5bd;font-style:italic">(без наличия)</span>' : '';
    const masterTag  = r.masterCityId ? '<span class="city-tag" style="color:#adb5bd">(мастер: '+esc(r.masterCityId)+')</span>' : '';

    let histIcon = '';
    if (r.historyBaseUrl) {
        const histUrl = r.historyBaseUrl
            + '?dateFrom=' + encodeURIComponent(toExportDate(DATE_FROM))
            + '&dateTo='   + encodeURIComponent(toExportDate(DATE_TO))
            + '&shop='     + encodeURIComponent(r.shopParam || '-')
            + (!multi && r.cityId ? '&cityId=' + encodeURIComponent(r.cityId) : '');
        histIcon = '<a href="'+histUrl+'" target="_blank" onclick="event.stopPropagation()" title="История выкачки"'
            +' style="color:#adb5bd;font-size:.75rem;text-decoration:none;flex-shrink:0">'
            +'<i class="fas fa-external-link-alt"></i></a>';
    }

    let bodyHtml = '';
    if (multi) {
        bodyHtml += renderCitiesTable(r, cities);
        if (r.statusReasons && r.statusReasons.length) {
            bodyHtml += renderIssueRows(r.statusReasons);
        }
    } else {
        const allIssues = [...(cities[0]?.issues||[]), ...(r.statusReasons||[])];
        bodyHtml += renderIssueRows(allIssues);
        bodyHtml += renderMetrics(r, status);
    }

    const matchingBase = BASE_URL ? BASE_URL.replace(/\/$/, '')+'/shop/'+encodeURIComponent(SHOP_NAME)+'/sites-items-mapping' : '';
    const matchingUrl  = matchingBase ? matchingBase+'?site='+encodeURIComponent(r.siteName)+'&onlyAssociated=1' : '#';

    const siteEsc = r.siteName.replace(/'/g, "\\'").replace(/\\/g, '\\\\');
    const configBtn = r.cityIdsId
        ? '<button class="btn btn-xs btn-outline-secondary btn-config-issue"'
            +' data-city-ids-id="'+r.cityIdsId+'"'
            +' data-site="'+esc(r.siteName)+'"'
            +' data-has-issue="'+(r.hasConfigIssue?'true':'false')+'"'
            +' data-current-type="'+esc(r.configIssueType||'')+'"'
            +' data-current-note="'+esc(r.configIssueNote||'')+'"'
            +' style="font-size:.78rem;padding:2px 10px">'
            +'<i class="fas fa-cog me-1"></i>Не выкачка</button>'
        : '';
    const checkBtn = '<button class="btn btn-xs btn-outline-success"'
        +' style="font-size:.78rem;padding:2px 10px"'
        +' onclick="markSiteChecked(\''+siteEsc+'\')">'
        +'✓ Проверено</button>';

    bodyHtml += '<div class="d-flex gap-2 mt-3 flex-wrap">'
        +(matchingBase?'<a href="'+matchingUrl+'" target="_blank" class="btn btn-xs btn-outline-secondary" style="font-size:.78rem;padding:2px 10px">'
        +'<i class="fas fa-handshake me-1"></i>Матчинг</a>':'')
        +configBtn
        +'<div class="ms-auto">'+checkBtn+'</div>'
        +'</div>';

    const configBadge = (r.hasConfigIssue && r.configIssueType)
        ? '<span class="config-issue-badge" data-config-site="'+esc(r.siteName)+'" title="'+esc(CONFIG_ISSUE_LABELS[r.configIssueType]||r.configIssueType)+(r.configIssueNote?': '+esc(r.configIssueNote):'')+'">⚙ '+esc(CONFIG_ISSUE_LABELS[r.configIssueType]||r.configIssueType)+'</span>'
        : '<span class="config-issue-badge d-none" data-config-site="'+esc(r.siteName)+'">⚙</span>';

    return '<div class="site-group border-'+status+'" data-status="'+status
        +'" data-type="'+(r.checkType||'')+'" data-site="'+esc(r.siteName).toLowerCase()+'">'
        +'<div class="group-header" onclick="toggleGroup(this)">'
        +'<span class="status-pill pill-'+status+'"></span>'
        +'<span class="status-badge badge-'+status+'">'+STATUS_LABEL[status]+'</span>'
        +'<span class="site-name" onclick="copySiteName(event,this,\''+r.siteName.replace(/'/g,"\\'")+'\')" title="Нажмите чтобы скопировать">'+esc(r.siteName)+'</span>'
        +configBadge
        +histIcon
        +(cityTag?'<span class="city-tag">'+esc(cityTag)+'</span>':'')
        +ignoreTag+masterTag+accountTag+typeTag
        +(headerLabel?'<span class="first-reason">'+esc(headerLabel)+'</span>':'')
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

function renderCitiesTable(r, cities) {
    const rows = cities.map(cr => {
        const lbl = STATUS_LABEL[cr.status]||cr.status;
        const fi  = cr.issues && cr.issues.length ? esc(cr.issues[0].shortLabel || cr.issues[0].message) : '';
        const cityDisplay = cr.cityId && cr.cityName
            ? esc(cr.cityId) + ' — ' + esc(cr.cityName)
            : esc(cr.cityName || cr.cityId || '—');
        let stockHtml = fmt(cr.inStock);
        if (cr.baselineInStock != null && cr.baselineInStock > 0) {
            stockHtml += ' <small class="text-muted">(база:&nbsp;'+Math.round(cr.baselineInStock)+')</small>';
        }
        if (cr.inStockDeltaPercent != null) {
            const cls = cr.inStockDeltaPercent >= 0 ? 'text-success' : 'text-danger';
            const sign = cr.inStockDeltaPercent >= 0 ? '+' : '';
            stockHtml += ' <span class="'+cls+'">'+sign+cr.inStockDeltaPercent+'%</span>';
        }
        let extLink = '';
        if (r.historyBaseUrl && cr.cityId) {
            const url = r.historyBaseUrl
                + '?dateFrom=' + encodeURIComponent(toExportDate(DATE_FROM))
                + '&dateTo='   + encodeURIComponent(toExportDate(DATE_TO))
                + '&shop='     + encodeURIComponent(r.shopParam || '-')
                + '&cityId='   + encodeURIComponent(cr.cityId);
            extLink = ' <a href="'+url+'" target="_blank" title="История на export.zoomos.by" style="color:#adb5bd">'
                +'<i class="fas fa-external-link-alt" style="font-size:.7rem"></i></a>';
        }
        const cityChecked = cr.status !== 'OK' && isCityChecked(r.siteName, cr.cityId);
        const cityEsc = (cr.cityId || '').replace(/'/g, "\\'");
        const siteEscCity = r.siteName.replace(/'/g, "\\'");
        const checkCityBtn = cr.status !== 'OK'
            ? '<button class="btn btn-xs '+(cityChecked?'btn-success':'btn-outline-success')+' py-0 px-1"'
              +' style="font-size:.7rem" onclick="event.stopPropagation();toggleCityChecked(\''+siteEscCity+'\',\''+cityEsc+'\')">'
              +(cityChecked?'↩':'✓')+'</button>'
            : '';
        const hiddenStyle = cityChecked ? ' style="display:none"' : '';
        return '<tr data-city-status="'+cr.status+'"'+hiddenStyle+'><td>'+cityDisplay+extLink+'</td>'
            +'<td><span class="status-badge badge-'+cr.status+'" style="font-size:.68rem">'+lbl+'</span></td>'
            +'<td class="text-end">'+stockHtml+'</td>'
            +'<td class="text-muted" style="font-size:.78rem">'+fi+'</td>'
            +'<td>'+checkCityBtn+'</td></tr>';
    }).join('');
    const showOk = getShowOkCities(r.siteName);
    const chk = showOk ? 'checked' : '';
    const hideClass = showOk ? '' : ' hide-ok-cities';
    const toggle = '<div class="mb-1 text-end" style="font-size:.78rem;color:#6c757d">'
        +'<label style="cursor:pointer"><input type="checkbox" '+chk
        +' data-site="'+esc(r.siteName)+'" onchange="toggleOkCities(this)" style="margin-right:4px">Показывать OK города</label>'
        +'</div>';
    return toggle+'<table class="table table-sm cities-table mb-2'+hideClass+'"><thead><tr>'
        +'<th>Город</th><th>Статус</th><th class="text-end">В наличии</th><th>Причина</th><th></th>'
        +'</tr></thead><tbody>'+rows+'</tbody></table>';
}

window.toggleOkCities = function(chk) {
    const siteName = chk.dataset.site;
    const show = chk.checked;
    localStorage.setItem('zoomos.showOkCities.' + siteName, show);
    const table = chk.closest('.group-body')?.querySelector('.cities-table');
    if (table) table.classList.toggle('hide-ok-cities', !show);
};

// ── Sparkline ─────────────────────────────────────────────────────────────
const sparkCharts = new Map();

function renderSparkline(r, status) {
    const mainReason = r.statusReasons && r.statusReasons.length ? r.statusReasons[0].reason : null;
    let history, labelPrefix, bl;
    if ((mainReason === 'SPEED_SPIKE' || mainReason === 'SPEED_TREND') && r.speedHistory && r.speedHistory.length >= 2) {
        history = r.speedHistory;
        labelPrefix = 'Время (мин) за';
        bl = r.baselineSpeedMinsPer1000 != null ? String(Math.round(r.baselineSpeedMinsPer1000)) : '';
    } else if (mainReason === 'ERROR_GROWTH' && r.errorHistory && r.errorHistory.length >= 2) {
        history = r.errorHistory;
        labelPrefix = 'Ошибок за';
        bl = '';
    } else {
        history = r.inStockHistory || [];
        labelPrefix = 'inStock за';
        bl = (r.baselineInStock != null) ? String(Math.round(r.baselineInStock)) : '';
    }
    console.log('sparkline data', r.siteName, history);
    if (!history || history.length < 2) {
        return '<div class="mt-2 p-2 rounded text-muted" style="font-size:0.75rem;border:1px solid #dee2e6;display:inline-block;">Нет данных для графика</div>';
    }
    const color  = sparklineColor(r);
    const safeId = 'spark-'+(r.siteName+'-'+(r.cityId||'')).replace(/[^a-zA-Z0-9]/g,'_');
    const data   = JSON.stringify(history).replace(/'/g,"&#39;");
    const first  = history[0]?.date||'';
    const last   = history[history.length-1]?.date||'';
    const label  = (first&&last) ? labelPrefix+' '+fmtDateShort(first)+' — '+fmtDateShort(last) : labelPrefix;
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
function getVisibleResults() {
    const search = searchText.toLowerCase();
    return allResults.filter(r => {
        if (r.status === 'OK') return false;
        if (isChecked(r.siteName)) return false;
        if (activeFilters.size && !activeFilters.has(r.status)) return false;
        if (activeType !== 'ALL' && r.checkType !== activeType) return false;
        if (search && !r.siteName.toLowerCase().includes(search)) return false;
        return true;
    });
}

function render() {
    const list   = document.getElementById('results-list');
    const visible = getVisibleResults();
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

// ── "Проверено" ───────────────────────────────────────────────────────────
window.markSiteChecked = function(siteName) {
    setChecked(siteName, true);
    render();
    renderCheckedSection();
};

window.unmarkSiteChecked = function(siteName) {
    setChecked(siteName, false);
    const r = allResults.find(x => x.siteName === siteName);
    if (r) (r.cityResults || []).forEach(cr => { if (cr.cityId) setCityChecked(siteName, cr.cityId, false); });
    render();
    renderCheckedSection();
};

window.toggleCityChecked = function(siteName, cityId) {
    const nowChecked = !isCityChecked(siteName, cityId);
    setCityChecked(siteName, cityId, nowChecked);
    const r = allResults.find(x => x.siteName === siteName);
    if (nowChecked && r) {
        const problemCities = (r.cityResults || []).filter(cr => cr.status !== 'OK' && cr.cityId);
        const allDone = problemCities.length > 0 && problemCities.every(cr => isCityChecked(siteName, cr.cityId));
        if (allDone) setChecked(siteName, true);
    } else {
        setChecked(siteName, false);
    }
    render();
    renderCheckedSection();
};

function renderCheckedSection() {
    const checked = allResults.filter(r => r.status !== 'OK' && isChecked(r.siteName));
    const section  = document.getElementById('checked-section');
    const list     = document.getElementById('checked-list');
    const countEl  = document.getElementById('checked-count');
    if (!section) return;
    if (!checked.length) { section.classList.add('d-none'); return; }
    section.classList.remove('d-none');
    if (countEl) countEl.textContent = checked.length;
    list.innerHTML = checked.map(r => {
        const cities = r.cityResults || [];
        const multi  = cities.length > 1;
        const cityLabel = multi
            ? cities.length + ' городов'
            : (r.cityId && r.cityName ? r.cityId + ' — ' + r.cityName : (r.cityName || ''));
        const sn = r.siteName.replace(/'/g, "\\'");
        return '<div class="d-flex align-items-center gap-2 py-1 border-bottom">'
            +'<span class="status-badge badge-'+r.status+'" style="font-size:.65rem">'+STATUS_LABEL[r.status]+'</span>'
            +'<span class="small flex-grow-1 text-truncate">'+esc(r.siteName)
            +(cityLabel?' <span class="text-muted">'+esc(cityLabel)+'</span>':'')+'</span>'
            +'<button class="btn btn-xs btn-outline-secondary py-0 px-1" style="font-size:.75rem;flex-shrink:0"'
            +' onclick="unmarkSiteChecked(\''+sn+'\')">↩ Вернуть</button>'
            +'</div>';
    }).join('');
}

window.toggleCheckedSection = function(btn) {
    const list = document.getElementById('checked-list');
    list.classList.toggle('d-none');
    const arrow = btn.textContent.match(/[▾▴]/);
    if (arrow) btn.textContent = btn.textContent.replace(/[▾▴]/, list.classList.contains('d-none') ? '▾' : '▴');
};

// ── "Не выкачка" / config-issue ───────────────────────────────────────────
let _ciCityIdsId = null;
let _ciBtn = null;

function openConfigIssueModal(btn) {
    _ciBtn = btn;
    _ciCityIdsId = btn.dataset.cityIdsId;
    const hasIssue = btn.dataset.hasIssue === 'true';
    document.getElementById('ciSiteName').textContent = btn.dataset.site || '';
    document.getElementById('ciType').value = btn.dataset.currentType || '';
    document.getElementById('ciNote').value = btn.dataset.currentNote || '';
    document.getElementById('ciClearFlag').checked = false;
    const clearRow = document.getElementById('ciClearRow');
    if (clearRow) clearRow.classList.toggle('d-none', !hasIssue);
    document.getElementById('ciError').classList.add('d-none');
    bootstrap.Modal.getOrCreateInstance(document.getElementById('configIssueModal')).show();
}

document.getElementById('results-list').addEventListener('click', e => {
    const btn = e.target.closest('.btn-config-issue');
    if (btn) { e.stopPropagation(); openConfigIssueModal(btn); }
});

document.getElementById('ciSaveBtn').addEventListener('click', function() {
    if (!_ciCityIdsId) return;
    const clearFlag = document.getElementById('ciClearFlag').checked;
    const type = clearFlag ? '' : document.getElementById('ciType').value.trim();
    const note = clearFlag ? '' : document.getElementById('ciNote').value.trim();
    const errEl = document.getElementById('ciError');
    if (!clearFlag && !type) {
        errEl.textContent = 'Выберите тип проблемы';
        errEl.classList.remove('d-none');
        return;
    }
    errEl.classList.add('d-none');
    this.disabled = true;
    const self = this;
    const fd = new FormData();
    if (!clearFlag) { fd.append('type', type); if (note) fd.append('note', note); }
    fetch('/zoomos/city-ids/' + _ciCityIdsId + '/config-issue', { method: 'POST', body: fd })
        .then(r => r.json())
        .then(data => {
            self.disabled = false;
            if (!data.success) {
                errEl.textContent = data.error || 'Ошибка';
                errEl.classList.remove('d-none');
                return;
            }
            bootstrap.Modal.getOrCreateInstance(document.getElementById('configIssueModal')).hide();
            const hasNow = data.hasConfigIssue;
            if (_ciBtn) {
                _ciBtn.dataset.hasIssue     = hasNow ? 'true' : 'false';
                _ciBtn.dataset.currentType  = data.configIssueType || '';
                _ciBtn.dataset.currentNote  = data.configIssueNote || '';
            }
            const siteName = _ciBtn ? _ciBtn.dataset.site : '';
            document.querySelectorAll('.config-issue-badge[data-config-site="' + CSS.escape(siteName) + '"]')
                .forEach(badge => {
                    if (hasNow && data.configIssueType) {
                        const lbl = CONFIG_ISSUE_LABELS[data.configIssueType] || data.configIssueType;
                        badge.textContent = '⚙ ' + lbl;
                        badge.title = lbl + (data.configIssueNote ? ': ' + data.configIssueNote : '');
                        badge.classList.remove('d-none');
                    } else {
                        badge.classList.add('d-none');
                    }
                });
            // Обновить allResults для корректной работы copy
            const r = allResults.find(x => x.siteName === siteName);
            if (r) {
                r.hasConfigIssue   = hasNow;
                r.configIssueType  = data.configIssueType || null;
                r.configIssueNote  = data.configIssueNote || null;
            }
        })
        .catch(() => { self.disabled = false; errEl.textContent = 'Ошибка сети'; errEl.classList.remove('d-none'); });
});

// ── Копировать ────────────────────────────────────────────────────────────
function showCopyToast(msg) {
    const t = document.getElementById('copy-toast');
    if (!t) return;
    t.textContent = msg;
    t.classList.add('show');
    setTimeout(() => t.classList.remove('show'), 2000);
}

window.copyDomains = function() {
    const visible = getVisibleResults();
    const domains = [...new Set(visible.map(r => r.siteName))];
    if (!domains.length) { showCopyToast('Нет данных'); return; }
    navigator.clipboard.writeText(domains.join('\n')).then(() => showCopyToast('Скопировано ' + domains.length + ' доменов'));
};

window.copyProblems = function() {
    const visible = getVisibleResults().filter(r => r.status === 'CRITICAL' || r.status === 'WARNING');
    const lines = [];
    visible.forEach(r => {
        const cities = r.cityResults || [];
        if (cities.length <= 1) {
            const cityPart = r.cityId ? r.cityId + (r.cityName ? ' ' + r.cityName : '') : '';
            const label = getHeaderLabel(r) || STATUS_LABEL[r.status] || r.status;
            lines.push(r.siteName + (cityPart ? ' — ' + cityPart : '') + ': ' + label);
        } else {
            cities.filter(cr => cr.status === 'CRITICAL' || cr.status === 'WARNING').forEach(cr => {
                const cityPart = (cr.cityId || '') + (cr.cityName ? ' ' + cr.cityName : '');
                const label = cr.issues && cr.issues.length ? (cr.issues[0].shortLabel || cr.issues[0].message) : STATUS_LABEL[cr.status];
                lines.push(r.siteName + ' — ' + cityPart + ': ' + label);
            });
        }
    });
    if (!lines.length) { showCopyToast('Нет данных'); return; }
    navigator.clipboard.writeText(lines.join('\n')).then(() => showCopyToast('Скопировано ' + lines.length + ' строк'));
};

window.copyConfigIssues = function() {
    const withIssue = getVisibleResults().filter(r => r.hasConfigIssue && r.configIssueType);
    const lines = withIssue.map(r => {
        const lbl = CONFIG_ISSUE_LABELS[r.configIssueType] || r.configIssueType;
        return r.siteName + ': ' + lbl + (r.configIssueNote ? ' — ' + r.configIssueNote : '');
    });
    if (!lines.length) { showCopyToast('Нет данных'); return; }
    navigator.clipboard.writeText(lines.join('\n')).then(() => showCopyToast('Скопировано ' + lines.length + ' строк'));
};

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
        renderCheckedSection();
    })
    .catch(err => {
        console.error('analyze fetch failed:', err);
        document.getElementById('loading').classList.add('d-none');
        document.getElementById('error-msg').classList.remove('d-none');
    });

})();
