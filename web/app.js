/* PartOut PWA — AI part scanner & repair assistant (Gemini) */
'use strict';

const MODEL = 'gemini-2.5-flash';
const API_BASE = 'https://generativelanguage.googleapis.com/v1beta/models/';

// ---------------------------------------------------------------------------
// Tiny IndexedDB key-value store (for scans + chat, which carry images)
// ---------------------------------------------------------------------------
const idb = {
  db: null,
  open() {
    if (this.db) return Promise.resolve(this.db);
    return new Promise((resolve, reject) => {
      const req = indexedDB.open('partout-db', 1);
      req.onupgradeneeded = () => req.result.createObjectStore('kv');
      req.onsuccess = () => { this.db = req.result; resolve(this.db); };
      req.onerror = () => reject(req.error);
    });
  },
  async get(key) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const req = db.transaction('kv').objectStore('kv').get(key);
      req.onsuccess = () => resolve(req.result);
      req.onerror = () => reject(req.error);
    });
  },
  async set(key, value) {
    const db = await this.open();
    return new Promise((resolve, reject) => {
      const tx = db.transaction('kv', 'readwrite');
      tx.objectStore('kv').put(value, key);
      tx.oncomplete = () => resolve();
      tx.onerror = () => reject(tx.error);
    });
  }
};

// ---------------------------------------------------------------------------
// App state
// ---------------------------------------------------------------------------
const state = {
  tab: 'scan',                 // scan | mechanic | garage
  scanScreen: 'capture',       // capture | preview | analyzing | result | pullguide | error
  previewDataUrl: null,        // full-size (downscaled 1280) data URL for analysis
  scanContext: '',
  currentScan: null,           // scan record being viewed
  errorMessage: null,
  analyzeStatusTimer: null,
  pullGuideLoading: false,
  scans: [],                   // [{id, ts, img, context, result, pullGuide?}]
  chat: [],                    // [{id, role, text, img?, isError?}]
  chatAttach: null,            // data URL pending attachment
  chatSending: false,
  deferredInstallPrompt: null
};

const $ = (sel, root = document) => root.querySelector(sel);
const view = $('#view');

const esc = (s) => String(s ?? '').replace(/[&<>"']/g, (c) => (
  { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
));

const getKey = () => localStorage.getItem('po_api_key') || '';
const getVehicle = () => localStorage.getItem('po_vehicle') || '';

function toast(msg) {
  const el = $('#toast');
  el.textContent = msg;
  el.classList.remove('hidden');
  clearTimeout(toast._t);
  toast._t = setTimeout(() => el.classList.add('hidden'), 2200);
}

// ---------------------------------------------------------------------------
// Images
// ---------------------------------------------------------------------------
function fileToDownscaledDataUrl(file, maxDim, quality = 0.8) {
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const img = new Image();
    img.onload = () => {
      URL.revokeObjectURL(url);
      let { width, height } = img;
      if (width > maxDim || height > maxDim) {
        const scale = maxDim / Math.max(width, height);
        width = Math.max(1, Math.round(width * scale));
        height = Math.max(1, Math.round(height * scale));
      }
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      canvas.getContext('2d').drawImage(img, 0, 0, width, height);
      resolve(canvas.toDataURL('image/jpeg', quality));
    };
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('Could not load that image.')); };
    img.src = url;
  });
}

function shrinkDataUrl(dataUrl, maxDim, quality = 0.75) {
  return new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      let { width, height } = img;
      if (width <= maxDim && height <= maxDim) { resolve(dataUrl); return; }
      const scale = maxDim / Math.max(width, height);
      const canvas = document.createElement('canvas');
      canvas.width = Math.max(1, Math.round(width * scale));
      canvas.height = Math.max(1, Math.round(height * scale));
      canvas.getContext('2d').drawImage(img, 0, 0, canvas.width, canvas.height);
      resolve(canvas.toDataURL('image/jpeg', quality));
    };
    img.onerror = () => resolve(dataUrl);
    img.src = dataUrl;
  });
}

const dataUrlToBase64 = (dataUrl) => dataUrl.slice(dataUrl.indexOf(',') + 1);

