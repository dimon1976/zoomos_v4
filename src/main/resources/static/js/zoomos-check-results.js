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
    EMPTY_RESULT:2,
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
    const filteringByInProgress = activeFilters.has('IN_PROGRESS');
    const cities = (filteringByInProgress && r.inProgressCities?.length > 0)
        ? r.inProgressCities
        : (r.cityResults || []);
    const multi  = cities.length > 1;

    const cityLabel = r.cityId && r.cityName ? r.cityId + ' — ' + r.cityName : (r.cityName || cities[0]?.cityName || '');
    const cityTag   = multi ? cities.length+' городов' : cityLabel;
    const accountTag = (!multi && r.accountName)
        ? '<span class="account-tag" title="'+esc(r.accountName)+'">'+esc(r.accountName)+'</span>' : '';
    const headerLabel = getHeaderLabel(r);
    const typeTag = '<span class="badge bg-light text-secondary border" style="font-size:.68rem">'+(r.checkType||'')+'</span>';
    const ignoreTag  = r.ignoreStock  ? '<span class="city-tag" style="color:#adb5bd;font-style:italic">(без наличия)</span>' : '';
    const masterTag  = '<span class="city-tag master-city-tag"'+(r.masterCityId?'':' style="display:none"')+'>'+(r.masterCityId?'(мастер: '+esc(r.masterCityId)+')':'')+'</span>';

    // ── Дополнительные бейджи: приоритет, ненастроен, Redmine ──────────────
    const priorityStar   = r.isPriority ? '<i class="fas fa-star priority-star" title="Приоритетный сайт"></i>' : '';
    const notConfigured  = r.itemPriceConfigured === false
        ? '<span class="not-configured-badge" data-nc-site="'+esc(r.siteName)+'" title="Сайт не настроен — нажмите =цены для проверки">⚠ не настроен</span>' : '';
    const rmCached       = redmineCache.get(r.siteName);
    const rmBadgeInner   = rmCached
        ? '<a href="'+esc(rmCached.url||'#')+'" target="_blank" onclick="event.stopPropagation()"'
          +' class="btn btn-sm rm-issue-link '+(rmCached.isClosed?'btn-success':'btn-danger')+'"'
          +' title="Redmine #'+rmCached.id+' — '+esc(rmCached.statusName||'')+'">#'+rmCached.id+'</a>'
          +'<button class="btn btn-sm btn-outline-secondary btn-rm-edit"'
          +' data-rm-site="'+esc(r.siteName)+'" data-issue-id="'+rmCached.id+'" data-issue-url="'+esc(rmCached.url||'')+'"'
          +' title="Редактировать в Redmine"><i class="fas fa-pencil-alt"></i></button>'
        : '';
    const rmBadgeTag = '<span class="rm-badge-container" data-rm-site="'+esc(r.siteName)+'">'+rmBadgeInner+'</span>';

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
    if (r.masterCityId) {
        bodyHtml += '<div class="small text-muted mb-1"><i class="fas fa-map-marker-alt me-1"></i>Мастер-город: <strong>'
            + esc(r.masterCityId) + '</strong></div>';
    }
    if (multi) {
        const ipCount = r.inProgressCities?.length || 0;
        if (ipCount > 0 && r.status !== 'IN_PROGRESS' && !filteringByInProgress) {
            bodyHtml += '<div class="small text-muted mb-1">⏳ ' + ipCount + ' '
                + (ipCount === 1 ? 'город' : ipCount < 5 ? 'города' : 'городов')
                + ' в процессе выкачки — <a href="#" onclick="activateFilter(\'IN_PROGRESS\');return false">подробнее</a></div>';
        }
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
        ? '<button class="btn btn-sm btn-outline-secondary btn-config-issue"'
            +' data-city-ids-id="'+r.cityIdsId+'"'
            +' data-site="'+esc(r.siteName)+'"'
            +' data-has-issue="'+(r.hasConfigIssue?'true':'false')+'"'
            +' data-current-type="'+esc(r.configIssueType||'')+'"'
            +' data-current-note="'+esc(r.configIssueNote||'')+'">'
            +'<i class="fas fa-cog me-1"></i>Не выкачка</button>'
        : '';
    const checkBtn = '<button class="btn btn-sm btn-outline-success btn-mark-checked"'
        +' data-site="'+esc(r.siteName)+'">'
        +'✓ Проверено</button>';

    const rmCreateBtn = !rmCached
        ? '<button class="btn btn-sm btn-outline-danger btn-rm-create"'
            +' data-rm-site="'+esc(r.siteName)+'">'
            +'<i class="fas fa-bug me-1"></i>Redmine</button>'
        : '';

    const eqIconCls = r.equalPrices === true ? 'text-success'
                    : r.equalPrices === false ? 'text-info'
                    : 'text-secondary';
    const eqTitle = r.equalPricesCheckedAt
        ? '= цены: ' + (r.equalPrices === true ? 'ДА' : 'НЕТ') + ', проверка ' + r.equalPricesCheckedAt
        : 'Проверить CITIES_EQUAL_PRICES';
    const eqBtn = r.siteId
        ? '<button class="btn btn-sm btn-outline-secondary btn-fetch-eq-prices"'
            +' data-site="'+esc(r.siteName)+'"'
            +' data-site-id="'+r.siteId+'"'
            +' data-checked-at="'+esc(r.equalPricesCheckedAt||'')+'"'
            +' title="'+esc(eqTitle)+'">'
            +'<i class="fas fa-sync-alt me-1 '+eqIconCls+'"></i>=цены</button>'
        : '';

    const matchingBtn = matchingBase
        ? '<a href="'+matchingUrl+'" target="_blank" class="btn btn-sm btn-outline-secondary">'
          +'<i class="fas fa-handshake me-1"></i>Матчинг</a>'
        : '';

    bodyHtml += '<div class="d-flex align-items-center gap-2 mt-2">'
        +'<div class="btn-group btn-group-sm">'+matchingBtn+eqBtn+configBtn+'</div>'
        +'<div class="ms-auto d-flex align-items-center gap-1">'+rmCreateBtn+checkBtn+'</div>'
        +'</div>';

    const configBadge = (r.hasConfigIssue && r.configIssueType)
        ? '<span class="config-issue-badge" data-config-site="'+esc(r.siteName)+'" title="'+esc(CONFIG_ISSUE_LABELS[r.configIssueType]||r.configIssueType)+(r.configIssueNote?': '+esc(r.configIssueNote):'')+'">⚙ '+esc(CONFIG_ISSUE_LABELS[r.configIssueType]||r.configIssueType)+'</span>'
        : '<span class="config-issue-badge d-none" data-config-site="'+esc(r.siteName)+'">⚙</span>';

    return '<div class="site-group border-'+status+'" data-status="'+status
        +'" data-type="'+(r.checkType||'')+'" data-site="'+esc(r.siteName).toLowerCase()+'">'
        +'<div class="group-header" onclick="toggleGroup(this)">'
        +'<span class="status-pill pill-'+status+'"></span>'
        +'<span class="status-badge badge-'+status+'">'+STATUS_LABEL[status]+'</span>'
        +priorityStar
        +'<span class="site-name" onclick="copySiteName(event,this,\''+r.siteName.replace(/'/g,"\\'")+'\')" title="Нажмите чтобы скопировать">'+esc(r.siteName)+'</span>'
        +notConfigured
        +configBadge
        +rmBadgeTag
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
            ? '<input type="checkbox" class="form-check-input city-check"'
              +' style="cursor:pointer;width:1.1em;height:1.1em;accent-color:#198754"'
              +' title="Отметить как проверено"'
              +(cityChecked?' checked':'')
              +' onclick="event.stopPropagation();toggleCityChecked(\''+siteEscCity+'\',\''+cityEsc+'\')">'
            : '';
        const hiddenStyle = cityChecked ? ' style="display:none"' : '';
        const needsLastKnown = cr.lastKnownDate != null &&
            (cr.status === 'NOT_FOUND' ||
             (cr.issues && cr.issues.some(i => i.reason === 'NOT_FOUND' || i.reason === 'STALLED')));
        let fiHtml = fi;
        if (needsLastKnown) {
            const ld = cr.lastKnownDate;
            const d = Array.isArray(ld)
                ? String(ld[2]).padStart(2,'0') + '.' + String(ld[1]).padStart(2,'0')
                : String(ld).substring(8,10) + '.' + String(ld).substring(5,7);
            let hint = 'Последняя: ' + d;
            if (cr.lastKnownIsStalled) hint += ' зависла' + (cr.lastKnownUpdatedTime ? ' (обн.: ' + cr.lastKnownUpdatedTime + ')' : '');
            if (cr.lastKnownCompletionPercent != null) hint += ' (' + cr.lastKnownCompletionPercent + '%)';
            if (cr.lastKnownInStock != null) hint += ', в нал.: ' + cr.lastKnownInStock;
            fiHtml += '<div class="text-muted" style="font-size:0.8em;margin-top:2px">' + esc(hint) + '</div>';
        }
        return '<tr data-city-status="'+cr.status+'"'+hiddenStyle+'><td>'+cityDisplay+extLink+'</td>'
            +'<td><span class="status-badge badge-'+cr.status+'" style="font-size:.68rem">'+lbl+'</span></td>'
            +'<td class="text-end">'+stockHtml+'</td>'
            +'<td class="text-muted" style="font-size:.78rem">'+fiHtml+'</td>'
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

// ── Мини-иконки для истории и Redmine ────────────────────────────────────
function histIcon(r, cityId) {
    if (!r.historyBaseUrl) return '';
    const url = r.historyBaseUrl
        + '?dateFrom=' + encodeURIComponent(toExportDate(DATE_FROM))
        + '&dateTo='   + encodeURIComponent(toExportDate(DATE_TO))
        + '&shop='     + encodeURIComponent(r.shopParam || '-')
        + (cityId ? '&cityId=' + encodeURIComponent(cityId) : '');
    return ' <a href="' + url + '" target="_blank" class="text-muted ms-1" style="font-size:.72rem" title="История выкачки">'
        + '<i class="fas fa-external-link-alt"></i></a>';
}

function miniRmIcon(siteName) {
    const iss = redmineCache.get(siteName);
    const sn  = esc(siteName);
    if (iss) {
        const cls = iss.isClosed ? 'text-success' : 'text-danger';
        return ' <button class="btn btn-xs btn-rm-edit border-0 bg-transparent p-0 ms-1 ' + cls + '"'
            + ' data-rm-site="' + sn + '" data-issue-id="' + iss.id + '" data-issue-url="' + esc(iss.url || '') + '"'
            + ' style="font-size:.72rem;vertical-align:baseline" title="Redmine #' + iss.id + ' — ' + esc(iss.statusName || '') + '">'
            + '<i class="fas fa-bug"></i></button>';
    }
    return ' <button class="btn btn-xs btn-rm-create border-0 bg-transparent p-0 ms-1 text-muted"'
        + ' data-rm-site="' + sn + '"'
        + ' style="font-size:.72rem;vertical-align:baseline" title="Создать задачу Redmine">'
        + '<i class="fas fa-bug"></i></button>';
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
        const masterInfo = r.masterCityId
            ? ' <span class="text-success" style="font-size:.75rem">(мастер: '+esc(r.masterCityId)+')</span>' : '';
        return '<tr><td>'+esc(r.siteName)+histIcon(r, r.cityId)+miniRmIcon(r.siteName)+'</td>'
            +'<td class="text-muted">'+esc(r.cityName||'')+masterInfo+'</td>'
            +'<td class="text-end">'+inStock+'</td></tr>';
    }).join('');
}

