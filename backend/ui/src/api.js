/** Thin fetch wrapper for the Labl backend API. */

export function imageUrl(key) {
  return `/images?key=${encodeURIComponent(key)}`;
}

export async function uploadImage(file, applianceType) {
  const form = new FormData();
  form.append('image', file);
  if (applianceType) form.append('appliance_type', applianceType);

  const res = await fetch('/upload', { method: 'POST', body: form });
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json(); // { session_id, key }
}

export async function fetchLabels(status = 'pending') {
  const res = await fetch(`/labels?status=${status}`);
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json(); // SymbolLabel[]
}

/** Poll until labels for this session appear (up to ~30 s). */
export async function pollForSession(sessionId, signal) {
  for (let i = 0; i < 20; i++) {
    await sleep(1500);
    if (signal?.aborted) throw new DOMException('Aborted', 'AbortError');
    const all = await fetchLabels('pending');
    const mine = all.filter(l => l.session_id === sessionId);
    if (mine.length > 0) return mine;
  }
  return [];
}

/**
 * Fetch up to `limit` non-rejected labels with crop images that share
 * the same category (and optionally appliance_type).
 */
export async function fetchSimilarLabels(category, applianceType, limit = 8) {
  const params = new URLSearchParams({ category, limit });
  if (applianceType) params.set('appliance_type', applianceType);
  const res = await fetch(`/labels/similar?${params}`);
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json(); // SymbolLabel[]
}

/** Update the name and description of a stored label. */
export async function updateLabel(id, name, description) {
  const res = await fetch('/labels/update', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id, name, description }),
  });
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json();
}

/** Approve or reject a stored label by its DB id. */
export async function validateLabel(id, approved) {
  const res = await fetch('/labels/validate', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ id, approved }),
  });
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json();
}

/**
 * Upload an image for classification preview.
 * Returns { session_id, s3_key, appliance_type, symbols[] } without storing any labels.
 */
export async function classifyPreview(file, applianceType) {
  const form = new FormData();
  form.append('image', file);
  if (applianceType) form.append('appliance_type', applianceType);

  const res = await fetch('/classify/preview', { method: 'POST', body: form });
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json(); // { session_id, s3_key, appliance_type, symbols[] }
}

/**
 * Save user-adjusted bounding boxes as finalized SymbolLabel records.
 * symbols[] should be the edited boxes from the bounding-box editor.
 */
export async function saveLabels({ sessionId, applianceType, s3Key, symbols }) {
  const res = await fetch('/labels/save', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      session_id: sessionId,
      appliance_type: applianceType,
      s3_key: s3Key,
      symbols,
    }),
  });
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json(); // { count, ids }
}

/** Submit feedback for a live (not yet stored) classification result. */
export async function submitFeedback({ name, category, description, confidence, approved }) {
  const res = await fetch('/labels/feedback', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, category, description, confidence, approved }),
  });
  if (!res.ok) throw new Error(await res.text().catch(() => res.statusText));
  return res.json();
}

function sleep(ms) {
  return new Promise(r => setTimeout(r, ms));
}