// ---------------------------------------------------------------------------
// Gemini
// ---------------------------------------------------------------------------
async function gemini(request) {
  const key = getKey();
  if (!key) { openKeyDialog(); throw new Error('Add your free Gemini API key to use AI features.'); }
  let res;
  try {
    res = await fetch(`${API_BASE}${MODEL}:generateContent?key=${encodeURIComponent(key)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request)
    });
  } catch {
    throw new Error('Network error — check your internet connection and try again.');
  }
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    if (res.status === 400 && /API key not valid|API_KEY_INVALID/i.test(body)) {
      throw new Error('Your Gemini API key was rejected. Double-check it (aistudio.google.com/apikey) and re-enter it via the 🔑 button.');
    }
    if (res.status === 401 || res.status === 403) throw new Error('Your Gemini API key isn’t authorized for this request. Re-check it via the 🔑 button.');
    if (res.status === 429) throw new Error('You’ve hit the Gemini rate limit. Wait a minute and try again.');
    if (res.status >= 500) throw new Error('Gemini is temporarily overloaded. Try again in a moment.');
    throw new Error(`Gemini request failed (HTTP ${res.status}). Try again.`);
  }
  const data = await res.json();
  const text = (data.candidates?.[0]?.content?.parts || []).map((p) => p.text || '').join('').trim();
  if (!text) throw new Error('Empty response from Gemini. Try again.');
  return text;
}

function parseJsonLoose(raw) {
  let t = raw.trim();
  if (t.startsWith('```json')) t = t.slice(7);
  else if (t.startsWith('```')) t = t.slice(3);
  if (t.endsWith('```')) t = t.slice(0, -3);
  return JSON.parse(t.trim());
}

async function analyzePart(imageBase64, userContext) {
  const vehicle = getVehicle();
  let contextPrompt = '';
  if (vehicle) {
    contextPrompt += `Owner's Vehicle: ${vehicle}\n(IMPORTANT: Anchor your identification and compatibility fitment to this vehicle when plausible.)\n`;
  }
  if (userContext) {
    contextPrompt += `Additional User Context: ${userContext}\n(IMPORTANT: Weight this context heavily for fitment/compatibility. Do not ignore it.)\n`;
  }

  const prompt = `Analyze the provided photo of an automotive part.
${contextPrompt}
Identify the part name, its category, guess unverified part numbers, evaluate conditions from the image, estimate Private Party price in USD (low, typical, high), determine the removal effort (grade and note), and generate a high-quality ready-to-post marketplace listing.

Respond strictly in valid JSON matching this exact schema:
{
  "identified": true,
  "part_name": "string",
  "category": "Engine | Electrical | Suspension | Brakes | Body | Interior | Drivetrain | Cooling | Exhaust | Other",
  "confidence": "high | medium | low",
  "confidence_reason": "one sentence",
  "possible_part_numbers": ["string"],
  "compatibility": [ { "make": "string", "models": ["string"], "year_range": "string" } ],
  "condition_observations": ["string", "string"],
  "condition_grade": "Like New | Good | Fair | Poor | For Parts",
  "price_estimate_usd": { "low": 0, "typical": 0, "high": 0 },
  "pricing_rationale": "one sentence",
  "removal_effort": { "grade": "easy | moderate | hard", "note": "short phrase like '4 bolts + one connector'" },
  "listing": {
    "title": "max 80 chars, keyword-rich for marketplace search",
    "description": "3-5 sentences: what it is, fitment, condition, buyer guidance",
    "condition_disclosure": "honest 1-2 sentence disclosure of flaws"
  }
}`;

  const system = `You are an expert automotive parts identifier and used-parts pricing analyst with junkyard, dealership, and eBay Motors experience.

Analyze the provided photo of an automotive part. Respond ONLY with valid JSON matching the specified schema. Do not output markdown code blocks or wrapper text.

Rules:
- If the image is not an automotive part, set identified to false and include a "message" field politely saying what you see instead.
- Never invent certainty. If unsure of exact fitment, give broader year ranges and set confidence to medium or low.
- Part numbers are best guesses — the UI labels them as unverified.
- Price estimates reflect USED private-party sale values in the US market.
- If a vehicle context is included, anchor predictions to compatibility/fitment with that vehicle.
- Provide a removal effort estimate representing how difficult it is to remove this part from the specified or typical vehicle.`;

  const raw = await gemini({
    contents: [{ parts: [
      { text: prompt },
      { inline_data: { mime_type: 'image/jpeg', data: imageBase64 } }
    ] }],
    generationConfig: { response_mime_type: 'application/json', temperature: 0.2 },
    systemInstruction: { parts: [{ text: system }] }
  });
  return parseJsonLoose(raw);
}

async function getPullGuide(imageBase64, partName) {
  const vehicle = getVehicle() || 'Typical vehicle';
  const prompt = `You are assisting with removing the following part:
Part Name: ${partName}
Vehicle/Context: ${vehicle}

Examine the provided photo carefully. Locate any visible fasteners (screws, bolts, clips, connectors, pins, tabs, pry points).
If you can confidently locate them in the photo, provide their locations in the 'annotations' array. Use normalized coordinates from 0 to 1000 (where x: 0 is left, 1000 is right, y: 0 is top, 1000 is bottom).
If you cannot confidently locate specific fasteners or connectors on this actual photo, return an empty 'annotations' array.

Respond strictly in valid JSON matching this exact schema:
{
  "removal_steps": [ { "step_number": 1, "instruction": "string", "tools": ["string"] } ],
  "tools_needed": ["string"],
  "time_estimate_minutes": 15,
  "preserve_value_tips": ["string"],
  "safety_warnings": ["string"],
  "annotations": [ { "label": "1", "point": { "x": 450, "y": 620 }, "description": "string" } ]
}`;

  const system = `You are a master mechanic and expert salvage yard advisor. You provide highly accurate, safe, and professional guides for removing automotive parts.
Analyze the user's photo of the part to locate fasteners/clips. Provide ordered removal steps (1-2 sentences each), a tool list, a time estimate in minutes, value-preservation tips, clear safety warnings, and photo annotations using 0-1000 normalized coordinates (empty array if not clearly visible).
Respond ONLY with valid JSON matching the specified schema. Do not output markdown code blocks or wrapper text.`;

  const raw = await gemini({
    contents: [{ parts: [
      { text: prompt },
      { inline_data: { mime_type: 'image/jpeg', data: imageBase64 } }
    ] }],
    generationConfig: { response_mime_type: 'application/json', temperature: 0.2 },
    systemInstruction: { parts: [{ text: system }] }
  });
  return parseJsonLoose(raw);
}

async function mechanicChat(history) {
  const vehicle = getVehicle();
  let system = 'You are a master automotive technician helping a vehicle owner diagnose and repair their own vehicle in a home garage.';
  if (vehicle) {
    system += `\nThe owner's vehicle: ${vehicle}. Anchor all diagnosis, part names, fluid specs, and procedures to this exact vehicle whenever possible.`;
  }
  system += `

How to respond:
- When they describe a symptom, give the most likely causes ranked by probability for their vehicle, and for each: how to test or confirm it cheaply before buying parts.
- When walking through a repair, give clear numbered steps, the tools and socket sizes needed, parts to buy (with common part names), rough time, and difficulty.
- If a photo is provided, examine it closely and reference what you actually see.
- Ask a short clarifying question when key info is missing (mileage, when it happens, sounds, recent work).
- Include safety warnings when the job involves jack stands, fuel, brakes, airbags, cooling system pressure, or battery.
- Be concise and practical. Use plain text only: no markdown symbols like ** or #, use plain numbered lists (1., 2., 3.) and short paragraphs.`;

  const turns = history.slice(-16).map((m) => {
    const parts = [];
    if (m.text) parts.push({ text: m.text });
    if (m.img) parts.push({ inline_data: { mime_type: 'image/jpeg', data: dataUrlToBase64(m.img) } });
    if (!parts.length) parts.push({ text: '' });
    return { role: m.role === 'user' ? 'user' : 'model', parts };
  });

  return gemini({
    contents: turns,
    generationConfig: { temperature: 0.6 },
    systemInstruction: { parts: [{ text: system }] }
  });
}

// ---------------------------------------------------------------------------
// Rendering
// ---------------------------------------------------------------------------
function render() {
  document.querySelectorAll('.tab').forEach((t) => t.classList.toggle('active', t.dataset.tab === state.tab));
  if (state.tab === 'scan') renderScan();
  else if (state.tab === 'mechanic') renderMechanic();
  else renderGarage();
}

// ----- Scan tab -----
function renderScan() {
  const s = state.scanScreen;
  if (s === 'capture') return renderCapture();
  if (s === 'preview') return renderPreview();
  if (s === 'analyzing') return renderAnalyzing();
  if (s === 'result') return renderResult();
  if (s === 'pullguide') return renderPullGuide();
  if (s === 'error') return renderScanError();
}

function renderCapture() {
  view.innerHTML = `
    <div class="kicker">PART SCANNER</div>
    <div class="h1">Scan a Part</div>
    <div class="sub">Photograph any automotive part. Gemini identifies it, grades condition, estimates used value, and drafts a listing.</div>

    <div class="capture-hero">
      <div class="big">📷</div>
      <div class="sub" style="margin-top:8px">Good light, part centered and fully visible.</div>
      <button class="btn" id="cam-btn" type="button">📸 Take Photo</button>
      <button class="btn secondary" id="gal-btn" type="button">🖼️ Choose from Gallery</button>
    </div>

    <div class="field">
      <label for="scan-context">Extra context for better accuracy (optional)</label>
      <textarea id="scan-context" rows="2" placeholder="e.g. came off a 1997 Jeep Wrangler TJ, OEM part">${esc(state.scanContext)}</textarea>
    </div>

    <input type="file" id="cam-input" accept="image/*" capture="environment" class="hidden">
    <input type="file" id="gal-input" accept="image/*" class="hidden">
  `;
  $('#cam-btn').onclick = () => $('#cam-input').click();
  $('#gal-btn').onclick = () => $('#gal-input').click();
  $('#scan-context').oninput = (e) => { state.scanContext = e.target.value; };
  const onPick = async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    try {
      state.previewDataUrl = await fileToDownscaledDataUrl(file, 1280);
      state.scanScreen = 'preview';
      render();
    } catch (err) {
      toast(err.message || 'Could not load that image.');
    }
  };
  $('#cam-input').onchange = onPick;
  $('#gal-input').onchange = onPick;
}

function renderPreview() {
  view.innerHTML = `
    <div class="kicker">VERIFY PHOTO QUALITY</div>
    <div class="h1">Ready to analyze?</div>
    <div class="sub">Make sure the part is well-lit, centered, and fully visible.</div>
    <img class="preview-img" src="${state.previewDataUrl}" alt="Captured part">
    <div class="btn-row">
      <button class="btn secondary" id="retake-btn" type="button">↺ Retake</button>
      <button class="btn" id="analyze-btn" type="button">✨ Analyze</button>
    </div>
  `;
  $('#retake-btn').onclick = () => { state.previewDataUrl = null; state.scanScreen = 'capture'; render(); };
  $('#analyze-btn').onclick = startAnalysis;
}

const ANALYZE_STATUSES = [
  'Identifying part…', 'Checking compatibility…', 'Estimating value…',
  'Synthesizing listing data…', 'Formulating condition observations…'
];

function renderAnalyzing() {
  view.innerHTML = `
    <div class="analyzing-wrap">
      <div class="spinner"></div>
      <div class="kicker" style="margin-top:20px">PARTOUT ANALYSIS HUD</div>
      <div class="analyzing-status" id="an-status">${ANALYZE_STATUSES[0]}</div>
      <div class="sub" style="margin-top:8px">Gemini is estimating used-market values and compatibility fitment.</div>
    </div>
  `;
  let i = 0;
  clearInterval(state.analyzeStatusTimer);
  state.analyzeStatusTimer = setInterval(() => {
    i = (i + 1) % ANALYZE_STATUSES.length;
    const el = $('#an-status');
    if (el) el.textContent = ANALYZE_STATUSES[i];
  }, 2000);
}

async function startAnalysis() {
  if (!getKey()) { openKeyDialog(); return; }
  state.scanScreen = 'analyzing';
  render();
  try {
    const result = await analyzePart(dataUrlToBase64(state.previewDataUrl), state.scanContext.trim());
    clearInterval(state.analyzeStatusTimer);
    const record = {
      id: 'scan_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8),
      ts: Date.now(),
      img: await shrinkDataUrl(state.previewDataUrl, 800),
      context: state.scanContext.trim(),
      result
    };
    if (result.identified) {
      state.scans.unshift(record);
      idb.set('scans', state.scans).catch(() => {});
    }
    state.currentScan = record;
    state.scanScreen = 'result';
    render();
  } catch (err) {
    clearInterval(state.analyzeStatusTimer);
    state.errorMessage = err.message || 'Couldn’t read that one — try a clearer photo.';
    state.scanScreen = 'error';
    render();
  }
}

function effortBadgeClass(grade) {
  const g = (grade || '').toLowerCase().trim();
  if (g === 'easy') return 'green';
  if (g === 'moderate') return 'yellow';
  if (g === 'hard') return 'red';
  return 'orange';
}

function confBadgeClass(conf) {
  const c = (conf || '').toLowerCase();
  if (c === 'high') return 'green';
  if (c === 'medium') return 'yellow';
  return 'red';
}

function renderScanError() {
  view.innerHTML = `
    <div class="state-wrap">
      <div class="state-icon">⚠️</div>
      <div class="state-title">Analysis Interrupted</div>
      <div class="state-sub">${esc(state.errorMessage)}</div>
      <button class="btn" id="retry-btn" type="button" style="margin-top:18px">↺ Try Another Photo</button>
    </div>
  `;
  $('#retry-btn').onclick = () => { state.previewDataUrl = null; state.errorMessage = null; state.scanScreen = 'capture'; render(); };
}

function renderResult() {
  const rec = state.currentScan;
  const r = rec?.result;
  if (!r) { state.scanScreen = 'capture'; return render(); }

  if (!r.identified) {
    view.innerHTML = `
      <div class="state-wrap">
        <div class="state-icon">❓</div>
        <div class="state-title">Part Not Identified</div>
        <div class="state-sub">${esc(r.message || "This image doesn't look like an automotive component.")}</div>
        <button class="btn" id="retry-btn" type="button" style="margin-top:18px">↺ Try Another Photo</button>
      </div>
    `;
    $('#retry-btn').onclick = () => { state.previewDataUrl = null; state.scanScreen = 'capture'; render(); };
    return;
  }

  const price = r.price_estimate_usd || { low: 0, typical: 0, high: 0 };
  const compat = Array.isArray(r.compatibility) ? r.compatibility : [];
  const partNums = Array.isArray(r.possible_part_numbers) ? r.possible_part_numbers : [];
  const obs = Array.isArray(r.condition_observations) ? r.condition_observations : [];
  const effort = r.removal_effort;
  const listing = r.listing;

  view.innerHTML = `
    <button class="pill-btn" id="back-capture" type="button" style="background:var(--surface);border:1px solid var(--border)">← New Scan</button>

    <div class="card result-card">
      ${rec.img ? `<img class="result-head-img" src="${rec.img}" alt="Scanned part">` : ''}
      <div class="inner">
        <div class="row-between">
          <span class="kicker">${esc((r.category || 'OTHER').toUpperCase())}</span>
          <span class="badge ${confBadgeClass(r.confidence)}">${esc((r.confidence || 'LOW').toUpperCase())} CONFIDENCE</span>
        </div>
        <div class="part-name">${esc(r.part_name || 'Unidentified Part')}</div>
        <div class="sub" style="margin-top:6px">${esc(r.confidence_reason || '')}</div>
      </div>
    </div>

    ${effort ? `
    <div class="card">
      <div class="row-between">
        <div class="card-title" style="margin-bottom:0">🔧 REMOVAL EFFORT</div>
        <span class="badge ${effortBadgeClass(effort.grade)}">${esc((effort.grade || '').toUpperCase())}</span>
      </div>
      <div class="sub" style="color:var(--text);margin-top:8px">${esc(effort.note || '')}</div>
    </div>` : ''}

    <div class="card">
      <div class="card-title">🚗 COMPATIBILITY FITMENT</div>
      ${compat.length ? compat.map((c) => `
        <div class="compat-row">
          <div style="min-width:0">
            <div class="compat-make">${esc((c.make || '').toUpperCase())}</div>
            <div class="compat-models">${esc((c.models || []).join(', '))}</div>
          </div>
          <span class="badge orange">${esc(c.year_range || '')}</span>
        </div>`).join('') : '<div class="sub">Broad universal compatibility or unverified custom fitment.</div>'}
    </div>

    <div class="card">
      <div class="card-title">#️⃣ ESTIMATED PART NUMBERS</div>
      <div class="sub" style="color:var(--primary);font-weight:700;font-size:11px;margin-bottom:8px">AI estimate — verify before relying on these</div>
      ${partNums.length ? `<div class="chip-row">${partNums.map((p) => `<span class="chip">${esc(p)}</span>`).join('')}</div>` : '<div class="sub">No specific part numbers identified.</div>'}
    </div>

    <div class="card">
      <div class="row-between">
        <div class="card-title" style="margin-bottom:0">👁️ CONDITION</div>
        <span class="badge orange">${esc((r.condition_grade || 'GOOD').toUpperCase())}</span>
      </div>
      ${obs.length ? `<ul class="obs" style="margin-top:10px">${obs.map((o) => `<li>${esc(o)}</li>`).join('')}</ul>` : '<div class="sub" style="margin-top:8px">No critical visual flaws identified.</div>'}
    </div>

    <div class="card">
      <div class="card-title">💰 USED-MARKET PRICE RANGE</div>
      <div class="price-cols">
        <div class="col"><div class="lbl">LOW</div><div class="val">$${Math.round(price.low || 0)}</div></div>
        <div class="col typ"><div class="lbl">TYPICAL</div><div class="val">$${Math.round(price.typical || 0)}</div></div>
        <div class="col"><div class="lbl">HIGH</div><div class="val">$${Math.round(price.high || 0)}</div></div>
      </div>
      <div class="sub" style="text-align:center;font-size:11.5px">${esc(r.pricing_rationale || 'Private sale values reflecting local demand.')}</div>
    </div>

    ${listing ? `
    <div class="card">
      <div class="row-between">
        <div class="card-title" style="margin-bottom:0">🏷️ MARKETPLACE LISTING</div>
        <button class="pill-btn" id="copy-listing" type="button">Copy</button>
      </div>
      <div class="listing-block" id="listing-text">${esc(listing.title || '')}

${esc(listing.description || '')}

Condition: ${esc(listing.condition_disclosure || '')}</div>
    </div>` : ''}

    <button class="btn" id="pullguide-btn" type="button" style="background:#F57C00">🔧 Pull Guide — how to remove this part</button>
    <button class="btn outline-primary" id="askmech-btn" type="button">🛠️ Ask AI Mechanic — repair help</button>
    <button class="btn secondary" id="scan-another" type="button">📷 Scan Another Part</button>
  `;

  $('#back-capture').onclick = () => { state.previewDataUrl = null; state.scanScreen = 'capture'; render(); };
  $('#scan-another').onclick = () => { state.previewDataUrl = null; state.scanScreen = 'capture'; render(); };
  const copyBtn = $('#copy-listing');
  if (copyBtn) copyBtn.onclick = () => {
    navigator.clipboard.writeText($('#listing-text').innerText)
      .then(() => toast('Listing copied to clipboard!'))
      .catch(() => toast('Copy failed — select the text manually.'));
  };
  $('#pullguide-btn').onclick = loadPullGuide;
  $('#askmech-btn').onclick = () => {
    state.chatAttach = rec.img;
    state.chatDraft = `Help me with this ${r.part_name || 'part'} — how do I check if it's bad, and how do I replace it?`;
    state.tab = 'mechanic';
    render();
  };
}

async function loadPullGuide() {
  const rec = state.currentScan;
  if (!rec) return;
  if (!getKey()) { openKeyDialog(); return; }
  if (rec.pullGuide) { state.scanScreen = 'pullguide'; render(); return; }

  state.pullGuideLoading = true;
  state.scanScreen = 'pullguide';
  render();
  try {
    const src = state.previewDataUrl || rec.img;
    rec.pullGuide = await getPullGuide(dataUrlToBase64(src), rec.result.part_name || 'this part');
    idb.set('scans', state.scans).catch(() => {});
  } catch (err) {
    state.pullGuideLoading = false;
    state.errorMessage = err.message || 'Could not generate removal steps for this part.';
    state.scanScreen = 'error';
    render();
    return;
  }
  state.pullGuideLoading = false;
  render();
}

function renderPullGuide() {
  const rec = state.currentScan;
  if (state.pullGuideLoading) {
    view.innerHTML = `
      <div class="analyzing-wrap">
        <div class="spinner"></div>
        <div class="kicker" style="margin-top:20px">GENERATING PULL GUIDE</div>
        <div class="analyzing-status">Consulting master mechanic…</div>
        <div class="sub" style="margin-top:8px">Analyzing fastener locations and safe removal procedures.</div>
      </div>`;
    return;
  }
  const g = rec?.pullGuide;
  if (!g) { state.scanScreen = 'result'; return render(); }

  const steps = Array.isArray(g.removal_steps) ? g.removal_steps : [];
  const tools = Array.isArray(g.tools_needed) ? g.tools_needed : [];
  const tips = Array.isArray(g.preserve_value_tips) ? g.preserve_value_tips : [];
  const warns = Array.isArray(g.safety_warnings) ? g.safety_warnings : [];
  const anns = Array.isArray(g.annotations) ? g.annotations : [];

  view.innerHTML = `
    <button class="pill-btn" id="back-result" type="button" style="background:var(--surface);border:1px solid var(--border)">← Back to Specs</button>
    <div class="kicker" style="margin-top:14px">REMOVAL PROCEDURES</div>
    <div class="h1">Pull Guide: ${esc(rec.result.part_name || 'Part')}</div>

    <div class="card result-card">
      <div class="pg-photo-wrap">
        ${rec.img ? `<img class="result-head-img" style="border-radius:15px" src="${rec.img}" alt="Part photo">` : ''}
        ${anns.map((a) => `
          <div class="pg-marker" style="left:${Math.min(100, Math.max(0, (a.point?.x ?? 0) / 10))}%;top:${Math.min(100, Math.max(0, (a.point?.y ?? 0) / 10))}%" title="${esc(a.description || '')}">${esc(a.label || '•')}</div>
        `).join('')}
      </div>
      ${anns.length ? `<div class="inner"><div class="sub">Tap a numbered marker to see what it is.</div>
        <ul class="obs" style="margin-top:6px">${anns.map((a) => `<li><strong>${esc(a.label)}</strong> — ${esc(a.description || '')}</li>`).join('')}</ul></div>` : ''}
    </div>

    <div class="card">
      <div class="row-between">
        <div class="card-title" style="margin-bottom:0">🧰 TOOLS &amp; TIME</div>
        <span class="badge orange">${Number(g.time_estimate_minutes) || '?'} MINS</span>
      </div>
      <ul class="obs" style="margin-top:10px">${tools.map((t) => `<li>${esc(t)}</li>`).join('')}</ul>
    </div>

    <div class="card">
      <div class="card-title">📋 REMOVAL STEPS</div>
      ${steps.map((s) => `
        <div class="step">
          <div class="step-num">${esc(String(s.step_number ?? ''))}</div>
          <div>
            <div class="step-text">${esc(s.instruction || '')}</div>
            ${Array.isArray(s.tools) && s.tools.length ? `<div class="step-tools">Tools: ${esc(s.tools.join(', '))}</div>` : ''}
          </div>
        </div>`).join('')}
    </div>

    ${tips.length ? `
    <div class="card tip-border">
      <div class="card-title" style="color:var(--primary)">🏷️ PROTECT THE VALUE</div>
      <ul class="obs">${tips.map((t) => `<li>${esc(t)}</li>`).join('')}</ul>
    </div>` : ''}

    ${warns.length ? `
    <div class="card warn-border">
      <div class="card-title" style="color:var(--red)">⚠️ SAFETY WARNINGS</div>
      <ul class="obs warn">${warns.map((w) => `<li>${esc(w)}</li>`).join('')}</ul>
    </div>` : ''}

    <div class="disclaimer">AI-generated guidance — verify against a repair manual. Do not rely on this alone for brakes, airbags, fuel, or high-voltage systems.</div>
  `;
  $('#back-result').onclick = () => { state.scanScreen = 'result'; render(); };
}

// ----- Mechanic tab -----
const SUGGESTIONS = [
  'My engine cranks but won’t start. Where do I begin?',
  'I hear a grinding noise when I brake. What should I check?',
  'The check engine light just came on. What do I do first?',
  'It shakes badly at highway speed. How do I diagnose it?'
];

function renderMechanic() {
  const msgs = state.chat;
  view.innerHTML = `
    <div class="mech-wrap">
      <div class="row-between">
        <div>
          <div class="kicker">AI MECHANIC</div>
          <div class="h1" style="font-size:22px">Repair Assistant</div>
        </div>
        ${msgs.length ? '<button class="icon-btn" id="clear-chat" type="button" title="Clear chat">🗑️</button>' : ''}
      </div>

      <div class="field" style="margin-top:6px">
        <label for="vehicle-input">Your vehicle (answers get anchored to it)</label>
        <input id="vehicle-input" type="text" placeholder="e.g. 1997 Jeep Wrangler TJ 4.0L" value="${esc(getVehicle())}">
      </div>

      <div class="mech-scroll" id="mech-scroll">
        ${msgs.length || state.chatSending ? msgs.map(bubbleHtml).join('') : `
          <div class="state-wrap" style="padding-top:4dvh">
            <div class="state-icon">🛠️</div>
            <div class="state-title">What are we fixing today?</div>
            <div class="state-sub">Describe the symptom, attach a photo of the part, and get a diagnosis with step-by-step repair help.</div>
            <div class="suggestions">
              ${SUGGESTIONS.map((s, i) => `<button class="suggestion" data-sugg="${i}" type="button">${esc(s)}</button>`).join('')}
            </div>
            <div class="state-sub" style="font-size:11px;opacity:.75;margin-top:14px">AI guidance — verify torque specs and anything safety-critical against a repair manual.</div>
          </div>`}
        ${state.chatSending ? `<div class="bubble-row"><div class="bubble"><div class="thinking"><span class="dot-spin"></span>Wrenching on it…</div></div></div>` : ''}
      </div>

      ${state.chatAttach ? `
      <div class="attach-preview">
        <img src="${state.chatAttach}" alt="Attached photo">
        <span class="sub">Photo attached — the mechanic will look at it.</span>
        <button class="attach-remove" id="attach-remove" type="button" aria-label="Remove photo">✕</button>
      </div>` : ''}

      <div class="mech-inputbar">
        <button class="round-btn" id="attach-btn" type="button" title="Attach photo">🖼️</button>
        <textarea id="mech-input" rows="1" placeholder="Describe the problem or ask a question…">${esc(state.chatDraft || '')}</textarea>
        <button class="round-btn send" id="send-btn" type="button" title="Send" ${state.chatSending ? 'disabled' : ''}>➤</button>
      </div>
      <input type="file" id="chat-file" accept="image/*" class="hidden">
    </div>
  `;
  state.chatDraft = '';

  const scroll = $('#mech-scroll');
  scroll.scrollTop = scroll.scrollHeight;

  $('#vehicle-input').oninput = (e) => localStorage.setItem('po_vehicle', e.target.value);
  const clearBtn = $('#clear-chat');
  if (clearBtn) clearBtn.onclick = () => {
    state.chat = [];
    idb.set('chat', state.chat).catch(() => {});
    render();
  };
  document.querySelectorAll('.suggestion').forEach((b) => {
    b.onclick = () => { $('#mech-input').value = SUGGESTIONS[Number(b.dataset.sugg)]; $('#mech-input').focus(); };
  });
  $('#attach-btn').onclick = () => $('#chat-file').click();
  $('#chat-file').onchange = async (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    try {
      state.chatAttach = await fileToDownscaledDataUrl(file, 1024);
      state.chatDraft = $('#mech-input').value;
      render();
    } catch (err) {
      toast(err.message || 'Could not load that image.');
    }
  };
  const removeBtn = $('#attach-remove');
  if (removeBtn) removeBtn.onclick = () => { state.chatDraft = $('#mech-input').value; state.chatAttach = null; render(); };

  const input = $('#mech-input');
  input.addEventListener('input', () => {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 120) + 'px';
  });
  $('#send-btn').onclick = sendChat;
}

