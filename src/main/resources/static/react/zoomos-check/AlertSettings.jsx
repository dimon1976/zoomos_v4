import { useState, useCallback, useEffect, useRef } from "react";

// ─── Constants ────────────────────────────────────────────────────────────────

const PRESETS = [
  { name: "Консервативный", pct: 50, abs: 120, logic: "AND", sigma: 3,   window: 10, minAbs: 120 },
  { name: "Сбалансированный", pct: 30, abs: 60,  logic: "AND", sigma: 2,   window: 5,  minAbs: 60  },
  { name: "Чувствительный",   pct: 15, abs: 30,  logic: "OR",  sigma: 1.5, window: 5,  minAbs: 30  },
];

const TEST_CASES = [
  { label: "1 → 7",     prev: 1,   curr: 7,   note: "мелкий рост, не критично" },
  { label: "500 → 700", prev: 500, curr: 700, note: "крупный рост, критично"   },
  { label: "60 → 90",   prev: 60,  curr: 90,  note: "умеренный рост"           },
  { label: "200 → 250", prev: 200, curr: 250, note: "небольшой рост"           },
  { label: "5 → 50",    prev: 5,   curr: 50,  note: "900% — но с малой базы"  },
  { label: "300 → 600", prev: 300, curr: 600, note: "удвоение времени"        },
];

// ─── Logic ────────────────────────────────────────────────────────────────────

function calcSimple(prev, curr) {
  const diff   = curr - prev;
  const pctVal = prev > 0 ? (diff / prev) * 100 : 0;
  return { diff, pctVal };
}

function checkSimple(prev, curr, pct, abs, logic) {
  const { diff, pctVal } = calcSimple(prev, curr);
  const passPct = pctVal >= pct;
  const passAbs = diff   >= abs;
  const triggered = logic === "AND" ? passPct && passAbs : passPct || passAbs;
  return { triggered, pctVal, diff, passPct, passAbs };
}

function checkAdvanced(prev, curr, sigma, minAbs) {
  const diff         = curr - prev;
  const estimatedStd = Math.max(prev * 0.15, 1);
  const zScore       = diff / estimatedStd;
  const passZ        = zScore  >= sigma;
  const passAbs      = diff    >= minAbs;
  const triggered    = passZ && passAbs;
  return { triggered, diff, zScore, estimatedStd, passZ, passAbs };
}

// ─── Micro-components ─────────────────────────────────────────────────────────

