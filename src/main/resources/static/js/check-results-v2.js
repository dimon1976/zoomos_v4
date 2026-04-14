// ── Toggle functions (new section/subgroup/domain-row design) ────────
function toggleSection(h) { h.closest('.section').classList.toggle('is-open'); }
function toggleSubgroup(h) { h.closest('.subgroup').classList.toggle('is-open'); }
function toggleDomain(row) { row.classList.toggle('is-open'); }

// ── Counter-pill smooth scroll ────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.counter-pill[href]').forEach(function(pill) {
        pill.addEventListener('click', function(e) {
            e.preventDefault();
            var target = document.querySelector(pill.getAttribute('href'));
            if (target) {
                target.scrollIntoView({ behavior: 'smooth', block: 'start' });
                if (!target.classList.contains('is-open')) target.classList.add('is-open');
            }
        });
    });
});

// Tabler не экспортирует window.bootstrap — определяем шим всегда
window.bootstrap = {
    Collapse: {
        getOrCreateInstance: function(el) {
            return {
                show: function() { if (!el || el.classList.contains('show')) return; el.classList.add('show'); },
                hide: function() { if (!el || !el.classList.contains('show')) return; el.classList.remove('show'); },
                toggle: function() { el && el.classList.contains('show') ? this.hide() : this.show(); }
            };
        }
    },
    Modal: (function() {
        const _inst = new WeakMap();
        function _make(el) {
            const inst = {
                show: function() {
                    if (el.classList.contains('show')) return;
                    el.classList.add('show'); el.style.display = 'block';
                    el.removeAttribute('aria-hidden'); el.setAttribute('aria-modal', 'true');
                    document.body.classList.add('modal-open');
                    if (!document.getElementById('_bsShimBackdrop')) {
                        const bd = document.createElement('div');
                        bd.id = '_bsShimBackdrop'; bd.className = 'modal-backdrop fade show';
                        document.body.appendChild(bd);
                        bd.addEventListener('click', function() { inst.hide(); });
                    }
                    el.querySelectorAll('[data-bs-dismiss="modal"]').forEach(function(btn) {
                        btn.addEventListener('click', function() { inst.hide(); }, { once: true });
                    });
                },
                hide: function() {
                    if (!el.classList.contains('show')) return;
                    el.classList.remove('show'); el.style.display = '';
                    el.setAttribute('aria-hidden', 'true'); el.removeAttribute('aria-modal');
                    document.body.classList.remove('modal-open');
                    const bd = document.getElementById('_bsShimBackdrop'); if (bd) bd.remove();
                }
            };
            return inst;
        }
        function Modal(el) { return _make(el); }
        Modal.getOrCreateInstance = function(el) { if (!_inst.has(el)) _inst.set(el, _make(el)); return _inst.get(el); };
        Modal.getInstance = function(el) { return _inst.get(el) || null; };
        return Modal;
    })()
};

// ── "Ошибка проверена" ────────────────────────────────────────────
const HIDE_KEY = 'v2_hidden_issues_' + RUN_ID;
let hiddenIssues = new Set(JSON.parse(localStorage.getItem(HIDE_KEY) || '[]'));

function saveHidden() {
    localStorage.setItem(HIDE_KEY, JSON.stringify([...hiddenIssues]));
}

function applyHidden() {
    document.querySelectorAll('.site-row[data-issue-key]').forEach(function(row) {
        const key = row.dataset.issueKey;
        if (hiddenIssues.has(key)) {
            row.classList.add('issue-checked');
            const btn = row.querySelector('.btn-hide-issue');
            if (btn) { btn.innerHTML = '<i class="fas fa-check-circle me-1 text-success"></i>Проверено'; }
        }
    });
}

document.addEventListener('click', function(e) {
    const btn = e.target.closest('.btn-hide-issue');
    if (!btn) return;
    e.stopPropagation();
    const key = btn.dataset.issueKey;
    const row = btn.closest('.site-row');
    if (!row) return;
    if (hiddenIssues.has(key)) {
        hiddenIssues.delete(key);
        saveHidden();
        row.classList.remove('issue-checked');
        btn.innerHTML = '<i class="far fa-circle me-1"></i>Проверено';
    } else {
        hiddenIssues.add(key);
        saveHidden();
        row.classList.add('issue-checked');
        btn.innerHTML = '<i class="fas fa-check-circle me-1 text-success"></i>Проверено';
    }
});

// ── Redmine helpers ───────────────────────────────────────────────

function collectSiteIssuesBySite(site) {
    const rows = document.querySelectorAll('.site-row[data-site="' + CSS.escape(site) + '"][data-message]');
    const issues = [];
    rows.forEach(function(row) {
        const msg = row.dataset.message || '';
        if (!msg.trim()) return;
        const checkType = row.dataset.checktype || '';
        const cityId    = row.dataset.cityid    || '';
        const addressId = row.dataset.addressid || '';
        const ts = Date.now();
        const shopParam = checkType === 'API' ? '-' : encodeURIComponent(SHOP_NAME);
        const historyUrl = BASE_URL + '/shops-parser/' + encodeURIComponent(site)
            + '/parsing-history?upd=' + ts
            + '&dateFrom=' + encodeURIComponent(DATE_FROM)
            + '&dateTo='   + encodeURIComponent(DATE_TO)
            + '&launchDate=&shop=' + shopParam
            + '&site=&cityId=' + encodeURIComponent(cityId)
            + '&addressId=' + encodeURIComponent(addressId) + '&accountId=&server=';
        const matchingUrl = BASE_URL + '/shop/' + encodeURIComponent(SHOP_NAME)
            + '/sites-items-mapping?site=' + encodeURIComponent(site) + '&onlyAssociated=1';
        issues.push({
            type: row.dataset.type || '',
            city: row.dataset.city || '',
            addressId: addressId,
            message: msg,
            historyUrl:  historyUrl,
            matchingUrl: matchingUrl
        });
    });
    return issues;
}