function updateCounters(results) {
    const counts = { CRITICAL:0, WARNING:0, TREND:0, IN_PROGRESS:0, OK:0 };
    results.forEach(r => {
        if (r.status !== 'IN_PROGRESS' && counts[r.status] !== undefined) counts[r.status]++;
        counts.IN_PROGRESS += (r.inProgressCities?.length || 0);
    });
    Object.entries(counts).forEach(([s,n]) => {
        const el = document.getElementById('cnt-'+s);
        if (el) el.textContent = n;
    });
}

// ── Main render ───────────────────────────────────────────────────────────
function getVisibleResults() {
    const search = searchText.toLowerCase();
    const filteringByInProgress = activeFilters.has('IN_PROGRESS');
    return allResults.filter(r => {
        if (r.status === 'OK') return false;
        if (isChecked(r.siteName)) return false;
        if (activeFilters.size) {
            if (!(filteringByInProgress && r.inProgressCities?.length > 0) && !activeFilters.has(r.status)) {
                return false;
            }
        }
        if (activeType !== 'ALL' && r.checkType !== activeType) return false;
        if (search && !r.siteName.toLowerCase().includes(search)) return false;
        return true;
    });
}

function render() {
    const list = document.getElementById('results-list');
    // Сохраняем какие карточки были раскрыты (data-site хранит siteName.toLowerCase())
    const openCards = new Set(
        [...list.querySelectorAll('.group-body.open')]
            .map(el => el.closest('.site-group')?.dataset.site).filter(Boolean)
    );
    const visible = getVisibleResults();
    visible.sort(sortByReasonPriority);
    if (!visible.length) {
        list.innerHTML = '<div class="empty-state"><i class="fas fa-check-circle fa-2x text-success mb-3 d-block"></i>'
            +(allResults.length ? 'Нет проблем по выбранным фильтрам' : 'Нет данных')+'</div>';
        return;
    }
    list.innerHTML = visible.map(renderSiteResult).join('');
    // Восстанавливаем открытые карточки
    openCards.forEach(siteKey => {
        const group = list.querySelector('.site-group[data-site="' + CSS.escape(siteKey) + '"]');
        if (!group) return;
        group.querySelector('.group-body')?.classList.add('open');
        group.querySelector('.expand-btn')?.classList.add('open');
    });
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
window.activateFilter = function(f) {
    activeFilters.clear();
    activeFilters.add(f);
    updateFilterButtons();
    render();
};

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
        if (activeFilters.size === 0) {
            activeFilters = new Set(['CRITICAL', 'WARNING']);
        } else {
            activeFilters.clear();
        }
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

window.unmarkCityChecked = function(siteName, cityId) {
    setCityChecked(siteName, cityId, false);
    setChecked(siteName, false);
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
    const section = document.getElementById('checked-section');
    const list    = document.getElementById('checked-list');
    const countEl = document.getElementById('checked-count');
    if (!section) return;

    const items = [];

    // 1. Сайты целиком в "Проверено"
    allResults.filter(r => r.status !== 'OK' && isChecked(r.siteName)).forEach(r => {
        const cities = r.cityResults || [];
        const multi  = cities.length > 1;
        const cityLabel = multi
            ? cities.length + ' городов'
            : (r.cityId && r.cityName ? r.cityId + ' — ' + r.cityName : (r.cityName || ''));
        const sn = r.siteName.replace(/'/g, "\\'");
        items.push('<div class="d-flex align-items-center gap-2 checked-row">'
            +'<span class="status-badge badge-'+r.status+'" style="font-size:.65rem">'+STATUS_LABEL[r.status]+'</span>'
            +'<span class="small flex-grow-1 text-truncate">'+esc(r.siteName)
            +(cityLabel?' <span class="text-muted">'+esc(cityLabel)+'</span>':'')
            +histIcon(r, r.cityId)+miniRmIcon(r.siteName)+'</span>'
            +'<button class="btn btn-sm btn-outline-secondary flex-shrink-0"'
            +' onclick="unmarkSiteChecked(\''+sn+'\')">↩ Вернуть</button>'
            +'</div>');
    });

    // 2. Отдельные города для сайтов, которые ещё не перемещены целиком
    allResults.filter(r => r.status !== 'OK' && !isChecked(r.siteName)).forEach(r => {
        const problemCities = (r.cityResults || []).filter(cr => cr.status !== 'OK' && cr.cityId);
        problemCities.filter(cr => isCityChecked(r.siteName, cr.cityId)).forEach(cr => {
            const cityLabel = (cr.cityId || '') + (cr.cityName ? ' — ' + cr.cityName : '');
            const sn  = r.siteName.replace(/'/g, "\\'");
            const cId = (cr.cityId || '').replace(/'/g, "\\'");
            items.push('<div class="d-flex align-items-center gap-2 checked-row">'
                +'<span class="status-badge badge-'+cr.status+'" style="font-size:.65rem">'+STATUS_LABEL[cr.status]+'</span>'
                +'<span class="small flex-grow-1 text-truncate">'+esc(r.siteName)
                +' <span class="text-muted">'+esc(cityLabel)+'</span>'
                +histIcon(r, cr.cityId)+miniRmIcon(r.siteName)+'</span>'
                +'<button class="btn btn-sm btn-outline-secondary flex-shrink-0"'
                +' onclick="unmarkCityChecked(\''+sn+'\',\''+cId+'\')">↩ Вернуть</button>'
                +'</div>');
        });
    });

    if (!items.length) { section.classList.add('d-none'); return; }
    section.classList.remove('d-none');
    if (countEl) countEl.textContent = items.length;
    list.innerHTML = items.join('');
}

window.toggleCheckedSection = function() {
    const list  = document.getElementById('checked-list');
    const arrow = document.getElementById('checked-arrow');
    list.classList.toggle('d-none');
    if (arrow) arrow.textContent = list.classList.contains('d-none') ? '▾' : '▴';
};

// ── "Не выкачка" / config-issue ───────────────────────────────────────────
let _ciCityIdsId = null;
let _ciBtn = null;

// Bootstrap может быть в window.bootstrap (стандарт) или window.tabler.* (Tabler UMD)
function bsModal(el) {
    const BSModal = (typeof bootstrap !== 'undefined' && bootstrap.Modal)
        || (typeof tabler !== 'undefined' && tabler.Modal);
    if (!BSModal) { console.error('Bootstrap Modal не найден'); return null; }
    return BSModal.getOrCreateInstance(el);
}

function bsToast(el, opts) {
    const BSToast = (typeof bootstrap !== 'undefined' && bootstrap.Toast)
        || (typeof tabler !== 'undefined' && tabler.Toast);
    if (!BSToast) { console.error('Bootstrap Toast не найден'); return null; }
    return new BSToast(el, opts);
}

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
    const modal = bsModal(document.getElementById('configIssueModal'));
    if (modal) modal.show();
}

document.getElementById('results-list').addEventListener('click', e => {
    const btn = e.target.closest('.btn-config-issue');
    if (btn) { e.stopPropagation(); openConfigIssueModal(btn); }
    const eqBtn = e.target.closest('.btn-fetch-eq-prices');
    if (eqBtn) { e.stopPropagation(); fetchEqPricesForSite(eqBtn); }
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
            const m = bsModal(document.getElementById('configIssueModal'));
            if (m) m.hide();
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

window.copyNotConfigured = function() {
    const sites = allResults.filter(r => r.itemPriceConfigured === false).map(r => r.siteName);
    if (!sites.length) { showCopyToast('Нет ненастроенных сайтов'); return; }
    navigator.clipboard.writeText(sites.join('\n')).then(() => showCopyToast('Скопировано ' + sites.length + ' сайтов'));
};

// ── CITIES_EQUAL_PRICES ───────────────────────────────────────────────────
let _eqSiteId = null, _eqSiteName = null;

function fetchEqPricesForSite(btn) {
    const siteId = btn.dataset.siteId, siteName = btn.dataset.site;
    const hasMasterCity = !!document.querySelector(
        '.site-group[data-site="' + CSS.escape(siteName.toLowerCase()) + '"] .master-city-tag:not([style*="display:none"])');
    if (btn.classList.contains('btn-outline-success') || hasMasterCity) {
        fetch('/zoomos/sites/' + siteId + '/historical-cities')
            .then(r => r.json())
            .then(cities => showEqModal(siteName, siteId, cities))
            .catch(() => showEqModal(siteName, siteId, []));
        return;
    }
    const origHtml = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Проверка\u2026';
    fetch('/zoomos/sites/' + siteId + '/fetch-equal-prices', { method: 'POST' })
        .then(r => r.json())
        .then(data => {
            btn.disabled = false;
            const now = new Date().toLocaleString('ru-RU',
                {day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});
            btn.dataset.checkedAt = now;
            updateEqBadge(siteName, data.citiesEqualPrices, now);
            if (data.itemPriceConfigured === true) {
                document.querySelectorAll('.site-group[data-site="' + CSS.escape(siteName.toLowerCase()) + '"] .not-configured-badge')
                    .forEach(b => b.remove());
                const result = allResults.find(r => r.siteName === siteName);
                if (result) result.itemPriceConfigured = true;
            }
            if (data.success && data.citiesEqualPrices === true && data.historicalCities?.length)
                showEqModal(siteName, siteId, data.historicalCities);
        })
        .catch(() => { btn.disabled = false; btn.innerHTML = origHtml; });
}

function updateEqBadge(siteName, val, checkedAt) {
    document.querySelectorAll('.btn-fetch-eq-prices[data-site="' + CSS.escape(siteName) + '"]')
        .forEach(b => {
            const when = checkedAt || b.dataset.checkedAt || '';
            b.title = when
                ? ('= цены: ' + (val === true ? 'ДА' : 'НЕТ') + ', проверка ' + when)
                : 'Проверить CITIES_EQUAL_PRICES';
            const iconCls = val === true ? 'text-success' : val === false ? 'text-info' : 'text-secondary';
            b.innerHTML = '<i class="fas fa-sync-alt me-1 ' + iconCls + '"></i>=цены';
        });
    const r = allResults.find(x => x.siteName === siteName);
    if (r) { r.equalPrices = val; if (checkedAt) r.equalPricesCheckedAt = checkedAt; }
}

function applyMasterCity(city) {
    if (!_eqSiteId) return;
    const raw = (city || '').trim();
    const match = raw.match(/^(\d+)/);
    const cityId = match ? match[1] : raw;
    const msgEl = document.getElementById('eqMasterMsg');
    msgEl.classList.add('d-none');
    fetch('/zoomos/sites/' + _eqSiteId + '/master-city', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: 'masterCityId=' + encodeURIComponent(cityId)
    }).then(r => r.json()).then(data => {
        if (data.success) {
            msgEl.textContent = cityId ? '✓ ' + cityId + ' — установлен' : '✓ Мастер-город снят';
            msgEl.classList.remove('d-none');
            document.querySelectorAll('.site-group[data-site="' + CSS.escape(_eqSiteName.toLowerCase()) + '"] .master-city-tag')
                .forEach(tag => {
                    if (cityId) {
                        tag.textContent = '(мастер: ' + cityId + ')';
                        tag.style.display = '';
                        tag.classList.remove('master-city-flash');
                        void tag.offsetWidth;
                        tag.classList.add('master-city-flash');
                    } else {
                        tag.style.display = 'none';
                    }
                });
            const r = allResults.find(x => x.siteName === _eqSiteName);
            if (r) r.masterCityId = cityId || null;
        }
    }).catch(() => {});
}
function showEqModal(siteName, siteId, cities) {
    _eqSiteName = siteName;
    _eqSiteId = siteId;
    document.getElementById('eqSiteName').textContent = siteName;
    document.getElementById('eqMasterMsg').classList.add('d-none');
    document.getElementById('eqManualCityId').value = '';
    const list = document.getElementById('eqCitiesList');
    list.innerHTML = '';
    if (!cities.length) {
        list.innerHTML = '<div class="text-muted small py-1">Нет данных</div>';
    } else {
        cities.forEach(city => {
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'list-group-item list-group-item-action py-1 small';
            btn.textContent = city;
            btn.onclick = function() {
                list.querySelectorAll('.list-group-item').forEach(b => b.classList.remove('active'));
                this.classList.add('active');
                document.getElementById('eqManualCityId').value = city;
                applyMasterCity(city);
            };
            list.appendChild(btn);
        });
    }
    bsModal(document.getElementById('eqPricesModal'))?.show();
}

document.getElementById('eqManualSetBtn')?.addEventListener('click', function() {
    applyMasterCity(document.getElementById('eqManualCityId').value);
});
document.getElementById('eqClearBtn')?.addEventListener('click', function() {
    applyMasterCity('');
});

window.fetchAllEqPrices = async function() {
    const seenSites = new Set();
    const btns = [...document.querySelectorAll('.btn-fetch-eq-prices')]
        .filter(btn => {
            if (seenSites.has(btn.dataset.site)) return false;
            seenSites.add(btn.dataset.site);
            return true;
        });
    if (!btns.length) return;
    const globalBtn = document.getElementById('btnFetchAllEqPrices');
    const progressEl = document.getElementById('eqPricesProgress');
    const progressText = document.getElementById('eqPricesProgressText');
    if (globalBtn) globalBtn.disabled = true;
    if (progressEl) progressEl.classList.remove('d-none');
    let done = 0;
    if (progressText) progressText.textContent = '0/' + btns.length;
    for (const btn of btns) {
        const origHtml = btn.innerHTML;
        document.querySelectorAll('.btn-fetch-eq-prices[data-site="' + CSS.escape(btn.dataset.site) + '"]')
            .forEach(b => { b.disabled = true; b.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Проверка\u2026'; });
        try {
            const data = await fetch('/zoomos/sites/' + btn.dataset.siteId + '/fetch-equal-prices',
                { method: 'POST' }).then(r => r.json());
            const now = new Date().toLocaleString('ru-RU',
                {day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});
            updateEqBadge(btn.dataset.site, data.citiesEqualPrices, now);
            if (data.itemPriceConfigured === true) {
                document.querySelectorAll('.site-group[data-site="' + CSS.escape(btn.dataset.site.toLowerCase()) + '"] .not-configured-badge')
                    .forEach(b => b.remove());
                const result = allResults.find(r => r.siteName === btn.dataset.site);
                if (result) result.itemPriceConfigured = true;
            }
        } catch (e) {
            document.querySelectorAll('.btn-fetch-eq-prices[data-site="' + CSS.escape(btn.dataset.site) + '"]')
                .forEach(b => { b.disabled = false; b.innerHTML = origHtml; });
        }
        document.querySelectorAll('.btn-fetch-eq-prices[data-site="' + CSS.escape(btn.dataset.site) + '"]')
            .forEach(b => b.disabled = false);
        if (progressText) progressText.textContent = (++done) + '/' + btns.length;
    }
    if (globalBtn) globalBtn.disabled = false;
    if (progressEl) progressEl.classList.add('d-none');
};

// ── Redmine ────────────────────────────────────────────────────────────────
const redmineCache = new Map(); // siteName → issue | null
let rmOptions = null; // кэш опций

function runBatchRedmineCheck(results) {
    const sites = [...new Set(results.filter(r => r.status !== 'OK').map(r => r.siteName))];
    if (!sites.length) return;
    const params = sites.map(s => 'sites=' + encodeURIComponent(s)).join('&');
    fetch('/zoomos/redmine/check-batch?' + params)
        .then(r => r.json())
        .then(data => {
            if (data.enabled === false) return;
            Object.entries(data).forEach(([site, issue]) => {
                redmineCache.set(site, issue || null);
                updateRedmineBadge(site, issue || null);
            });
        })
        .catch(() => {});
}

function updateRedmineBadge(site, issue) {
    document.querySelectorAll('.rm-badge-container[data-rm-site="' + CSS.escape(site) + '"]').forEach(c => {
        c.innerHTML = '';
        if (!issue) return;
        const cls = issue.isClosed ? 'btn-success' : 'btn-danger';
        c.innerHTML = '<a href="' + esc(issue.url || '#') + '" target="_blank" onclick="event.stopPropagation()"'
            + ' class="btn btn-sm rm-issue-link ' + cls + '"'
            + ' title="Redmine #' + issue.id + ' — ' + esc(issue.statusName || '') + '">#' + issue.id + '</a>'
            + '<button class="btn btn-sm btn-outline-secondary btn-rm-edit"'
            + ' data-rm-site="' + esc(site) + '" data-issue-id="' + issue.id + '"'
            + ' data-issue-url="' + esc(issue.url || '') + '"'
            + ' title="Редактировать в Redmine"><i class="fas fa-pencil-alt"></i></button>';
    });
    document.querySelectorAll('.btn-rm-create[data-rm-site="' + CSS.escape(site) + '"]').forEach(btn => {
        btn.style.display = issue ? 'none' : '';
    });
}

// ── Redmine modal helpers ─────────────────────────────────────────────────
let _rmSiteName = null, _rmMode = 'create', _rmIssueId = null, _rmResult = null;

function rmFillSelect(selId, items, defaultId) {
    const sel = document.getElementById(selId);
    if (!sel) return;
    sel.innerHTML = items.map(it =>
        '<option value="' + it.id + '"' + (String(it.id) === String(defaultId) ? ' selected' : '') + '>'
        + esc(it.name) + '</option>'
    ).join('');
}

function rmResetModal() {
    ['rmCreateBlock','rmSuccessBlock','rmNotesBlock','issueSelectPanel'].forEach(id => document.getElementById(id)?.classList.add('d-none'));
    [
        ['rmCreateBtn', '<i class="fas fa-bug me-1"></i>Создать задачу'],
        ['rmUpdateBtn', '<i class="fas fa-save me-1"></i>Обновить задачу'],
        ['rmNextBtn',   'Далее <i class="fas fa-arrow-right ms-1"></i>']
    ].forEach(([id, html]) => {
        const el = document.getElementById(id);
        if (el) { el.classList.add('d-none'); el.disabled = false; el.innerHTML = html; }
    });
    ['rmCfErrorRow','rmCfMethodCol','rmCfVariantCol'].forEach(id => { const el = document.getElementById(id); if (el) el.style.display = 'none'; });
    document.getElementById('rmErrorMsg')?.classList.add('d-none');
    document.getElementById('rmLoading')?.classList.remove('d-none');
    const descEl = document.getElementById('rmDescription');
    descEl.value    = '';
    descEl.disabled = false;
    document.getElementById('rmShortMessage').value = '';
    document.getElementById('rmNotes').value = '';
}

// Собрать список проблем сайта для Phase 1
function collectRmIssues(r) {
    function cityHistUrl(cityId) {
        if (!r.historyBaseUrl) return '';
        return r.historyBaseUrl
            + '?dateFrom=' + encodeURIComponent(toExportDate(DATE_FROM))
            + '&dateTo='   + encodeURIComponent(toExportDate(DATE_TO))
            + '&shop='     + encodeURIComponent(r.shopParam || '-')
            + (cityId ? '&cityId=' + encodeURIComponent(cityId) : '');
    }

    const cities = r.cityResults || [];
    const problemCities = cities.filter(cr => cr.status !== 'OK');
    if (problemCities.length > 0) {
        return problemCities.map(cr => ({
            cityId:     cr.cityId || '',
            cityName:   cr.cityName || '',
            shortLabel: cr.issues && cr.issues.length ? (cr.issues[0].shortLabel || cr.issues[0].message) : STATUS_LABEL[cr.status] || cr.status,
            level:      cr.status,
            historyUrl: cityHistUrl(cr.cityId),
        }));
    }
    // Однородный сайт — используем statusReasons
    const issues = (r.statusReasons || []).filter(i => i.level === 'CRITICAL' || i.level === 'WARNING');
    if (!issues.length && r.statusReasons && r.statusReasons.length) {
        const first = r.statusReasons[0];
        return [{ cityId: r.cityId || '', cityName: r.cityName || '', shortLabel: first.shortLabel || first.message, level: first.level, historyUrl: cityHistUrl(r.cityId) }];
    }
    return issues.map(i => ({
        cityId:     r.cityId || '',
        cityName:   r.cityName || '',
        shortLabel: i.shortLabel || i.message,
        level:      i.level,
        historyUrl: cityHistUrl(r.cityId),
    }));
}

// Построить описание задачи Redmine из выбранных пунктов
function buildRedmineDescription(r, items) {
    if (!items.length) return '';

    // Группировка по shortLabel
    const byLabel = {};
    items.forEach(i => {
        const key = i.shortLabel || '';
        if (!byLabel[key]) byLabel[key] = [];
        byLabel[key].push(i);
    });

    let desc = 'Сайт: ' + (r.siteName || '') + '\n';
    if (r.dateFrom || r.dateTo) {
        desc += 'Период: ' + (r.dateFrom || '?') + ' — ' + (r.dateTo || '?') + '\n';
    }
    desc += '\nпроблемы с выкачкой:\n\n';

    // CRITICAL → WARNING → TREND
    ['CRITICAL', 'WARNING', 'TREND'].forEach(level => {
        Object.entries(byLabel)
            .filter(([, list]) => list[0].level === level)
            .forEach(([label, list]) => {
                const badge  = level === 'CRITICAL' ? '[ERROR]' : '[WARN]';
                const cities = list.map(i => i.cityId + ' - ' + i.cityName).join(', ');
                desc += badge + ' ' + label + '\nГорода: ' + cities + '\n\n';
            });
    });
    // Уровень не определён (items без level) — добавляем в конце
    Object.entries(byLabel)
        .filter(([, list]) => !list[0].level || !['CRITICAL','WARNING','TREND'].includes(list[0].level))
        .forEach(([label, list]) => {
            const cities = list.map(i => i.cityId + ' - ' + i.cityName).join(', ');
            desc += '[ERROR] ' + label + '\nГорода: ' + cities + '\n\n';
        });

    // Уникальные ссылки на историю (дедупликация по URL)
    const seenUrls = new Set();
    const historyLines = [];
    items.forEach(i => {
        if (i.historyUrl && !seenUrls.has(i.historyUrl)) {
            seenUrls.add(i.historyUrl);
            historyLines.push(i.cityId + ' - ' + i.cityName + ': ' + i.historyUrl);
        }
    });
    if (historyLines.length) {
        desc += '{{collapse(Ссылки на историю парсинга)\n' + historyLines.join('\n') + '\n}}\n\n';
    }

    const matchBase = BASE_URL ? BASE_URL.replace(/\/$/, '') + '/shop/' + encodeURIComponent(SHOP_NAME) + '/sites-items-mapping' : '';
    if (matchBase) {
        desc += '{{collapse(Ссылка на матчинг)\n' + matchBase + '?site=' + encodeURIComponent(r.siteName) + '&onlyAssociated=1\n}}\n';
    }
    return desc;
}

// Краткое описание = уникальные shortLabel через ", "
function buildRmShortMessage(items) {
    return [...new Set(items.map(i => i.shortLabel).filter(Boolean))].join(', ');
}

let _rmIssueItems = []; // все items сайта для текущей модалки

function fillCfSelects(cfs, cfValues) {
    if (cfs.cfError) {
        const sel = document.getElementById('rmSelectCfError');
        if (!sel) return;
        sel.innerHTML = '';
        const rawCur = cfValues ? cfValues[String(cfs.cfError.id)] : null;
        const curArr = Array.isArray(rawCur) ? rawCur : (rawCur ? [rawCur] : []);
        (cfs.cfError.possibleValues || []).forEach(v => {
            const opt = document.createElement('option');
            opt.value = v; opt.textContent = v;
            if (curArr.includes(v)) opt.selected = true;
            sel.appendChild(opt);
        });
        const row = document.getElementById('rmCfErrorRow');
        if (row) row.style.display = '';
    }
    if (cfs.cfMethod) {
        const items = (cfs.cfMethod.possibleValues || []).map(v => ({id:v, name:v}));
        const cur = cfValues ? cfValues[String(cfs.cfMethod.id)] : '';
        rmFillSelect('rmSelectCfMethod', items, cur || '');
        const col = document.getElementById('rmCfMethodCol');
        if (col) col.style.display = '';
    }
    if (cfs.cfVariant) {
        const items = [{id:'', name:'— выбрать —'}].concat((cfs.cfVariant.possibleValues || []).map(v => ({id:v, name:v})));
        const cur = cfValues ? cfValues[String(cfs.cfVariant.id)] : '';
        rmFillSelect('rmSelectCfVariant', items, cur || '');
        const col = document.getElementById('rmCfVariantCol');
        if (col) col.style.display = '';
    }
}

function rmCollectBody(site, description) {
    const cfs = rmOptions ? (rmOptions.customFields || {}) : {};
    const customFields = [];
    const cfErrorSel = document.getElementById('rmSelectCfError');
    const cfErrorValues = (cfs.cfError && cfErrorSel)
        ? Array.from(cfErrorSel.selectedOptions).map(o => o.value).filter(v => v)
        : [];
    if (cfs.cfError)   customFields.push({ id: cfs.cfError.id,   value: cfErrorValues });
    if (cfs.cfMethod)  { const v = document.getElementById('rmSelectCfMethod')?.value; if (v != null) customFields.push({ id: cfs.cfMethod.id,  value: v }); }
    if (cfs.cfVariant) { const v = document.getElementById('rmSelectCfVariant')?.value; if (v != null) customFields.push({ id: cfs.cfVariant.id, value: v }); }
    const shortMsg = cfErrorValues.length
        ? cfErrorValues.join(', ')
        : (document.getElementById('rmShortMessage')?.value || '');
    return {
        site, description,
        shortMessage: shortMsg,
        notes:        document.getElementById('rmNotes')?.value || '',
        trackerId:    parseInt(document.getElementById('rmSelectTracker')?.value)  || 0,
        statusId:     parseInt(document.getElementById('rmSelectStatus')?.value)   || 0,
        priorityId:   parseInt(document.getElementById('rmSelectPriority')?.value) || 0,
        assignedToId: parseInt(document.getElementById('rmSelectAssignee')?.value) || 0,
        customFields,
    };
}

function rmShowForm(opts, items, issueData) {
    const r = allResults.find(x => x.siteName === _rmSiteName);
    const defs = opts.defaults || {};
    const cfs  = opts.customFields || {};
    // Скрываем кастомные поля до заполнения
    ['rmCfErrorRow','rmCfMethodCol','rmCfVariantCol'].forEach(id => {
        const el = document.getElementById(id); if (el) el.style.display = 'none';
    });
    if (issueData && !issueData.error) {
        rmFillSelect('rmSelectTracker',  opts.trackers   || [], issueData.trackerId    || defs.trackerId   || 0);
        rmFillSelect('rmSelectStatus',   opts.statuses   || [], issueData.statusId     || defs.statusId    || 0);
        rmFillSelect('rmSelectPriority', opts.priorities || [], issueData.priorityId   || defs.priorityId  || 0);
        rmFillSelect('rmSelectAssignee', opts.users      || [], issueData.assignedToId || defs.assignedToId|| 0);
        fillCfSelects(cfs, issueData.customFieldValues || {});
        const descEl = document.getElementById('rmDescription');
        descEl.value    = issueData.description || '';
        descEl.disabled = true;
        document.getElementById('rmNotes').value        = r ? buildRedmineDescription(r, items) : '';
        document.getElementById('rmShortMessage').value = items[0]?.shortLabel || '';
        document.getElementById('rmNotesBlock')?.classList.remove('d-none');
        document.getElementById('rmUpdateBtn')?.classList.remove('d-none');
    } else {
        rmFillSelect('rmSelectTracker',  opts.trackers   || [], defs.trackerId   || 0);
        rmFillSelect('rmSelectStatus',   opts.statuses   || [], defs.statusId    || 0);
        rmFillSelect('rmSelectPriority', opts.priorities || [], defs.priorityId  || 0);
        rmFillSelect('rmSelectAssignee', opts.users      || [], defs.assignedToId|| 0);
        fillCfSelects(cfs, null);
        document.getElementById('rmDescription').value  = r ? buildRedmineDescription(r, items) : '';
        document.getElementById('rmShortMessage').value = items[0]?.shortLabel || '';
        document.getElementById('rmCreateBtn')?.classList.remove('d-none');
    }
    document.getElementById('rmCreateBlock')?.classList.remove('d-none');
}

function rmShowSuccess(msg, site, issueUrl, shortMsg) {
    document.getElementById('rmCreateBlock')?.classList.add('d-none');
    document.getElementById('rmSuccessMsg').textContent = msg;
    document.getElementById('rmCopyText').textContent = (shortMsg && shortMsg !== site ? site + ' — ' + shortMsg : site) + '\n' + issueUrl;
    document.getElementById('rmSuccessBlock')?.classList.remove('d-none');
    document.getElementById('rmCreateBtn')?.classList.add('d-none');
    document.getElementById('rmUpdateBtn')?.classList.add('d-none');
    const toastEl = document.getElementById('rm-success-toast');
    if (toastEl) {
        const textEl = document.getElementById('rm-success-text');
        textEl.innerHTML = msg + (issueUrl ? ' — <a href="' + esc(issueUrl) + '" target="_blank" class="text-white fw-bold">открыть</a>' : '');
        bsToast(toastEl, { delay: 6000 })?.show();
    }
}

function rmOpenModal(siteName, mode, issueId, issueUrl) {
    _rmSiteName = siteName;
    _rmMode = mode;
    _rmIssueId = issueId || null;

    rmResetModal();
    const nameEl = document.getElementById('rmSiteName');
    if (mode === 'edit' && issueId && issueUrl) {
        nameEl.innerHTML = '<a href="' + esc(issueUrl) + '" target="_blank" class="text-dark text-decoration-none">#' + issueId + '</a> — ' + esc(siteName);
    } else {
        nameEl.textContent = siteName;
    }
    bsModal(document.getElementById('redmineModal'))?.show();

    const r = allResults.find(x => x.siteName === siteName);
    _rmIssueItems = r ? collectRmIssues(r) : [];

    Promise.all([
        rmOptions ? Promise.resolve(rmOptions) : fetch('/zoomos/redmine/options').then(rr => rr.json()),
        mode === 'edit' && issueId
            ? fetch('/zoomos/redmine/issue/' + issueId).then(rr => rr.json())
            : Promise.resolve(null)
    ]).then(([opts, issueData]) => {
        rmOptions = opts;
        document.getElementById('rmLoading')?.classList.add('d-none');
        if (!opts || opts.enabled === false) {
            document.getElementById('rmErrorMsg').textContent = 'Redmine не настроен';
            document.getElementById('rmErrorMsg')?.classList.remove('d-none');
            return;
        }
        if (_rmIssueItems.length >= 1) {
            // Phase 1: показать список проблем с чекбоксами (и CREATE, и EDIT)
            const listEl = document.getElementById('issueSelectList');
            listEl.innerHTML = _rmIssueItems.map(it => {
                const cityLabel = [it.cityId, it.cityName].filter(Boolean).join(' — ');
                const text = cityLabel ? cityLabel + ': ' + it.shortLabel : it.shortLabel;
                return '<label class="list-group-item list-group-item-action py-1 px-2 d-flex align-items-center gap-2" style="cursor:pointer;font-size:.85rem">'
                    + '<input type="checkbox" class="form-check-input flex-shrink-0" checked'
                    + ' data-city-id="'    + esc(it.cityId    || '') + '"'
                    + ' data-city-name="'  + esc(it.cityName  || '') + '"'
                    + ' data-short-label="'+ esc(it.shortLabel|| '') + '"'
                    + ' data-history-url="'+ esc(it.historyUrl|| '') + '"'
                    + ' data-level="'      + esc(it.level     || '') + '">'
                    + '<span>' + esc(text) + '</span>'
                    + '</label>';
            }).join('');
            document.getElementById('issueSelectPanel')?.classList.remove('d-none');
            const toggleBtn = document.getElementById('rmToggleAllBtn');
            if (toggleBtn) toggleBtn.textContent = 'Снять все'; // все чекбоксы checked по умолчанию
            const nextBtn = document.getElementById('rmNextBtn');
            nextBtn._opts      = opts;
            nextBtn._issueData = issueData;
            nextBtn?.classList.remove('d-none');
        } else {
            rmShowForm(opts, _rmIssueItems, issueData);
        }
    }).catch(err => {
        document.getElementById('rmLoading')?.classList.add('d-none');
        document.getElementById('rmErrorMsg').textContent = 'Ошибка: ' + err.message;
        document.getElementById('rmErrorMsg')?.classList.remove('d-none');
    });
}

document.getElementById('rmNextBtn')?.addEventListener('click', function() {
    const opts      = this._opts      || rmOptions;
    const issueData = this._issueData || null;
    if (!opts) return;
    const selected = [];
    document.querySelectorAll('#issueSelectList input[type=checkbox]:checked').forEach(cb => {
        selected.push({
            cityId:     cb.dataset.cityId     || '',
            cityName:   cb.dataset.cityName   || '',
            shortLabel: cb.dataset.shortLabel || '',
            historyUrl: cb.dataset.historyUrl || '',
            level:      cb.dataset.level      || '',
        });
    });
    if (!selected.length) return;
    document.getElementById('issueSelectPanel')?.classList.add('d-none');
    this.classList.add('d-none');
    rmShowForm(opts, selected, issueData);
});

window.rmToggleAllIssues = function() {
    const cbs = [...document.querySelectorAll('#issueSelectList input[type=checkbox]')];
    const allChecked = cbs.every(cb => cb.checked);
    cbs.forEach(cb => cb.checked = !allChecked);
    const btn = document.getElementById('rmToggleAllBtn');
    if (btn) btn.textContent = allChecked ? 'Выбрать все' : 'Снять все';
};

// ── Общая delegation для Redmine-кнопок (results-list + ok-section + checked-section) ──
document.addEventListener('click', e => {
    const createBtn = e.target.closest('.btn-rm-create');
    if (createBtn) { e.stopPropagation(); rmOpenModal(createBtn.dataset.rmSite, 'create'); return; }
    const editBtn = e.target.closest('.btn-rm-edit');
    if (editBtn) { e.stopPropagation(); rmOpenModal(editBtn.dataset.rmSite, 'edit', parseInt(editBtn.dataset.issueId), editBtn.dataset.issueUrl); return; }
    const markBtn = e.target.closest('.btn-mark-checked');
    if (markBtn) { e.stopPropagation(); markSiteChecked(markBtn.dataset.site); }
});

document.getElementById('rmCreateBtn')?.addEventListener('click', function() {
    if (!_rmSiteName || !rmOptions) return;
    const btn = this; btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Создаётся...';
    document.getElementById('rmErrorMsg')?.classList.add('d-none');
    const siteName = _rmSiteName;
    const body = rmCollectBody(siteName, document.getElementById('rmDescription').value);

    function applyCreated(iss) {
        redmineCache.set(siteName, { id: iss.id, url: iss.url, statusName: iss.status || iss.statusName, isClosed: iss.closed || false });
        updateRedmineBadge(siteName, redmineCache.get(siteName));
        markSiteChecked(siteName);
        rmShowSuccess('Задача #' + iss.id + ' создана', siteName, iss.url, iss.shortMessage || '');
    }

    // Workaround: Redmine-сервер возвращает 404 на POST, но задача создаётся.
    // Если POST вернул 404 или сервер не нашёл задачу сразу — ищем через GET /check.
    function fetchByCheck() {
        fetch('/zoomos/redmine/check?site=' + encodeURIComponent(siteName))
            .then(r => r.json())
            .then(d => {
                const iss = d.existing && d.existing[0];
                if (iss) { applyCreated(iss); }
                else { showRmError('Задача создана, но id не получен — обновите страницу'); }
            })
            .catch(() => showRmError('Задача создана, но id не получен — обновите страницу'));
    }

    function showRmError(msg) {
        document.getElementById('rmErrorMsg').textContent = msg;
        document.getElementById('rmErrorMsg')?.classList.remove('d-none');
        btn.disabled = false;
        btn.innerHTML = '<i class="fas fa-bug me-1"></i>Создать задачу';
    }

    fetch('/zoomos/redmine/create', { method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body) })
        .then(r => {
            if (r.status === 404) { fetchByCheck(); return null; }
            return r.json();
        })
        .then(data => {
            if (!data) return;
            if (data.success && data.issue) {
                applyCreated(data.issue);
            } else if (data.error && data.error.includes('не найдена')) {
                // findRecentIssueBySubject вернул null (timing) — ищем вручную
                fetchByCheck();
            } else {
                showRmError(data.error || 'Ошибка');
            }
        })
        .catch(err => showRmError('Ошибка сети: ' + err.message));
});

document.getElementById('rmUpdateBtn')?.addEventListener('click', function() {
    if (!_rmIssueId || !rmOptions) return;
    const btn = this; btn.disabled = true;
    btn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Сохраняется...';
    document.getElementById('rmErrorMsg')?.classList.add('d-none');
    const body = rmCollectBody(_rmSiteName, document.getElementById('rmDescription').value);
    fetch('/zoomos/redmine/update/' + _rmIssueId, { method:'PUT', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body) })
        .then(r => r.json())
        .then(data => {
            if (data.success) {
                const iss = data.issue || {};
                const cached = redmineCache.get(_rmSiteName) || {};
                cached.isClosed = iss.isClosed || false;
                if (iss.statusName) cached.statusName = iss.statusName;
                redmineCache.set(_rmSiteName, cached);
                updateRedmineBadge(_rmSiteName, cached);
                markSiteChecked(_rmSiteName);
                rmShowSuccess('Задача обновлена', _rmSiteName, iss.url || cached.url || '', iss.shortMessage || '');
            } else {
                document.getElementById('rmErrorMsg').textContent = data.error || 'Ошибка';
                document.getElementById('rmErrorMsg')?.classList.remove('d-none');
                btn.disabled = false; btn.innerHTML = '<i class="fas fa-save me-1"></i>Обновить задачу';
            }
        })
        .catch(err => {
            document.getElementById('rmErrorMsg').textContent = 'Ошибка сети: ' + err.message;
            document.getElementById('rmErrorMsg')?.classList.remove('d-none');
            btn.disabled = false; btn.innerHTML = '<i class="fas fa-save me-1"></i>Обновить задачу';
        });
});

document.getElementById('rmCopyBtn')?.addEventListener('click', function() {
    const text = document.getElementById('rmCopyText').textContent;
    navigator.clipboard.writeText(text).then(() => {
        this.innerHTML = '<i class="fas fa-check me-1"></i>Скопировано';
        setTimeout(() => { this.innerHTML = '<i class="fas fa-copy me-1"></i>Скопировать'; }, 2000);
    });
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
        renderCheckedSection();
        setTimeout(() => runBatchRedmineCheck(results), 0);
    })
    .catch(err => {
        console.error('analyze fetch failed:', err);
        document.getElementById('loading').classList.add('d-none');
        document.getElementById('error-msg').classList.remove('d-none');
    });

})();
