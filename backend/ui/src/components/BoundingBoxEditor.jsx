import { useState, useRef, useCallback, useEffect } from 'react';
import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import Stack from '@mui/material/Stack';
import IconButton from '@mui/material/IconButton';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Tooltip from '@mui/material/Tooltip';
import Skeleton from '@mui/material/Skeleton';
import DeleteIcon from '@mui/icons-material/Delete';
import AddIcon from '@mui/icons-material/Add';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import { fetchSimilarLabels, imageUrl } from '../api.js';

const CATEGORIES = ['washing', 'drying', 'ironing', 'bleaching', 'dishwasher', 'oven', 'other'];
const HANDLE_SIZE = 10; // px
const MIN_BOX = 0.02;   // fraction

const COLORS = [
  '#00BFA5', '#FF6B6B', '#FFD93D', '#6BCB77', '#4D96FF',
  '#C77DFF', '#FF9F45', '#F72585', '#4CC9F0', '#B5E48C',
];

function boxColor(idx) {
  return COLORS[idx % COLORS.length];
}

/**
 * BoundingBoxEditor
 *
 * Props:
 *   imageUrl      — src for the uploaded image preview
 *   symbols       — array of { name, description, category, confidence, crop_x, crop_y, crop_w, crop_h }
 *   onChange      — called with updated symbols array whenever a box/field changes
 *   applianceType — passed to the similar-labels query for better matching
 */