/** Заменяет кнопку создания для сайта на btn-group */
function replaceAllSiteBtns(site, issue, srcBtn) {
    const btnClass = issue.btnClass || (issue.isClosed ? 'btn-success' : 'btn-danger');
    function makeGroup(b) {
        const group = document.createElement('div');
        group.className = 'btn-group btn-group-sm';
        group.dataset.rmSite = site;
        const a = document.createElement('a');
        a.href = issue.url || '#'; a.target = '_blank';
        a.className = 'btn btn-xs px-2 ' + btnClass;
        a.title = 'Redmine #' + issue.id + ' — ' + (issue.status || issue.statusName || '');
        a.textContent = '#' + issue.id;
        const editBtn = document.createElement('button');
        editBtn.type = 'button';
        editBtn.className = 'btn btn-xs btn-outline-secondary px-1 btn-redmine-site-edit';
        editBtn.dataset.issueid  = issue.id;
        editBtn.dataset.issueurl = issue.url || '';
        editBtn.dataset.site     = site;
        if (b) { ['checktype'].forEach(function(k) { if (b.dataset[k]) editBtn.dataset[k] = b.dataset[k]; }); }
        editBtn.title = 'Редактировать задачу в Redmine';
        editBtn.innerHTML = '<i class="fas fa-pen" style="font-size:0.6rem"></i>';
        group.appendChild(a); group.appendChild(editBtn);
        return group;
    }
    document.querySelectorAll('.btn-redmine-site[data-site="' + CSS.escape(site) + '"]')
        .forEach(function(b) { b.replaceWith(makeGroup(b)); });
}

function revertStaleBtnGroup(site) {
    document.querySelectorAll('[data-rm-site="' + CSS.escape(site) + '"]')
        .forEach(function(grp) {
            const editBtn = grp.querySelector('.btn-redmine-site-edit');
            const btn = document.createElement('button');
            btn.type = 'button';
            btn.className = 'btn btn-xs btn-outline-danger btn-redmine-site';
            btn.dataset.site = site;
            if (editBtn) btn.dataset.checktype = editBtn.dataset.checktype || '';
            btn.innerHTML = '<i class="fas fa-bug me-1"></i>Redmine';
            btn.title = 'Создать задачу в Redmine';
            grp.replaceWith(btn);
        });
}

function markSiteVerified(site) { /* нет partial groups в v2 */ }
function hideIssue(key) { hiddenIssues.add(key); saveHidden(); applyHidden(); }

// ── Redmine batch-check ───────────────────────────────────────────
function runBatchCheck() {
    const allSites = new Set();
    document.querySelectorAll('.btn-redmine-site[data-site]').forEach(b => allSites.add(b.dataset.site));
    document.querySelectorAll('[data-rm-site]').forEach(g => { if (g.dataset.rmSite) allSites.add(g.dataset.rmSite); });
    if (!allSites.size) return;
    const params = [...allSites].map(s => 'sites=' + encodeURIComponent(s)).join('&');
    fetch('/zoomos/redmine/check-batch?' + params)
        .then(r => r.json())
        .then(function(data) {
            Object.entries(data).forEach(function([site, issue]) {
                if (!issue) { revertStaleBtnGroup(site); return; }
                const btnClass = issue.isClosed ? 'btn-success' : 'btn-danger';
                document.querySelectorAll('[data-rm-site="' + CSS.escape(site) + '"] a.btn')
                    .forEach(a => { a.className = 'btn btn-xs px-2 ' + btnClass; a.href = issue.url; a.title = '#' + issue.id; a.textContent = '#' + issue.id; });
                document.querySelectorAll('.btn-redmine-site[data-site="' + CSS.escape(site) + '"]')
                    .forEach(b => replaceAllSiteBtns(site, {id: issue.id, url: issue.url, status: issue.statusName, btnClass: btnClass}, b));
            });
        }).catch(function(){});
}