function bubbleHtml(m) {
  return `
    <div class="bubble-row ${m.role === 'user' ? 'user' : ''}">
      <div class="bubble ${m.isError ? 'error' : ''}">
        ${m.img ? `<img class="bubble-img" src="${m.img}" alt="Attached photo">` : ''}
        ${m.text ? `<div class="bubble-text">${esc(m.text)}</div>` : ''}
      </div>
    </div>`;
}

async function sendChat() {
  if (state.chatSending) return;
  if (!getKey()) { openKeyDialog(); return; }
  const input = $('#mech-input');
  const text = input.value.trim();
  const img = state.chatAttach;
  if (!text && !img) return;

  state.chat.push({ id: 'm' + Date.now(), role: 'user', text, img });
  state.chatAttach = null;
  state.chatSending = true;
  render();

  try {
    const reply = await mechanicChat(state.chat.filter((m) => !m.isError));
    state.chat.push({ id: 'm' + Date.now() + 'r', role: 'model', text: reply });
  } catch (err) {
    state.chat.push({ id: 'm' + Date.now() + 'e', role: 'model', text: err.message || 'Something went wrong. Try sending that again.', isError: true });
  }
  state.chatSending = false;
  idb.set('chat', state.chat.filter((m) => !m.isError)).catch(() => {});
  render();
}