export default function BoundingBoxEditor({ imageUrl: imgSrc, symbols, onChange, applianceType }) {
  const imgRef      = useRef(null);
  const containerRef = useRef(null);
  const dragRef     = useRef(null);
  const [imgSize, setImgSize] = useState({ w: 1, h: 1 });

  // similar labels keyed by category: Map<string, SymbolLabel[] | 'loading' | null>
  const [similar, setSimilar] = useState({});

  // ── image measurement ─────────────────────────────────────────────────────

  const measureImg = useCallback(() => {
    if (!imgRef.current) return;
    setImgSize({ w: imgRef.current.offsetWidth, h: imgRef.current.offsetHeight });
  }, []);

  useEffect(() => {
    const ro = new ResizeObserver(measureImg);
    if (imgRef.current) ro.observe(imgRef.current);
    return () => ro.disconnect();
  }, [measureImg]);

  // ── fetch similar labels for each unique category in symbols ──────────────

  useEffect(() => {
    const categories = [...new Set(symbols.map(s => s.category).filter(Boolean))];
    categories.forEach(cat => {
      if (similar[cat] !== undefined) return; // already fetched or loading
      setSimilar(prev => ({ ...prev, [cat]: 'loading' }));
      fetchSimilarLabels(cat, applianceType)
        .then(data => setSimilar(prev => ({ ...prev, [cat]: data })))
        .catch(() => setSimilar(prev => ({ ...prev, [cat]: [] })));
    });
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [symbols.map(s => s.category).join(','), applianceType]);

  // ── pointer drag handlers ─────────────────────────────────────────────────

  const startDrag = useCallback((e, idx, type, handle) => {
    e.preventDefault();
    e.stopPropagation();
    dragRef.current = {
      idx, type, handle,
      startX: e.clientX, startY: e.clientY,
      origBox: { ...symbols[idx] },
    };
  }, [symbols]);

  const onMouseMove = useCallback((e) => {
    if (!dragRef.current) return;
    const { idx, type, handle, startX, startY, origBox } = dragRef.current;
    const dx = (e.clientX - startX) / imgSize.w;
    const dy = (e.clientY - startY) / imgSize.h;
    const sym = { ...origBox };

    if (type === 'move') {
      sym.crop_x = clamp(origBox.crop_x + dx, 0, 1 - origBox.crop_w);
      sym.crop_y = clamp(origBox.crop_y + dy, 0, 1 - origBox.crop_h);
    } else {
      if (handle.includes('e')) sym.crop_w = clamp(origBox.crop_w + dx, MIN_BOX, 1 - origBox.crop_x);
      if (handle.includes('s')) sym.crop_h = clamp(origBox.crop_h + dy, MIN_BOX, 1 - origBox.crop_y);
      if (handle.includes('w')) {
        const nx = clamp(origBox.crop_x + dx, 0, origBox.crop_x + origBox.crop_w - MIN_BOX);
        sym.crop_w = origBox.crop_x + origBox.crop_w - nx;
        sym.crop_x = nx;
      }
      if (handle.includes('n')) {
        const ny = clamp(origBox.crop_y + dy, 0, origBox.crop_y + origBox.crop_h - MIN_BOX);
        sym.crop_h = origBox.crop_y + origBox.crop_h - ny;
        sym.crop_y = ny;
      }
    }

    onChange(symbols.map((s, i) => i === idx ? sym : s));
  }, [imgSize, symbols, onChange]);

  const stopDrag = useCallback(() => { dragRef.current = null; }, []);

  useEffect(() => {
    window.addEventListener('mousemove', onMouseMove);
    window.addEventListener('mouseup', stopDrag);
    return () => {
      window.removeEventListener('mousemove', onMouseMove);
      window.removeEventListener('mouseup', stopDrag);
    };
  }, [onMouseMove, stopDrag]);

  // ── symbol list helpers ───────────────────────────────────────────────────

  function deleteBox(idx) {
    onChange(symbols.filter((_, i) => i !== idx));
  }

  function addBox() {
    onChange([...symbols, {
      name: 'New symbol', description: '',
      category: 'other', confidence: 'low',
      crop_x: 0.1, crop_y: 0.1, crop_w: 0.2, crop_h: 0.2,
    }]);
  }

  function updateField(idx, field, value) {
    const updated = symbols.map((s, i) => i === idx ? { ...s, [field]: value } : s);
    // If category changed, trigger a fetch for the new category next render
    if (field === 'category') {
      setSimilar(prev => {
        if (prev[value] !== undefined) return prev;
        return { ...prev, [value]: undefined };
      });
    }
    onChange(updated);
  }

  function applySuggestion(idx, label) {
    onChange(symbols.map((s, i) =>
      i === idx ? { ...s, name: label.name, description: label.description } : s
    ));
  }

  // ── render ────────────────────────────────────────────────────────────────

  return (
    <Box>
      {/* ── Image + bounding box overlay ── */}
      <Box
        ref={containerRef}
        sx={{ position: 'relative', display: 'inline-block', width: '100%', userSelect: 'none' }}
      >
        <img
          ref={imgRef}
          src={imgSrc}
          alt="Classification target"
          onLoad={measureImg}
          style={{ width: '100%', display: 'block', borderRadius: 8 }}
        />

        {symbols.map((sym, idx) => {
          const color  = boxColor(idx);
          const left   = sym.crop_x * imgSize.w;
          const top    = sym.crop_y * imgSize.h;
          const width  = sym.crop_w * imgSize.w;
          const height = sym.crop_h * imgSize.h;

          return (
            <Box
              key={idx}
              sx={{
                position: 'absolute',
                left, top, width, height,
                border: `2px solid ${color}`,
                boxSizing: 'border-box',
                cursor: 'move',
              }}
              onMouseDown={e => startDrag(e, idx, 'move', null)}
            >
              {/* Label badge */}
              <Box sx={{
                position: 'absolute', top: -22, left: 0,
                bgcolor: color, color: '#000',
                fontSize: 10, fontWeight: 700,
                px: 0.7, py: 0.2,
                borderRadius: '3px 3px 0 0',
                whiteSpace: 'nowrap',
                maxWidth: 130, overflow: 'hidden', textOverflow: 'ellipsis',
                lineHeight: '16px',
              }}>
                {idx + 1}. {sym.name}
              </Box>

              {/* Resize handles */}
              {[
                ['nw', { top: -5, left: -5 }],
                ['ne', { top: -5, right: -5 }],
                ['sw', { bottom: -5, left: -5 }],
                ['se', { bottom: -5, right: -5 }],
              ].map(([h, pos]) => (
                <Box
                  key={h}
                  sx={{
                    position: 'absolute',
                    width: HANDLE_SIZE, height: HANDLE_SIZE,
                    bgcolor: color, border: '1px solid #000',
                    borderRadius: '2px', cursor: h + '-resize', ...pos,
                  }}
                  onMouseDown={e => startDrag(e, idx, 'resize', h)}
                />
              ))}
            </Box>
          );
        })}
      </Box>

      {/* ── Symbol list editor ── */}
      <Stack sx={{ mt: 2 }} gap={1.5}>
        <Stack direction="row" alignItems="center" justifyContent="space-between">
          <Typography variant="subtitle2" color="text.secondary" sx={{ letterSpacing: '0.08em' }}>
            {symbols.length === 0
              ? 'NO SYMBOLS'
              : `${symbols.length} SYMBOL${symbols.length !== 1 ? 'S' : ''} — ADJUST BOXES ABOVE`}
          </Typography>
          <Tooltip title="Add symbol manually">
            <IconButton size="small" color="primary" onClick={addBox}>
              <AddIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>

        {symbols.map((sym, idx) => {
          const color       = boxColor(idx);
          const suggestions = similar[sym.category];

          return (
            <Box
              key={idx}
              sx={{
                border: '1px solid',
                borderColor: color + '55',
                borderLeft: `3px solid ${color}`,
                borderRadius: 2,
                p: 1.5,
                bgcolor: 'background.paper',
              }}
            >
              <Stack direction="row" alignItems="flex-start" gap={1}>
                {/* Number badge */}
                <Box sx={{
                  minWidth: 22, height: 22, borderRadius: '50%',
                  bgcolor: color, color: '#000',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 11, fontWeight: 800, mt: 0.5, flexShrink: 0,
                }}>
                  {idx + 1}
                </Box>

                <Stack gap={1} sx={{ flex: 1, minWidth: 0 }}>

                  {/* ── Similar symbols row ── */}
                  <SimilarRow
                    suggestions={suggestions}
                    onApply={label => applySuggestion(idx, label)}
                  />

                  <TextField
                    size="small"
                    label="Name"
                    value={sym.name}
                    onChange={e => updateField(idx, 'name', e.target.value)}
                    fullWidth
                  />
                  <TextField
                    size="small"
                    label="Description"
                    value={sym.description}
                    onChange={e => updateField(idx, 'description', e.target.value)}
                    fullWidth
                    multiline
                    minRows={2}
                  />
                  <Stack direction="row" gap={1}>
                    <FormControl size="small" sx={{ flex: 1 }}>
                      <InputLabel>Category</InputLabel>
                      <Select
                        value={sym.category}
                        label="Category"
                        onChange={e => updateField(idx, 'category', e.target.value)}
                      >
                        {CATEGORIES.map(c => (
                          <MenuItem key={c} value={c}>
                            {c.charAt(0).toUpperCase() + c.slice(1)}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ flex: 1 }}>
                      <InputLabel>Confidence</InputLabel>
                      <Select
                        value={sym.confidence}
                        label="Confidence"
                        onChange={e => updateField(idx, 'confidence', e.target.value)}
                      >
                        {['high', 'medium', 'low'].map(c => (
                          <MenuItem key={c} value={c}>
                            {c.charAt(0).toUpperCase() + c.slice(1)}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Stack>
                </Stack>

                <IconButton
                  size="small"
                  color="error"
                  onClick={() => deleteBox(idx)}
                  sx={{ mt: 0.5, flexShrink: 0 }}
                >
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </Stack>
            </Box>
          );
        })}
      </Stack>
    </Box>
  );
}

// ── SimilarRow ────────────────────────────────────────────────────────────────
// Horizontal scrollable strip of previous classifications for the same category.

function SimilarRow({ suggestions, onApply }) {
  if (suggestions === undefined || suggestions === null) return null;

  if (suggestions === 'loading') {
    return (
      <Stack direction="row" gap={1} sx={{ pb: 0.5 }}>
        {[0, 1, 2].map(i => (
          <Skeleton key={i} variant="rounded" width={64} height={72} />
        ))}
      </Stack>
    );
  }

  if (suggestions.length === 0) return null;

  return (
    <Box>
      <Typography variant="caption" color="text.disabled" sx={{ letterSpacing: '0.06em' }}>
        FROM PREVIOUS CLASSIFICATIONS — CLICK TO REUSE
      </Typography>
      <Box
        sx={{
          display: 'flex',
          gap: 1,
          mt: 0.5,
          pb: 0.5,
          overflowX: 'auto',
          '&::-webkit-scrollbar': { height: 4 },
          '&::-webkit-scrollbar-thumb': { bgcolor: 'rgba(255,255,255,0.15)', borderRadius: 2 },
        }}
      >
        {suggestions.map(label => (
          <Tooltip
            key={label.id}
            title={
              <Box sx={{ maxWidth: 220 }}>
                <Typography variant="caption" fontWeight={700}>{label.name}</Typography>
                {label.description && (
                  <Typography variant="caption" display="block" sx={{ opacity: 0.8, mt: 0.25 }}>
                    {label.description}
                  </Typography>
                )}
                <Typography variant="caption" display="block" sx={{ opacity: 0.5, mt: 0.5 }}>
                  Click to copy name &amp; description
                </Typography>
              </Box>
            }
            placement="top"
            arrow
          >
            <Box
              onClick={() => onApply(label)}
              sx={{
                flexShrink: 0,
                width: 68,
                cursor: 'pointer',
                borderRadius: 1.5,
                border: '1px solid rgba(255,255,255,0.1)',
                bgcolor: 'rgba(255,255,255,0.04)',
                overflow: 'hidden',
                transition: 'border-color 0.15s, background-color 0.15s',
                '&:hover': {
                  borderColor: 'primary.main',
                  bgcolor: 'rgba(0,191,165,0.08)',
                },
              }}
            >
              {/* Crop thumbnail */}
              <Box sx={{
                width: '100%', height: 52,
                bgcolor: 'rgba(0,0,0,0.3)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
                position: 'relative', overflow: 'hidden',
              }}>
                <img
                  src={imageUrl(label.crop_s3_key)}
                  alt={label.name}
                  style={{ width: '100%', height: '100%', objectFit: 'contain' }}
                  onError={e => { e.target.style.display = 'none'; }}
                />
                {/* Copy icon overlay on hover */}
                <Box sx={{
                  position: 'absolute', inset: 0,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  bgcolor: 'rgba(0,191,165,0.7)',
                  opacity: 0,
                  transition: 'opacity 0.15s',
                  '.MuiBox-root:hover > &': { opacity: 1 },
                }}>
                  <ContentCopyIcon sx={{ fontSize: 18, color: '#000' }} />
                </Box>
              </Box>

              {/* Name */}
              <Box sx={{ px: 0.75, py: 0.5 }}>
                <Typography
                  sx={{
                    fontSize: '0.6rem',
                    lineHeight: 1.3,
                    color: 'text.secondary',
                    overflow: 'hidden',
                    display: '-webkit-box',
                    WebkitLineClamp: 2,
                    WebkitBoxOrient: 'vertical',
                  }}
                >
                  {label.name}
                </Typography>
              </Box>
            </Box>
          </Tooltip>
        ))}
      </Box>
    </Box>
  );
}

function clamp(v, lo, hi) {
  return Math.max(lo, Math.min(hi, v));
}