// ── Redmine IIFE ──────────────────────────────────────────────────
(function() {
    const modalEl = document.getElementById('redmineModal');
    if (!modalEl) return;
    const modal = bootstrap.Modal.getOrCreateInstance(modalEl);
    let currentBtn = null, currentMode = 'create', currentIssueId = null;
    let rmOptions = null, currentSiteIssues = [], currentSelectedIssues = [];

    function fillSelect(selId, items, defaultId) {
        const sel = document.getElementById(selId);
        if (!sel) return;
        sel.innerHTML = '';
        items.forEach(function(item) {
            const opt = document.createElement('option');
            opt.value = item.id; opt.textContent = item.name;
            if (String(item.id) === String(defaultId)) opt.selected = true;
            sel.appendChild(opt);
        });
    }
    function showError(msg) { const el = document.getElementById('rmErrorMsg'); el.textContent = msg; el.classList.remove('d-none'); }
    function resetModal() {
        ['rmLoading'].forEach(id => document.getElementById(id)?.classList.remove('d-none'));
        ['rmIssueSelect','rmExistingBlock','rmCreateBlock','rmCopyBlock','rmErrorMsg','rmCommentsBlock','rmCommentsList','rmNotesBlock'].forEach(id => document.getElementById(id)?.classList.add('d-none'));
        ['rmCreateBtn','rmUpdateBtn','rmNextBtn'].forEach(id => { const el = document.getElementById(id); if (!el) return; el.classList.add('d-none'); el.disabled = false; });
        document.getElementById('rmCreateBtn').innerHTML = '<i class="fas fa-bug me-1"></i>Создать задачу';
        document.getElementById('rmUpdateBtn').innerHTML = '<i class="fas fa-save me-1"></i>Обновить задачу';
        document.getElementById('rmNotes').value = '';
        currentSelectedIssues = [];
    }
    function fillCfSelects(cfs, cfValues, checkType) {
        if (cfs.cfError) {
            const sel = document.getElementById('rmSelectCfError'); sel.innerHTML = '';
            const rawCur = cfValues ? cfValues[String(cfs.cfError.id)] : null;
            const curArr = Array.isArray(rawCur) ? rawCur : (rawCur ? [rawCur] : []);
            (cfs.cfError.possibleValues || []).forEach(v => { const opt = document.createElement('option'); opt.value = v; opt.textContent = v; if (curArr.includes(v)) opt.selected = true; sel.appendChild(opt); });
            document.getElementById('rmCfErrorRow').style.display = '';
        }
        if (cfs.cfMethod) {
            const items = (cfs.cfMethod.possibleValues || []).map(v => ({id:v,name:v}));
            const cur = cfValues ? cfValues[String(cfs.cfMethod.id)] : checkType;
            fillSelect('rmSelectCfMethod', items, cur || checkType || '');
            document.getElementById('rmCfMethodCol').style.display = '';
        }
        if (cfs.cfVariant) {
            const items = [{id:'',name:'— выбрать —'}].concat((cfs.cfVariant.possibleValues || []).map(v => ({id:v,name:v})));
            const cur = cfValues ? cfValues[String(cfs.cfVariant.id)] : '';
            fillSelect('rmSelectCfVariant', items, cur || '');
            document.getElementById('rmCfVariantCol').style.display = '';
        }
    }
    function loadFormPhase(opts, checkType, issueData) {
        const cfs = opts.customFields || {}, defs = opts.defaults || {};
        if (issueData) {
            fillSelect('rmSelectTracker',  opts.trackers   || [], issueData.trackerId    || 0);
            fillSelect('rmSelectStatus',   opts.statuses   || [], issueData.statusId     || 0);
            fillSelect('rmSelectPriority', opts.priorities || [], issueData.priorityId   || 0);
            fillSelect('rmSelectAssignee', opts.users      || [], issueData.assignedToId || 0);
            fillCfSelects(cfs, issueData.customFieldValues || {}, checkType);
        } else {
            fillSelect('rmSelectTracker',  opts.trackers   || [], defs.trackerId   || 0);
            fillSelect('rmSelectStatus',   opts.statuses   || [], defs.statusId    || 0);
            fillSelect('rmSelectPriority', opts.priorities || [], defs.priorityId  || 0);
            fillSelect('rmSelectAssignee', opts.users      || [], defs.assignedToId|| 0);
            fillCfSelects(cfs, null, checkType);
        }
    }
    function showIssueSelectPhase(issues) {
        const listEl = document.getElementById('rmIssueSelectList'); listEl.innerHTML = '';
        issues.forEach(function(iss, idx) {
            const isError = iss.type === 'ERROR';
            const label = document.createElement('label');
            label.className = 'list-group-item list-group-item-action py-1 px-2 d-flex align-items-center gap-2';
            label.style.cursor = 'pointer';
            const cb = document.createElement('input'); cb.type = 'checkbox'; cb.checked = true; cb.value = idx; cb.className = 'form-check-input flex-shrink-0';
            const badge = document.createElement('span'); badge.className = 'badge ' + (isError ? 'bg-danger text-white' : 'bg-warning text-dark'); badge.textContent = iss.type;
            const text = document.createElement('span'); text.style.fontSize = '0.85rem'; text.textContent = (iss.city ? iss.city + ' — ' : '') + iss.message;
            label.appendChild(cb); label.appendChild(badge); label.appendChild(text); listEl.appendChild(label);
        });
        document.getElementById('rmLoading').classList.add('d-none');
        document.getElementById('rmIssueSelect').classList.remove('d-none');
        document.getElementById('rmNextBtn').classList.remove('d-none');
    }
    function getSelectedIssues() {
        const sel = [];
        document.querySelectorAll('#rmIssueSelectList input[type=checkbox]:checked').forEach(cb => sel.push(currentSiteIssues[parseInt(cb.value)]));
        return sel;
    }
    function buildSiteDescription(site, selectedIssues) {
        const byType = {};
        selectedIssues.forEach(iss => (byType[iss.type] = byType[iss.type] || []).push(iss));
        let desc = 'проблемы с выкачкой:\n\n';
        ['ERROR','WARNING','TREND_WARNING'].forEach(function(type) {
            const list = byType[type]; if (!list) return;
            const byMsg = {};
            list.forEach(iss => (byMsg[iss.message] = byMsg[iss.message] || []).push(iss));
            Object.entries(byMsg).forEach(([msg, items]) => {
                const badge = type === 'ERROR' ? '[ERROR]' : '[WARN]';
                const cities = items.map(i => i.city + (i.addressId ? ' (' + i.addressId + ')' : '')).join(', ');
                desc += badge + ' ' + msg + '\nГорода: ' + cities + '\n\n';
            });
        });
        const seenUrls = new Set();
        const histLines = [];
        selectedIssues.forEach(i => {
            if (i.historyUrl && !seenUrls.has(i.historyUrl)) {
                seenUrls.add(i.historyUrl);
                const prefix = (i.city || '') + (i.addressId ? ' (' + i.addressId + ')' : '');
                histLines.push(prefix ? prefix + ': ' + i.historyUrl : i.historyUrl);
            }
        });
        if (histLines.length) desc += '{{collapse(Ссылки на историю парсинга)\n' + histLines.join('\n') + '\n}}\n\n';
        const matchUrl = selectedIssues.find(i => i.matchingUrl);
        if (matchUrl) desc += '{{collapse(Ссылка на матчинг)\n' + matchUrl.matchingUrl + '\n}}\n';
        return desc;
    }
    function collectBody(mode, site, description) {
        const cfs = rmOptions.customFields || {};
        const customFields = [];
        const cfErrorSel = document.getElementById('rmSelectCfError');
        const cfErrorValues = cfs.cfError ? Array.from(cfErrorSel.selectedOptions).map(o => o.value).filter(v => v !== '') : [];
        if (cfs.cfError)   customFields.push({ id: cfs.cfError.id,   value: cfErrorValues });
        if (cfs.cfMethod)  customFields.push({ id: cfs.cfMethod.id,  value: document.getElementById('rmSelectCfMethod').value  || '' });
        if (cfs.cfVariant) customFields.push({ id: cfs.cfVariant.id, value: document.getElementById('rmSelectCfVariant').value || '' });
        const shortMsg = cfErrorValues.length ? cfErrorValues.join(', ') : (currentBtn ? currentBtn.dataset.message || '' : '');
        return {
            site: site, description: description, shortMessage: shortMsg,
            notes: document.getElementById('rmNotes').value || '',
            trackerId:    parseInt(document.getElementById('rmSelectTracker').value)  || 0,
            statusId:     parseInt(document.getElementById('rmSelectStatus').value)   || 0,
            priorityId:   parseInt(document.getElementById('rmSelectPriority').value) || 0,
            assignedToId: parseInt(document.getElementById('rmSelectAssignee').value) || 0,
            customFields: customFields
        };
    }
    function showCopyBlock(site, shortMessage, issueUrl, mode) {
        const msg = (shortMessage && shortMessage !== site) ? shortMessage : '';
        const copyText = (msg ? site + ' - ' + msg : site) + '\n' + issueUrl;
        document.getElementById('rmCopyText').textContent = copyText;
        ['rmCreateBlock','rmCreateBtn','rmUpdateBtn','rmNextBtn','rmIssueSelect','rmExistingBlock'].forEach(id => document.getElementById(id)?.classList.add('d-none'));
        const alertEl = document.querySelector('#rmCopyBlock .alert');
        if (alertEl) alertEl.innerHTML = mode === 'edit' ? '<i class="fas fa-check-circle me-1"></i>Задача обновлена!' : '<i class="fas fa-check-circle me-1"></i>Задача создана!';
        document.getElementById('rmCopyBlock').classList.remove('d-none');
    }

    document.getElementById('rmNextBtn').addEventListener('click', function() {
        const selected = getSelectedIssues(); if (!selected.length) return;
        currentSelectedIssues = selected;
        const site = currentBtn ? currentBtn.dataset.site : '';
        document.getElementById('rmIssueSelect').classList.add('d-none');
        document.getElementById('rmNextBtn').classList.add('d-none');
        document.getElementById('rmCreateBlock').classList.remove('d-none');
        if (currentMode === 'edit') {
            document.getElementById('rmNotes').value = buildSiteDescription(site, selected);
            document.getElementById('rmNotesBlock').classList.remove('d-none');
            document.getElementById('rmUpdateBtn').classList.remove('d-none');
        } else {
            document.getElementById('rmDescription').value = buildSiteDescription(site, selected);
            document.getElementById('rmCreateBtn').classList.remove('d-none');
        }
    });

    document.addEventListener('click', function(e) {
        const btn = e.target.closest('.btn-redmine-site');
        if (!btn) return;
        currentBtn = btn; currentMode = 'create'; currentIssueId = null;
        const site = btn.dataset.site;
        currentSiteIssues = collectSiteIssuesBySite(site);
        resetModal();
        document.getElementById('rmSiteName').textContent = site;
        modal.show();
        const checkType = btn.dataset.checktype || '';
        Promise.all([
            rmOptions ? Promise.resolve(rmOptions) : fetch('/zoomos/redmine/options').then(r => r.json()),
            fetch('/zoomos/redmine/check?' + new URLSearchParams({ site: site })).then(r => r.json())
        ]).then(function([opts, checkData]) {
            rmOptions = opts;
            if (!opts.enabled) { document.getElementById('rmLoading').classList.add('d-none'); showError('Redmine не настроен'); return; }
            if (checkData.existing && checkData.existing.length > 0) {
                const listEl = document.getElementById('rmExistingList'); listEl.innerHTML = '';
                checkData.existing.forEach(iss => { const a = document.createElement('a'); a.href = iss.url; a.target = '_blank'; a.className = 'btn btn-sm btn-outline-primary py-0 px-2'; a.innerHTML = '<i class="fas fa-external-link-alt me-1"></i>#' + iss.id + ' <span class="badge bg-secondary ms-1">' + iss.statusName + '</span>'; listEl.appendChild(a); });
                document.getElementById('rmExistingBlock').classList.remove('d-none');
            }
            loadFormPhase(opts, checkType, null);
            if (currentSiteIssues.length > 1) {
                showIssueSelectPhase(currentSiteIssues);
            } else {
                document.getElementById('rmLoading').classList.add('d-none');
                document.getElementById('rmDescription').value = currentSiteIssues.length === 1 ? buildSiteDescription(site, currentSiteIssues) : '';
                document.getElementById('rmCreateBlock').classList.remove('d-none');
                document.getElementById('rmCreateBtn').classList.remove('d-none');
            }
        }).catch(err => { document.getElementById('rmLoading').classList.add('d-none'); showError('Ошибка: ' + err.message); });
    });

    document.addEventListener('click', function(e) {
        const btn = e.target.closest('.btn-redmine-site-edit');
        if (!btn) return;
        currentBtn = btn; currentMode = 'edit'; currentIssueId = parseInt(btn.dataset.issueid);
        const site = btn.dataset.site;
        currentSiteIssues = collectSiteIssuesBySite(site);
        resetModal();
        const issueUrl = btn.dataset.issueurl || '';
        const siteNameEl = document.getElementById('rmSiteName');
        if (issueUrl) {
            siteNameEl.innerHTML = '';
            const _a = document.createElement('a'); _a.href = issueUrl; _a.target = '_blank'; _a.className = 'text-dark text-decoration-none'; _a.textContent = '#' + currentIssueId;
            siteNameEl.appendChild(_a); siteNameEl.appendChild(document.createTextNode(' — ' + site));
        } else {
            siteNameEl.textContent = '#' + currentIssueId + ' — ' + site;
        }
        modal.show();
        const checkType = btn.dataset.checktype || '';
        Promise.all([
            rmOptions ? Promise.resolve(rmOptions) : fetch('/zoomos/redmine/options').then(r => r.json()),
            fetch('/zoomos/redmine/issue/' + currentIssueId).then(r => r.json())
        ]).then(function([opts, issueData]) {
            rmOptions = opts;
            document.getElementById('rmLoading').classList.add('d-none');
            if (!opts.enabled) { showError('Redmine не настроен'); return; }
            if (issueData.error) {
                currentMode = 'create';
                document.getElementById('rmSiteName').textContent = site;
                showError('Задача #' + currentIssueId + ' не найдена — создайте новую.');
                loadFormPhase(opts, checkType, null);
                revertStaleBtnGroup(site);
                fetch('/zoomos/redmine/local-delete/' + encodeURIComponent(site), { method: 'DELETE' }).catch(()=>{});
                currentSiteIssues.length > 1 ? showIssueSelectPhase(currentSiteIssues) : (() => {
                    document.getElementById('rmDescription').value = buildSiteDescription(site, currentSiteIssues.length ? currentSiteIssues : [{type:'ERROR',city:'',message:'',historyUrl:'',matchingUrl:''}]);
                    document.getElementById('rmCreateBlock').classList.remove('d-none');
                    document.getElementById('rmCreateBtn').classList.remove('d-none');
                })();
                return;
            }
            loadFormPhase(opts, checkType, issueData);
            document.getElementById('rmDescription').value = issueData.description || '';
            const comments = issueData.comments || [];
            if (comments.length > 0) {
                const list = document.getElementById('rmCommentsList'); list.innerHTML = '';
                comments.forEach(c => { const wrap = document.createElement('div'); wrap.className = 'border-start border-secondary ps-2 mb-2'; wrap.style.fontSize = '0.78rem'; const author = document.createElement('span'); author.className = 'fw-semibold'; author.textContent = c.author || ''; const date = document.createElement('span'); date.className = 'text-muted ms-2'; date.style.fontSize = '0.75rem'; date.textContent = c.date || ''; const text = document.createElement('div'); text.className = 'text-secondary mt-1'; text.style.whiteSpace = 'pre-wrap'; text.textContent = c.text || ''; wrap.appendChild(author); wrap.appendChild(date); wrap.appendChild(text); list.appendChild(wrap); });
                document.getElementById('rmCommentsBlock').classList.remove('d-none');
                document.getElementById('rmCommentsList').classList.remove('d-none');
            }
            if (currentSiteIssues.length > 1) {
                showIssueSelectPhase(currentSiteIssues);
            } else {
                if (currentSiteIssues.length > 0) document.getElementById('rmNotes').value = buildSiteDescription(site, currentSiteIssues);
                document.getElementById('rmNotesBlock').classList.remove('d-none');
                document.getElementById('rmCreateBlock').classList.remove('d-none');
                document.getElementById('rmUpdateBtn').classList.remove('d-none');
            }
        }).catch(err => { document.getElementById('rmLoading').classList.add('d-none'); showError('Ошибка: ' + err.message); });
    });

    document.getElementById('rmCreateBtn').addEventListener('click', function() {
        if (!currentBtn || !rmOptions) return;
        const btn = this; const orig = btn.innerHTML;
        btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Создаётся...';
        document.getElementById('rmErrorMsg').classList.add('d-none');
        const site = currentBtn.dataset.site;
        const body = Object.assign(collectBody('create', site, document.getElementById('rmDescription').value), {city:'',historyUrl:'',matchingUrl:''});
        fetch('/zoomos/redmine/create', {method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
            .then(r => r.json())
            .then(function(data) {
                if (data.success && data.issue) {
                    replaceAllSiteBtns(site, data.issue);
                    const toHide = currentSelectedIssues.length > 0 ? currentSelectedIssues : currentSiteIssues;
                    toHide.forEach(iss => { const k = site + '|' + (iss.city||'') + '|' + (iss.addressId||'') + '|' + iss.type; hideIssue(k); });
                    showCopyBlock(data.issue.site, data.issue.shortMessage, data.issue.url);
                } else { showError('Ошибка: ' + (data.error || 'неизвестная')); btn.disabled=false; btn.innerHTML=orig; }
            }).catch(err => { showError('Ошибка сети: ' + err.message); btn.disabled=false; btn.innerHTML=orig; });
    });

    document.getElementById('rmUpdateBtn').addEventListener('click', function() {
        if (!currentIssueId || !rmOptions) return;
        const btn = this; const orig = btn.innerHTML;
        btn.disabled = true; btn.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Сохраняется...';
        document.getElementById('rmErrorMsg').classList.add('d-none');
        const site = currentBtn.dataset.site;
        const body = collectBody('edit', site, document.getElementById('rmDescription').value);
        fetch('/zoomos/redmine/update/' + currentIssueId, {method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)})
            .then(r => r.json())
            .then(function(data) {
                if (data.success) {
                    if (data.issue) {
                        const iss = data.issue, btnClass = iss.isClosed ? 'btn-success' : 'btn-danger';
                        document.querySelectorAll('[data-rm-site="' + CSS.escape(site) + '"] a.btn').forEach(a => { a.className = 'btn btn-xs px-2 ' + btnClass; a.title = 'Redmine #' + iss.id; });
                    }
                    const issueUrl = (data.issue && data.issue.url) || document.querySelector('[data-rm-site="' + CSS.escape(site) + '"] a.btn')?.href || '';
                    showCopyBlock(site, body.shortMessage || site, issueUrl || '#' + currentIssueId, 'edit');
                } else { showError('Ошибка: ' + (data.error || 'неизвестная')); btn.disabled=false; btn.innerHTML=orig; }
            }).catch(err => { showError('Ошибка сети: ' + err.message); btn.disabled=false; btn.innerHTML=orig; });
    });

    document.getElementById('rmCopyBtn').addEventListener('click', function() {
        const text = document.getElementById('rmCopyText').textContent;
        const self = this;
        navigator.clipboard.writeText(text).then(() => { self.innerHTML = '<i class="fas fa-check me-1 text-success"></i>Скопировано'; setTimeout(() => { self.innerHTML = '<i class="fas fa-copy me-1"></i>Скопировать'; }, 2000); }).catch(() => { const ta = document.createElement('textarea'); ta.value = text; document.body.appendChild(ta); ta.select(); document.execCommand('copy'); document.body.removeChild(ta); });
    });

})(); // end Redmine IIFE

// ── Копирование ───────────────────────────────────────────────────
function animateCopyBtn(btn, label) {
    const orig = btn.innerHTML;
    btn.innerHTML = '<i class="fas fa-check me-1"></i>' + label;
    setTimeout(() => { btn.innerHTML = orig; }, 2000);
}

function copyIssueDomains(btn) {
    const sites = new Set();
    document.querySelectorAll('.site-row[data-site]').forEach(row => {
        if (!row.classList.contains('issue-checked') && row.dataset.site) sites.add(row.dataset.site);
    });
    navigator.clipboard.writeText([...sites].join('\n')).then(() => animateCopyBtn(btn, 'Скопировано'));
}

function copyIssueProblems(btn) {
    const lines = [];
    const seen = new Set();
    document.querySelectorAll('.site-row[data-site]').forEach(row => {
        if (row.classList.contains('issue-checked')) return;
        const site = row.dataset.site || '';
        const city = row.dataset.city || '';
        const msg  = row.dataset.message || '';
        const key  = site + '|' + city + '|' + msg;
        if (!site || seen.has(key)) return;
        seen.add(key);
        const parts = [site, city].filter(Boolean).join(' - ');
        lines.push(parts + (msg ? ` (${msg})` : ''));
    });
    navigator.clipboard.writeText(lines.join('\n')).then(() => animateCopyBtn(btn, 'Скопировано'));
}

// ── Конфиг. проблема (не в выкачке) ─────────────────────────────
let _ciBtn = null;

document.addEventListener('click', function(e) {
    const btn = e.target.closest('.btn-config-issue');
    if (!btn) return;
    _ciBtn = btn;
    const hasIssue = btn.dataset.hasIssue === 'true';
    document.getElementById('ciSiteName').textContent = btn.dataset.site || '';
    document.getElementById('ciType').value = btn.dataset.currentType || '';
    document.getElementById('ciNote').value = btn.dataset.currentNote || '';
    document.getElementById('ciClearFlag').checked = false;
    document.getElementById('ciClearRow').style.display = hasIssue ? '' : 'none';
    document.getElementById('ciError').classList.add('d-none');
    bootstrap.Modal.getOrCreateInstance(document.getElementById('configIssueModal')).show();
});

document.getElementById('ciSaveBtn')?.addEventListener('click', function () {
    const cityIdId = _ciBtn?.dataset.cityidId;
    if (!cityIdId) {
        document.getElementById('ciError').textContent = 'Не удалось определить cityIdId';
        document.getElementById('ciError').classList.remove('d-none');
        return;
    }
    const clearFlag = document.getElementById('ciClearFlag').checked;
    const type = clearFlag ? '' : document.getElementById('ciType').value.trim();
    const note = clearFlag ? '' : document.getElementById('ciNote').value.trim();
    const errEl = document.getElementById('ciError');
    if (!clearFlag && !type) { errEl.textContent = 'Выберите тип проблемы'; errEl.classList.remove('d-none'); return; }
    errEl.classList.add('d-none');
    this.disabled = true;
    const self = this;
    const fd = new FormData();
    if (!clearFlag) { fd.append('type', type); if (note) fd.append('note', note); }
    fetch('/zoomos/city-ids/' + cityIdId + '/config-issue', { method: 'POST', body: fd })
        .then(r => r.json())
        .then(data => {
            self.disabled = false;
            if (!data.success) { errEl.textContent = data.error || 'Ошибка'; errEl.classList.remove('d-none'); return; }
            bootstrap.Modal.getOrCreateInstance(document.getElementById('configIssueModal')).hide();
            const hasNow = data.hasConfigIssue;
            const site = _ciBtn?.dataset.site;
            // Обновить все кнопки и бейджи для этого сайта
            document.querySelectorAll('.btn-config-issue[data-site="' + CSS.escape(site) + '"]').forEach(b => {
                b.dataset.hasIssue = hasNow ? 'true' : 'false';
                b.dataset.currentType = data.configIssueType || '';
                b.dataset.currentNote = data.configIssueNote || '';
                b.className = 'btn btn-xs ' + (hasNow ? 'btn-warning' : 'btn-outline-secondary') + ' btn-config-issue';
                b.title = hasNow ? ('Конфиг. проблема: ' + (data.configIssueNote || '')) : 'Отметить: проблема не в выкачке';
            });
            const opt = document.querySelector('#ciType option[value="' + CSS.escape(data.configIssueType || '') + '"]');
            const typeLabel = opt ? opt.textContent.trim() : (data.configIssueType || '');
            document.querySelectorAll('.site-row[data-site="' + CSS.escape(site) + '"]').forEach(row => {
                let badge = row.querySelector('.ci-badge-v2');
                if (hasNow) {
                    if (!badge) {
                        badge = document.createElement('span');
                        badge.className = 'badge bg-warning text-dark ci-badge-v2';
                        badge.style.fontSize = '0.65rem';
                        const nameEl = row.querySelector('.site-name');
                        if (nameEl) nameEl.insertAdjacentElement('afterend', badge);
                    }
                    badge.textContent = '⚙ ' + typeLabel;
                    badge.title = 'Конфиг. проблема: ' + (data.configIssueNote || '');
                } else if (badge) { badge.remove(); }
            });
        })
        .catch(() => { self.disabled = false; errEl.textContent = 'Ошибка сети'; errEl.classList.remove('d-none'); });
});

// ── Копирование ненастроенных и "не выкачка" ──────────────────────
function getTypeLabel(typeVal) {
    const opt = document.querySelector('#ciType option[value="' + CSS.escape(typeVal) + '"]');
    return opt ? opt.textContent.trim() : typeVal;
}

function copyNotConfiguredSites(btn) {
    const sites = new Set();
    document.querySelectorAll('.site-row[data-site]').forEach(row => {
        if ([...row.querySelectorAll('.badge')].some(b => b.textContent.includes('не настроен'))) {
            sites.add(row.dataset.site);
        }
    });
    if (!sites.size) return;
    navigator.clipboard.writeText([...sites].join('\n')).then(() => animateCopyBtn(btn, 'Скопировано ' + sites.size));
}

function copyConfigIssues(btn) {
    const lines = [];
    const seen = new Set();
    document.querySelectorAll('.btn-config-issue[data-has-issue="true"]').forEach(function(b) {
        const site = b.dataset.site || '';
        if (!site || seen.has(site)) return;
        seen.add(site);
        const type  = b.dataset.currentType || '';
        const note  = b.dataset.currentNote || '';
        const label = getTypeLabel(type) || type;
        let line = site;
        if (label) line += ' — ' + label;
        if (note)  line += ': ' + note;
        lines.push(line);
    });
    if (!lines.length) return;
    navigator.clipboard.writeText(lines.join('\n')).then(() => animateCopyBtn(btn, 'Скопировано ' + lines.length));
}

// ── CITIES_EQUAL_PRICES ───────────────────────────────────────────
let _eqSiteId = null, _eqSiteName = null;

function fetchEqPricesForSite(btn) {
    const siteId = btn.dataset.siteId, siteName = btn.dataset.site;
    const hasMasterCity = !!document.querySelector(
        '.site-row[data-site="' + CSS.escape(siteName) + '"] .badge[title="Выкачка только по мастер-городу"]');
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
            if (data.itemPriceConfigured === true)
                document.querySelectorAll('.site-row[data-site="' + CSS.escape(siteName) + '"] .badge[title="Сайт не настроен"]')
                    .forEach(b => b.remove());
            if (data.success && data.citiesEqualPrices === true && data.historicalCities?.length)
                showEqModal(siteName, siteId, data.historicalCities);
        })
        .catch(() => { btn.disabled = false; btn.innerHTML = origHtml; });
}

