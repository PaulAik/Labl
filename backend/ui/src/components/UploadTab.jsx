import { useState, useRef, useCallback, useEffect } from 'react';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import MenuItem from '@mui/material/MenuItem';
import Select from '@mui/material/Select';
import FormControl from '@mui/material/FormControl';
import InputLabel from '@mui/material/InputLabel';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Alert from '@mui/material/Alert';
import LinearProgress from '@mui/material/LinearProgress';
import Divider from '@mui/material/Divider';
import CloudUploadIcon from '@mui/icons-material/CloudUpload';
import { classifyPreview, saveLabels, validateLabel, updateLabel, fetchLabels } from '../api.js';
import BoundingBoxEditor from './BoundingBoxEditor.jsx';
import LabelCard from './LabelCard.jsx';

const APPLIANCE_TYPES = [
  { value: '',           label: 'Unknown / auto-detect' },
  { value: 'washing',    label: 'Washing machine' },
  { value: 'dryer',      label: 'Dryer' },
  { value: 'dishwasher', label: 'Dishwasher' },
  { value: 'oven',       label: 'Oven' },
  { value: 'iron',       label: 'Iron' },
  { value: 'other',      label: 'Other' },
];

// phases: idle → scanning → editing → saving → done | error
export default function UploadTab() {
  const [file, setFile]               = useState(null);
  const [preview, setPreview]         = useState(null);
  const [dragging, setDragging]       = useState(false);
  const [applianceType, setAppliance] = useState('');
  const [phase, setPhase]             = useState('idle');
  const [error, setError]             = useState('');

  // data from classifyPreview
  const [sessionId, setSessionId]     = useState('');
  const [s3Key, setS3Key]             = useState('');
  const [symbols, setSymbols]         = useState([]);

  // saved labels shown after final save
  const [savedIds, setSavedIds]       = useState([]);

  const fileRef  = useRef();
  const abortRef = useRef();

  function pickFile(f) {
    if (!f || !f.type.startsWith('image/')) return;
    setFile(f);
    setPreview(URL.createObjectURL(f));
    setPhase('idle');
    setError('');
    setSymbols([]);
    setSavedIds([]);
  }

  const onDrop = useCallback(e => {
    e.preventDefault();
    setDragging(false);
    pickFile(e.dataTransfer.files[0]);
  }, []);

  async function scan() {
    if (!file) return;
    abortRef.current?.abort();
    const ac = new AbortController();
    abortRef.current = ac;

    setError('');
    setPhase('scanning');

    try {
      const result = await classifyPreview(file, applianceType);
      setSessionId(result.session_id);
      setS3Key(result.s3_key);
      setSymbols(result.symbols ?? []);
      setPhase('editing');
    } catch (err) {
      if (err.name === 'AbortError') return;
      setError(err.message);
      setPhase('error');
    }
  }

  async function save() {
    setError('');
    setPhase('saving');
    try {
      const result = await saveLabels({
        sessionId,
        applianceType,
        s3Key,
        symbols,
      });
      setSavedIds(result.ids ?? []);
      setPhase('done');
    } catch (err) {
      setError(err.message);
      setPhase('error');
    }
  }

  function reset() {
    abortRef.current?.abort();
    setFile(null);
    setPreview(null);
    setPhase('idle');
    setError('');
    setSymbols([]);
    setSavedIds([]);
  }

  const busy = phase === 'scanning' || phase === 'saving';

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>

      {/* Drop zone — only shown when not in the editing/done phase */}
      {phase !== 'editing' && phase !== 'saving' && phase !== 'done' && (
        <>
          <Paper
            elevation={0}
            onClick={() => !file && fileRef.current.click()}
            onDragOver={e => { e.preventDefault(); setDragging(true); }}
            onDragLeave={() => setDragging(false)}
            onDrop={onDrop}
            sx={{
              border: '2px dashed',
              borderColor: dragging ? 'primary.main' : 'rgba(255,255,255,0.15)',
              borderRadius: 3,
              bgcolor: dragging ? 'rgba(0,191,165,0.06)' : 'background.paper',
              cursor: file ? 'default' : 'pointer',
              transition: 'border-color 0.15s, background-color 0.15s',
              overflow: 'hidden',
              minHeight: 200,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {preview ? (
              <Box sx={{ position: 'relative', width: '100%' }}>
                <img
                  src={preview}
                  alt="Preview"
                  style={{ width: '100%', maxHeight: 340, objectFit: 'contain', display: 'block' }}
                />
              </Box>
            ) : (
              <Stack alignItems="center" gap={1} sx={{ py: 5, px: 3, userSelect: 'none' }}>
                <CloudUploadIcon sx={{ fontSize: 48, color: 'primary.main', opacity: 0.7 }} />
                <Typography variant="subtitle1" fontWeight={600}>
                  Drop an image here or click to select
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Appliance control panel, laundry care label, etc.
                </Typography>
              </Stack>
            )}
          </Paper>
          <input ref={fileRef} type="file" accept="image/*" style={{ display: 'none' }}
            onChange={e => pickFile(e.target.files[0])} />

          {/* Controls */}
          <Stack direction="row" gap={1.5} alignItems="center" sx={{ mt: 2 }}>
            <FormControl size="small" sx={{ flex: 1 }}>
              <InputLabel>Appliance type</InputLabel>
              <Select
                value={applianceType}
                label="Appliance type"
                onChange={e => setAppliance(e.target.value)}
                disabled={busy}
              >
                {APPLIANCE_TYPES.map(o => (
                  <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>
                ))}
              </Select>
            </FormControl>

            {file && (
              <Button variant="text" color="inherit" size="small" onClick={reset} disabled={busy}
                sx={{ opacity: 0.55, whiteSpace: 'nowrap' }}>
                Change image
              </Button>
            )}

            <Button
              variant="contained"
              color="primary"
              onClick={scan}
              disabled={!file || busy}
              sx={{ whiteSpace: 'nowrap', fontWeight: 600 }}
            >
              {busy ? 'Scanning…' : 'Scan'}
            </Button>
          </Stack>

          {busy && <LinearProgress color="primary" sx={{ mt: 2, borderRadius: 1 }} />}

          {phase === 'error' && (
            <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>
          )}
        </>
      )}

      {/* Editing phase — bounding box editor */}
      {(phase === 'editing' || phase === 'saving') && (
        <>
          <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 2 }}>
            <Box>
              <Typography variant="h6" fontWeight={700}>Adjust Bounding Boxes</Typography>
              <Typography variant="body2" color="text.secondary">
                Drag boxes to reposition · drag corners to resize · delete unwanted symbols · add missing ones
              </Typography>
            </Box>
            <Stack direction="row" gap={1}>
              <Button variant="text" color="inherit" size="small" onClick={reset}
                sx={{ opacity: 0.55 }} disabled={phase === 'saving'}>
                Start over
              </Button>
              <Button
                variant="contained"
                color="primary"
                onClick={save}
                disabled={symbols.length === 0 || phase === 'saving'}
                sx={{ fontWeight: 600 }}
              >
                {phase === 'saving' ? 'Saving…' : `Save ${symbols.length} symbol${symbols.length !== 1 ? 's' : ''}`}
              </Button>
            </Stack>
          </Stack>

          {phase === 'saving' && <LinearProgress color="primary" sx={{ mb: 2, borderRadius: 1 }} />}

          <BoundingBoxEditor
            imageUrl={preview}
            symbols={symbols}
            onChange={setSymbols}
            applianceType={applianceType}
          />

          {phase === 'error' && (
            <Alert severity="error" sx={{ mt: 2 }}>{error}</Alert>
          )}
        </>
      )}

      {/* Done phase — show saved labels for validation */}
      {phase === 'done' && (
        <>
          <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
            <Typography variant="h6" fontWeight={700}>Saved — Validate Below</Typography>
            <Button variant="outlined" color="primary" size="small" onClick={reset}>
              Upload another
            </Button>
          </Stack>

          <Divider sx={{ mb: 3 }}>
            <Typography variant="caption" color="text.secondary" sx={{ letterSpacing: '0.1em' }}>
              {savedIds.length} SYMBOL{savedIds.length !== 1 ? 'S' : ''} SAVED — VALIDATE BELOW
            </Typography>
          </Divider>

          {/* Re-fetch saved labels so we can show them in LabelCards */}
          <SavedLabelsList ids={savedIds} onValidate={validateLabel} onUpdate={updateLabel} />
        </>
      )}
    </Container>
  );
}

/** Loads the just-saved labels by id so we can validate them. */
function SavedLabelsList({ ids, onValidate, onUpdate }) {
  const [labels, setLabels] = useState(null);
  const [err, setErr] = useState('');

  useEffect(() => {
    fetchLabels('pending')
      .then(all => setLabels(all.filter(l => ids.includes(l.id))))
      .catch(e => setErr(e.message));
  }, [ids]);

  if (err) return <Alert severity="error">{err}</Alert>;
  if (!labels) return <LinearProgress color="primary" sx={{ mt: 2 }} />;
  if (labels.length === 0) return (
    <Typography color="text.secondary" textAlign="center" sx={{ py: 3 }}>
      Labels saved. Head to the Review Queue to validate them.
    </Typography>
  );

  return (
    <Stack gap={1.5}>
      {labels.map(label => (
        <LabelCard
          key={label.id}
          label={label}
          initialStatus="pending"
          onValidate={onValidate}
          onUpdate={onUpdate}
        />
      ))}
    </Stack>
  );
}