// ----- Garage tab -----
function renderGarage() {
  const scans = state.scans;
  view.innerHTML = `
    <div class="kicker">GARAGE LOGS</div>
    <div class="h1">Scan History</div>
    <div class="sub">Every part you've scanned, saved on this device. Tap one to reopen it.</div>
    ${scans.length ? scans.map((s) => {
      const r = s.result || {};
      const d = new Date(s.ts);
      return `
      <div class="hist-row" data-id="${esc(s.id)}">
        ${s.img ? `<img class="hist-thumb" src="${s.img}" alt="">` : '<div class="hist-thumb"></div>'}
        <div class="hist-info">
          <div class="hist-cat">${esc((r.category || 'OTHER').toUpperCase())}</div>
          <div class="hist-name">${esc(r.part_name || 'Unidentified Part')}</div>
          <div class="hist-meta">${esc(d.toLocaleDateString())} ${esc(d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' }))} · <span class="hist-price">$${Math.round(r.price_estimate_usd?.typical || 0)} typical</span></div>
        </div>
        <div style="color:var(--text-2)">›</div>
      </div>`;
    }).join('') : `
      <div class="state-wrap">
        <div class="state-icon">🗂️</div>
        <div class="state-title">No Parts Scanned Yet</div>
        <div class="state-sub">Capture a photo of any automotive part to identify it and estimate its value.</div>
        <button class="btn" id="go-scan" type="button" style="margin-top:16px">📷 Scan Your First Part</button>
      </div>`}
  `;
  const goScan = $('#go-scan');
  if (goScan) goScan.onclick = () => { state.tab = 'scan'; state.scanScreen = 'capture'; render(); };
  document.querySelectorAll('.hist-row').forEach((row) => {
    row.onclick = () => {
      const rec = state.scans.find((s) => s.id === row.dataset.id);
      if (!rec) return;
      state.currentScan = rec;
      state.previewDataUrl = null;
      state.tab = 'scan';
      state.scanScreen = 'result';
      render();
    };
  });
}

// ---------------------------------------------------------------------------
// API key dialog
// ---------------------------------------------------------------------------
function openKeyDialog() {
  const root = $('#modal-root');
  root.innerHTML = `
    <div class="modal-backdrop" id="key-backdrop">
      <div class="modal">
        <h2>🔑 Gemini API Key</h2>
        <p>The AI features need a free Google Gemini API key. Get one in about a minute at
          <a href="https://aistudio.google.com/apikey" target="_blank" rel="noopener">aistudio.google.com/apikey</a>,
          then paste it below. It's stored only on this device.</p>
        <div class="field">
          <label for="key-input">API Key</label>
          <input id="key-input" type="password" placeholder="AIza…" value="${esc(getKey())}" autocomplete="off">
        </div>
        <div class="btn-row">
          <button class="btn secondary" id="key-cancel" type="button">Cancel</button>
          <button class="btn" id="key-save" type="button">Save Key</button>
        </div>
      </div>
    </div>
  `;
  $('#key-cancel').onclick = () => { root.innerHTML = ''; };
  $('#key-backdrop').onclick = (e) => { if (e.target.id === 'key-backdrop') root.innerHTML = ''; };
  $('#key-save').onclick = () => {
    const v = $('#key-input').value.trim();
    if (!v) return;
    localStorage.setItem('po_api_key', v);
    root.innerHTML = '';
    toast('API key saved on this device.');
  };
}

// ---------------------------------------------------------------------------
// PWA install
// ---------------------------------------------------------------------------
function setupPwa() {
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.register('sw.js').catch(() => {});
  }

  window.addEventListener('beforeinstallprompt', (e) => {
    e.preventDefault();
    state.deferredInstallPrompt = e;
    $('#install-btn').classList.remove('hidden');
  });
  $('#install-btn').onclick = async () => {
    const p = state.deferredInstallPrompt;
    if (!p) return;
    p.prompt();
    await p.userChoice;
    state.deferredInstallPrompt = null;
    $('#install-btn').classList.add('hidden');
  };
  window.addEventListener('appinstalled', () => {
    $('#install-btn').classList.add('hidden');
    toast('PartOut installed! Find it on your home screen.');
  });

  // iOS has no install prompt API — show a one-time hint in Safari.
  const isIos = /iphone|ipad|ipod/i.test(navigator.userAgent) ||
    (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  const standalone = window.matchMedia('(display-mode: standalone)').matches || window.navigator.standalone === true;
  if (isIos && !standalone && !localStorage.getItem('po_ios_hint_dismissed')) {
    $('#ios-hint').classList.remove('hidden');
  }
  $('#ios-hint-close').onclick = () => {
    $('#ios-hint').classList.add('hidden');
    localStorage.setItem('po_ios_hint_dismissed', '1');
  };
}

// ---------------------------------------------------------------------------
// Boot
// ---------------------------------------------------------------------------
async function boot() {
  document.querySelectorAll('.tab').forEach((t) => {
    t.onclick = () => { state.tab = t.dataset.tab; render(); };
  });
  $('#key-btn').onclick = openKeyDialog;
  setupPwa();

  try {
    state.scans = (await idb.get('scans')) || [];
    state.chat = (await idb.get('chat')) || [];
  } catch { /* storage unavailable — run in-memory */ }

  render();

  if (!getKey()) openKeyDialog();
}

boot();