function updateEqBadge(siteName, val, checkedAt) {
    document.querySelectorAll('.site-row[data-site="' + CSS.escape(siteName) + '"] .btn-fetch-eq-prices')
        .forEach(syncBtn => {
            syncBtn.classList.remove('btn-outline-info', 'btn-outline-success', 'btn-outline-secondary');
            if (val === true)       syncBtn.classList.add('btn-outline-success');
            else if (val === false) syncBtn.classList.add('btn-outline-info');
            else                   syncBtn.classList.add('btn-outline-secondary');
            const when = checkedAt || syncBtn.dataset.checkedAt || '';
            syncBtn.title = when
                ? ('= цены: ' + (val === true ? 'ДА' : 'НЕТ') + ', проверка ' + when)
                : 'Проверить CITIES_EQUAL_PRICES';
            syncBtn.innerHTML = '<i class="fas fa-sync-alt me-1"></i>=цены';
        });
}

function applyMasterCity(city) {
    if (!_eqSiteId) return;
    const trimmedCity = (city || '').trim();
    const msgEl = document.getElementById('eqMasterMsg');
    msgEl.classList.add('d-none');
    fetch('/zoomos/sites/' + _eqSiteId + '/master-city', {
        method: 'POST',
        headers: {'Content-Type': 'application/x-www-form-urlencoded'},
        body: 'masterCityId=' + encodeURIComponent(trimmedCity)
    }).then(r => r.json()).then(data => {
        if (data.success) {
            msgEl.textContent = trimmedCity ? '\u2713 Мастер-город: ' + trimmedCity : '\u2713 Мастер-город снят';
            msgEl.classList.remove('d-none');
            document.querySelectorAll('.site-row[data-site="' + CSS.escape(_eqSiteName) + '"]')
                .forEach(row => {
                    let badge = row.querySelector('.badge[title="Выкачка только по мастер-городу"]');
                    if (trimmedCity) {
                        if (!badge) {
                            badge = document.createElement('span');
                            badge.className = 'badge bg-info bg-opacity-75 text-dark';
                            badge.style.fontSize = '0.65rem';
                            badge.title = 'Выкачка только по мастер-городу';
                            row.querySelector('.site-name')?.insertAdjacentElement('afterend', badge);
                        }
                        badge.textContent = '\u2197 ' + trimmedCity;
                    } else if (badge) { badge.remove(); }
                });
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
    bootstrap.Modal.getOrCreateInstance(document.getElementById('eqPricesModal')).show();
}

document.getElementById('eqManualSetBtn')?.addEventListener('click', function() {
    applyMasterCity(document.getElementById('eqManualCityId').value);
});
document.getElementById('eqClearBtn')?.addEventListener('click', function() {
    applyMasterCity('');
});

async function fetchAllEqPrices() {
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
        document.querySelectorAll('.site-row[data-site="' + CSS.escape(btn.dataset.site) + '"] .btn-fetch-eq-prices')
            .forEach(b => { b.disabled = true; b.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i>Проверка\u2026'; });
        try {
            const data = await fetch('/zoomos/sites/' + btn.dataset.siteId + '/fetch-equal-prices',
                { method: 'POST' }).then(r => r.json());
            const now = new Date().toLocaleString('ru-RU',
                {day:'2-digit',month:'2-digit',year:'numeric',hour:'2-digit',minute:'2-digit'});
            updateEqBadge(btn.dataset.site, data.citiesEqualPrices, now);
            if (data.itemPriceConfigured === true)
                document.querySelectorAll('.site-row[data-site="' + CSS.escape(btn.dataset.site) + '"] .badge[title="Сайт не настроен"]')
                    .forEach(b => b.remove());
        } catch(e) {
            document.querySelectorAll('.site-row[data-site="' + CSS.escape(btn.dataset.site) + '"] .btn-fetch-eq-prices')
                .forEach(b => { b.disabled = false; b.innerHTML = origHtml; });
        }
        document.querySelectorAll('.site-row[data-site="' + CSS.escape(btn.dataset.site) + '"] .btn-fetch-eq-prices')
            .forEach(b => b.disabled = false);
        if (progressText) progressText.textContent = (++done) + '/' + btns.length;
    }
    if (globalBtn) globalBtn.disabled = false;
    if (progressEl) progressEl.classList.add('d-none');
}

// ── Инициализация ─────────────────────────────────────────────────
document.addEventListener('DOMContentLoaded', function() {
    applyHidden();
    setTimeout(runBatchCheck, 100);
});