function SliderRow({ label, value, min, max, step = 1, unit = "", onChange, leftLabel, rightLabel }) {
  return (
    <div style={{ marginBottom: 24 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline", marginBottom: 10 }}>
        <label style={{ fontSize: 12, color: "#8b949e", letterSpacing: 0.5 }}>{label}</label>
        <span style={{
          fontSize: 20, fontWeight: 700, color: "#e2c97e",
          textShadow: "0 0 12px rgba(226,201,126,0.3)",
          transition: "all 0.15s",
          minWidth: 80, textAlign: "right",
        }}>
          {value}{unit}
        </span>
      </div>
      <input type="range" min={min} max={max} step={step} value={value}
        onChange={e => onChange(Number(e.target.value))} />
      {(leftLabel || rightLabel) && (
        <div style={{ display: "flex", justifyContent: "space-between", fontSize: 10, color: "#3d4451", marginTop: 5 }}>
          <span>{leftLabel ?? min}{unit}</span>
          <span>{rightLabel ?? max}{unit}</span>
        </div>
      )}
    </div>
  );
}

function StatusBadge({ triggered }) {
  return (
    <span style={{
      fontSize: 10, fontWeight: 700, letterSpacing: 1.5,
      padding: "3px 10px", borderRadius: 3,
      background: triggered ? "rgba(248,81,73,0.15)" : "rgba(56,193,114,0.1)",
      color:      triggered ? "#f85149"               : "#38c172",
      border:     `1px solid ${triggered ? "rgba(248,81,73,0.3)" : "rgba(56,193,114,0.2)"}`,
      transition: "all 0.3s",
    }}>
      {triggered ? "ПРОБЛЕМА" : "OK"}
    </span>
  );
}

function Dot({ active, color = "#f85149" }) {
  return (
    <span style={{
      display: "inline-block", width: 7, height: 7, borderRadius: "50%",
      background: active ? color : "#2a3040",
      boxShadow: active ? `0 0 8px ${color}88` : "none",
      transition: "all 0.3s",
    }} />
  );
}

// ─── Main component ───────────────────────────────────────────────────────────

export default function AlertSettings() {
  const [mode,       setMode]       = useState("simple");
  const [pct,        setPct]        = useState(30);
  const [abs,        setAbs]        = useState(60);
  const [logic,      setLogic]      = useState("AND");
  const [sigma,      setSigma]      = useState(2);
  const [histWindow, setHistWindow] = useState(5);
  const [minAbs,     setMinAbs]     = useState(60);
  const [customPrev, setCustomPrev] = useState("");
  const [customCurr, setCustomCurr] = useState("");
  const [activePreset, setActivePreset] = useState(1); // "Сбалансированный"

  const applyPreset = useCallback((p, idx) => {
    setPct(p.pct); setAbs(p.abs); setLogic(p.logic);
    setSigma(p.sigma); setHistWindow(p.window); setMinAbs(p.minAbs);
    setActivePreset(idx);
  }, []);

  // Reset active preset when user moves a slider
  const handleSlider = (setter) => (val) => { setter(val); setActivePreset(null); };

  const evaluate = (prev, curr) =>
    mode === "simple"
      ? checkSimple(prev, curr, pct, abs, logic)
      : checkAdvanced(prev, curr, sigma, minAbs);

  const allCases = [...TEST_CASES];
  const cp = Number(customPrev), cc = Number(customCurr);
  if (cp > 0 && cc > 0) {
    allCases.push({ label: `${cp} → ${cc}`, prev: cp, curr: cc, note: "пользовательский сценарий", custom: true });
  }

  const results     = allCases.map(tc => ({ ...tc, result: evaluate(tc.prev, tc.curr) }));
  const alertCount  = results.filter(r => r.result.triggered).length;
  const totalCount  = results.length;

  return (
    <div style={{
      fontFamily: "'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace",
      background: "#080b10",
      color: "#c9d1d9",
      minHeight: "100vh",
      padding: "40px 24px 80px",
      position: "relative",
      overflow: "hidden",
    }}>
      <style>{`
        @import url('https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@300;400;500;600;700&display=swap');
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }

        /* Subtle scanline overlay */
        body::before {
          content: ''; position: fixed; inset: 0; pointer-events: none; z-index: 999;
          background: repeating-linear-gradient(
            0deg, transparent, transparent 2px,
            rgba(0,0,0,0.03) 2px, rgba(0,0,0,0.03) 4px
          );
        }

        input[type=range] {
          -webkit-appearance: none; appearance: none;
          width: 100%; height: 4px; border-radius: 2px;
          background: linear-gradient(to right, #e2c97e var(--val, 50%), #1e2433 var(--val, 50%));
          outline: none; cursor: pointer;
        }
        input[type=range]::-webkit-slider-thumb {
          -webkit-appearance: none; appearance: none;
          width: 16px; height: 16px; border-radius: 50%;
          background: #e2c97e; cursor: pointer;
          border: 2px solid #080b10;
          box-shadow: 0 0 10px rgba(226,201,126,0.5);
          transition: transform 0.1s;
        }
        input[type=range]::-webkit-slider-thumb:hover { transform: scale(1.2); }

        input[type=number] {
          background: #0d1117; border: 1px solid #1e2433;
          color: #c9d1d9; border-radius: 4px; padding: 8px 12px;
          font-family: inherit; font-size: 13px; width: 100%;
          outline: none; transition: border-color 0.2s;
          -moz-appearance: textfield;
        }
        input[type=number]:focus { border-color: #e2c97e; box-shadow: 0 0 0 2px rgba(226,201,126,0.1); }
        input[type=number]::-webkit-inner-spin-button,
        input[type=number]::-webkit-outer-spin-button { -webkit-appearance: none; }

        .tab-btn {
          flex: 1; padding: 10px 16px; border: none; border-radius: 5px;
          cursor: pointer; font-family: inherit; font-size: 12px; font-weight: 500;
          transition: all 0.2s; text-align: left;
        }
        .tab-btn.active {
          background: #131a26;
          color: #e6edf3;
          border-left: 2px solid #e2c97e;
        }
        .tab-btn:not(.active) {
          background: transparent; color: #3d4451;
        }
        .tab-btn:not(.active):hover { color: #8b949e; background: #0d1117; }

        .preset-btn {
          padding: 5px 14px; border-radius: 3px; cursor: pointer;
          font-family: inherit; font-size: 11px; font-weight: 500;
          transition: all 0.2s; letter-spacing: 0.5px;
          border: 1px solid #1e2433;
          background: transparent; color: #484f58;
        }
        .preset-btn.active {
          background: rgba(226,201,126,0.1);
          border-color: rgba(226,201,126,0.4);
          color: #e2c97e;
        }
        .preset-btn:not(.active):hover {
          border-color: #3d4451; color: #8b949e;
        }

        .sim-row {
          display: grid;
          grid-template-columns: 110px 1fr auto;
          align-items: center; gap: 12px;
          padding: 10px 14px; border-radius: 4px;
          border: 1px solid transparent;
          transition: all 0.3s;
          margin-bottom: 6px;
        }
        .sim-row.alert {
          background: rgba(248,81,73,0.06);
          border-color: rgba(248,81,73,0.18);
        }
        .sim-row.ok {
          background: rgba(56,193,114,0.04);
          border-color: rgba(56,193,114,0.1);
        }

        @keyframes fadeSlideIn {
          from { opacity: 0; transform: translateY(8px); }
          to   { opacity: 1; transform: translateY(0); }
        }
        .panel { animation: fadeSlideIn 0.2s ease; }
      `}</style>

      {/* Grid texture */}
      <div style={{
        position: "fixed", inset: 0, pointerEvents: "none", zIndex: 0,
        backgroundImage: `
          linear-gradient(rgba(30,36,51,0.3) 1px, transparent 1px),
          linear-gradient(90deg, rgba(30,36,51,0.3) 1px, transparent 1px)
        `,
        backgroundSize: "40px 40px",
      }} />

      <div style={{ maxWidth: 740, margin: "0 auto", position: "relative", zIndex: 1 }}>

        {/* ── Header ── */}
        <div style={{ marginBottom: 36 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 6 }}>
            <span style={{
              width: 6, height: 6, borderRadius: "50%", background: "#e2c97e",
              boxShadow: "0 0 10px rgba(226,201,126,0.8)",
              animation: "pulse 2s ease-in-out infinite",
            }} />
            <span style={{ fontSize: 10, letterSpacing: 3, color: "#3d4451", textTransform: "uppercase" }}>
              zoomos · мониторинг
            </span>
          </div>
          <h1 style={{ fontSize: 24, fontWeight: 700, color: "#e6edf3", lineHeight: 1.2, marginBottom: 6 }}>
            Пороги оповещений
          </h1>
          <p style={{ fontSize: 12, color: "#484f58", lineHeight: 1.6 }}>
            Когда замедление выкачки считается проблемой
          </p>
        </div>

        {/* ── Mode + Presets row ── */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 24, flexWrap: "wrap", gap: 12 }}>
          <div style={{ display: "flex", gap: 2, background: "#0d1117", borderRadius: 6, padding: 3 }}>
            {[
              { key: "simple",   label: "Базовый" },
              { key: "advanced", label: "Продвинутый" },
            ].map(m => (
              <button key={m.key} className={`tab-btn${mode === m.key ? " active" : ""}`}
                onClick={() => setMode(m.key)}>
                {m.label}
              </button>
            ))}
          </div>
          <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
            {PRESETS.map((p, i) => (
              <button key={p.name} className={`preset-btn${activePreset === i ? " active" : ""}`}
                onClick={() => applyPreset(p, i)}>
                {p.name}
              </button>
            ))}
          </div>
        </div>

        {/* ── Settings panel ── */}
        <div style={{
          background: "#0d1117", border: "1px solid #1e2433", borderRadius: 8,
          padding: "28px 28px 24px", marginBottom: 24,
        }}>
          {mode === "simple" ? (
            <div className="panel" key="simple">
              <SliderRow
                label="Порог роста в процентах"
                value={pct} min={5} max={500} unit="%"
                onChange={handleSlider(setPct)}
                leftLabel="5%" rightLabel="500%"
              />

              {/* AND/OR toggle */}
              <div style={{ display: "flex", alignItems: "center", gap: 12, margin: "4px 0 24px" }}>
                <span style={{ fontSize: 11, color: "#3d4451", flexShrink: 0 }}>Логика:</span>
                <div style={{ display: "flex", borderRadius: 4, overflow: "hidden", border: "1px solid #1e2433" }}>
                  {["AND", "OR"].map(l => (
                    <button key={l} onClick={() => { setLogic(l); setActivePreset(null); }} style={{
                      padding: "7px 20px", border: "none", cursor: "pointer",
                      fontFamily: "inherit", fontSize: 12, fontWeight: 700,
                      transition: "all 0.2s",
                      background: logic === l
                        ? (l === "AND" ? "rgba(56,193,114,0.15)"  : "rgba(226,201,126,0.12)")
                        : "#080b10",
                      color: logic === l
                        ? (l === "AND" ? "#38c172" : "#e2c97e")
                        : "#3d4451",
                    }}>
                      {l}
                    </button>
                  ))}
                </div>
                <span style={{ fontSize: 11, color: "#484f58" }}>
                  {logic === "AND"
                    ? "оба условия — меньше ложных срабатываний"
                    : "любое условие — не пропустит крупные проблемы"}
                </span>
              </div>

              <SliderRow
                label="Порог абсолютного роста"
                value={abs} min={1} max={500} unit=" мин"
                onChange={handleSlider(setAbs)}
                leftLabel="1 мин" rightLabel="500 мин"
              />

              {/* Formula */}
              <FormulaBox>
                triggered = (<Val color="#e2c97e">{pct}%</Val> &le; рост%)
                {" "}
                <Logic>{logic === "AND" ? "&&" : "||"}</Logic>
                {" "}
                (<Val color="#e2c97e">{abs} мин</Val> &le; рост_мин)
              </FormulaBox>
            </div>
          ) : (
            <div className="panel" key="advanced">
              <SliderRow
                label="Окно истории (последних проверок)"
                value={histWindow} min={3} max={30}
                onChange={handleSlider(setHistWindow)}
                leftLabel="3" rightLabel="30"
              />
              <SliderRow
                label="Чувствительность (σ — стандартных отклонений)"
                value={sigma} min={1} max={4} step={0.5}
                onChange={handleSlider(setSigma)}
                leftLabel="1σ чувствительно" rightLabel="4σ только аномалии"
              />
              <SliderRow
                label="Минимальный абсолютный рост"
                value={minAbs} min={1} max={500} unit=" мин"
                onChange={handleSlider(setMinAbs)}
                leftLabel="1 мин" rightLabel="500 мин"
              />

              {/* Formula */}
              <FormulaBox>
                z = (curr - prev) / std<br/>
                std &asymp; prev &times; 0.15 &nbsp;·&nbsp; baseline: последние <Val color="#e2c97e">{histWindow}</Val> проверок<br/>
                triggered = (z &ge; <Val color="#e2c97e">{sigma}σ</Val>) <Logic>&&</Logic> (рост_мин &ge; <Val color="#e2c97e">{minAbs}</Val>)
              </FormulaBox>
            </div>
          )}
        </div>

        {/* ── Simulator ── */}
        <div style={{
          background: "#0d1117", border: "1px solid #1e2433", borderRadius: 8,
          padding: "24px 24px 20px",
        }}>
          {/* Simulator header */}
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 18 }}>
            <span style={{ fontSize: 10, letterSpacing: 2.5, color: "#3d4451", textTransform: "uppercase" }}>
              симулятор
            </span>
            <div style={{ display: "flex", alignItems: "center", gap: 8, fontSize: 11 }}>
              <span style={{ color: "#3d4451" }}>срабатывает</span>
              <span style={{
                fontWeight: 700, fontSize: 16,
                color: alertCount > 0 ? "#f85149" : "#38c172",
                textShadow: alertCount > 0 ? "0 0 10px rgba(248,81,73,0.4)" : "0 0 10px rgba(56,193,114,0.3)",
                transition: "all 0.3s",
              }}>
                {alertCount}
              </span>
              <span style={{ color: "#3d4451" }}>из {totalCount}</span>
            </div>
          </div>

          {/* Column headers */}
          <div style={{
            display: "grid",
            gridTemplateColumns: "110px 1fr auto",
            gap: 12, paddingBottom: 8, marginBottom: 4,
            borderBottom: "1px solid #1a1f2e",
          }}>
            {["сценарий", mode === "simple" ? "пct / абс" : "z-score / абс", "статус"].map(h => (
              <span key={h} style={{ fontSize: 9, color: "#2a3040", letterSpacing: 2, textTransform: "uppercase" }}>
                {h}
              </span>
            ))}
          </div>

          {/* Rows */}
          {results.map((tc, i) => {
            const { triggered, diff, pctVal, zScore, passZ, passAbs, passPct } = tc.result;
            return (
              <div key={i} className={`sim-row ${triggered ? "alert" : "ok"}`}>

                {/* Label */}
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600, color: "#c9d1d9" }}>
                    {tc.label}
                    {tc.custom && <span style={{ fontSize: 9, color: "#e2c97e", marginLeft: 6 }}>★</span>}
                  </div>
                  <div style={{ fontSize: 10, color: "#2a3040", marginTop: 2 }}>{tc.note}</div>
                </div>

                {/* Metrics */}
                <div style={{ display: "flex", gap: 16, flexWrap: "wrap" }}>
                  {mode === "simple" ? (
                    <>
                      <Metric label="%" value={`${pctVal != null ? Math.round(pctVal) : "∞"}%`} pass={passPct} />
                      <Metric label="мин" value={`+${diff}`} pass={passAbs} />
                    </>
                  ) : (
                    <>
                      <Metric label="z" value={zScore != null ? zScore.toFixed(2) : "—"} pass={passZ} />
                      <Metric label="мин" value={`+${diff}`} pass={passAbs} />
                    </>
                  )}
                </div>

                {/* Status */}
                <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
                  <Dot active={triggered} color="#f85149" />
                  <StatusBadge triggered={triggered} />
                </div>
              </div>
            );
          })}

          {/* Custom input */}
          <div style={{
            display: "flex", gap: 10, alignItems: "center",
            marginTop: 16, paddingTop: 16, borderTop: "1px solid #1a1f2e",
          }}>
            <span style={{ fontSize: 10, color: "#3d4451", whiteSpace: "nowrap", letterSpacing: 1 }}>ТЕСТ:</span>
            <input type="number" placeholder="было, мин" min={0}
              value={customPrev} onChange={e => setCustomPrev(e.target.value)} />
            <span style={{ color: "#2a3040", fontSize: 16, flexShrink: 0 }}>→</span>
            <input type="number" placeholder="стало, мин" min={0}
              value={customCurr} onChange={e => setCustomCurr(e.target.value)} />
          </div>
        </div>

      </div>
    </div>
  );
}

// ─── Tiny helpers ─────────────────────────────────────────────────────────────

function FormulaBox({ children }) {
  return (
    <div style={{
      marginTop: 20, padding: "14px 16px", borderRadius: 4,
      background: "#080b10", border: "1px solid #1a1f2e",
      fontSize: 11, color: "#484f58", lineHeight: 2,
      fontFamily: "inherit",
    }}>
      <span style={{ fontSize: 9, letterSpacing: 2, color: "#2a3040", display: "block", marginBottom: 6 }}>
        ФОРМУЛА
      </span>
      {children}
    </div>
  );
}

function Val({ color, children }) {
  return <span style={{ color, fontWeight: 700 }}>{children}</span>;
}

function Logic({ children }) {
  return <span style={{ color: "#58a6ff", fontWeight: 700 }}>{children}</span>;
}

function Metric({ label, value, pass }) {
  return (
    <span style={{ fontSize: 11 }}>
      <span style={{ color: "#2a3040" }}>{label} </span>
      <span style={{
        color: pass ? "#f85149" : "#38c172",
        fontWeight: 600,
        transition: "color 0.3s",
      }}>
        {value}
      </span>
    </span>
  );
}
