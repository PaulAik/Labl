import { useState } from 'react';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardMedia from '@mui/material/CardMedia';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import IconButton from '@mui/material/IconButton';
import CircularProgress from '@mui/material/CircularProgress';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbDownIcon from '@mui/icons-material/ThumbDown';
import EditIcon from '@mui/icons-material/Edit';
import CheckIcon from '@mui/icons-material/Check';
import CloseIcon from '@mui/icons-material/Close';
import { imageUrl } from '../api.js';

const CATEGORY_COLORS = {
  washing:    '#4D9EFF',
  drying:     '#FF9A3C',
  ironing:    '#FF4B4B',
  bleaching:  '#FFD700',
  dishwasher: '#00BCD4',
  oven:       '#AB47BC',
};

const CONFIDENCE_COLORS = {
  high:   '#00BFA5',
  medium: '#FFD700',
  low:    '#FF9A3C',
};

/**
 * Reusable symbol label card.
 *
 * Props:
 *   label         — SymbolLabel object from the backend
 *   onValidate    — async (id, approved) => void   (omit to hide action buttons)
 *   onUpdate      — async (id, name, description) => void  (omit to hide edit button)
 *   initialStatus — 'pending' | 'approved' | 'rejected' (overrides label.validated)
 */
export default function LabelCard({ label, onValidate, onUpdate, initialStatus }) {
  const startStatus = initialStatus ?? statusFromValidated(label.validated);
  const [status, setStatus]     = useState(startStatus);
  const [loading, setLoading]   = useState(false);
  const [imgError, setImgError] = useState(false);

  // edit state
  const [editing, setEditing]   = useState(false);
  const [editName, setEditName] = useState(label.name ?? '');
  const [editDesc, setEditDesc] = useState(label.description ?? '');
  const [saving, setSaving]     = useState(false);

  // displayed values (updated optimistically after save)
  const [name, setName]         = useState(label.name ?? '');
  const [desc, setDesc]         = useState(label.description ?? '');

  const cropKey  = label.crop_s3_key || label.s3_key;
  const catColor = CATEGORY_COLORS[label.category?.toLowerCase()] ?? '#9E9E9E';
  const confColor = CONFIDENCE_COLORS[label.confidence?.toLowerCase()] ?? '#9E9E9E';

  async function handleValidate(approved) {
    if (!onValidate) return;
    setLoading(true);
    try {
      await onValidate(label.id, approved);
      setStatus(approved ? 'approved' : 'rejected');
    } finally {
      setLoading(false);
    }
  }

  function startEdit() {
    setEditName(name);
    setEditDesc(desc);
    setEditing(true);
  }

  function cancelEdit() {
    setEditing(false);
  }

  async function commitEdit() {
    if (!onUpdate) return;
    setSaving(true);
    try {
      await onUpdate(label.id, editName, editDesc);
      setName(editName);
      setDesc(editDesc);
      setEditing(false);
    } finally {
      setSaving(false);
    }
  }

  const canEdit = !!onUpdate && status === 'pending';

  return (
    <Card
      elevation={0}
      sx={{
        display: 'flex',
        gap: 0,
        bgcolor: 'background.paper',
        border: '1px solid',
        borderColor: status === 'approved'
          ? 'rgba(0,191,165,0.35)'
          : status === 'rejected'
          ? 'rgba(255,75,75,0.35)'
          : 'rgba(255,255,255,0.07)',
        borderRadius: 3,
        overflow: 'hidden',
        transition: 'border-color 0.2s',
      }}
    >
      {/* Thumbnail */}
      <Box
        sx={{
          width: 100,
          flexShrink: 0,
          bgcolor: 'rgba(0,0,0,0.4)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {cropKey && !imgError ? (
          <CardMedia
            component="img"
            image={imageUrl(cropKey)}
            alt={name}
            onError={() => setImgError(true)}
            sx={{ width: '100%', height: '100%', objectFit: 'contain' }}
          />
        ) : (
          <Typography sx={{ fontSize: '2rem', userSelect: 'none' }}>🏷️</Typography>
        )}
      </Box>

      {/* Content */}
      <CardContent sx={{ flex: 1, py: 1.5, '&:last-child': { pb: 1.5 } }}>
        <Stack direction="row" alignItems="flex-start" justifyContent="space-between" gap={1}>
          <Box sx={{ flex: 1, minWidth: 0 }}>

            {editing ? (
              /* ── edit mode ── */
              <Stack gap={1} sx={{ pr: 1 }}>
                <TextField
                  size="small"
                  label="Name"
                  value={editName}
                  onChange={e => setEditName(e.target.value)}
                  fullWidth
                  autoFocus
                  disabled={saving}
                />
                <TextField
                  size="small"
                  label="Description"
                  value={editDesc}
                  onChange={e => setEditDesc(e.target.value)}
                  fullWidth
                  multiline
                  minRows={2}
                  disabled={saving}
                />
                <Stack direction="row" gap={1}>
                  <Button
                    size="small"
                    variant="contained"
                    color="primary"
                    startIcon={saving ? <CircularProgress size={14} color="inherit" /> : <CheckIcon />}
                    onClick={commitEdit}
                    disabled={saving || !editName.trim()}
                    sx={{ fontSize: '0.75rem' }}
                  >
                    Save
                  </Button>
                  <Button
                    size="small"
                    variant="text"
                    color="inherit"
                    startIcon={<CloseIcon />}
                    onClick={cancelEdit}
                    disabled={saving}
                    sx={{ fontSize: '0.75rem', opacity: 0.6 }}
                  >
                    Cancel
                  </Button>
                </Stack>
              </Stack>
            ) : (
              /* ── display mode ── */
              <>
                <Stack direction="row" alignItems="center" gap={0.5} sx={{ mb: 0.5 }}>
                  <Typography variant="subtitle1" fontWeight={600} noWrap sx={{ flex: 1, minWidth: 0 }}>
                    {name || 'Unknown symbol'}
                  </Typography>
                  {canEdit && (
                    <IconButton size="small" onClick={startEdit} sx={{ opacity: 0.45, '&:hover': { opacity: 1 } }}>
                      <EditIcon sx={{ fontSize: 15 }} />
                    </IconButton>
                  )}
                </Stack>

                <Stack direction="row" gap={0.75} flexWrap="wrap" sx={{ mb: 1 }}>
                  <Chip
                    label={label.category?.toUpperCase().replace(/_/g, ' ') ?? '—'}
                    size="small"
                    sx={{
                      height: 20,
                      fontSize: '0.65rem',
                      fontWeight: 700,
                      letterSpacing: '0.05em',
                      bgcolor: `${catColor}22`,
                      color: catColor,
                      border: `1px solid ${catColor}55`,
                    }}
                  />
                  <Chip
                    label={`${label.confidence ?? '?'} confidence`}
                    size="small"
                    sx={{
                      height: 20,
                      fontSize: '0.65rem',
                      fontWeight: 600,
                      bgcolor: `${confColor}22`,
                      color: confColor,
                      border: `1px solid ${confColor}44`,
                    }}
                  />
                </Stack>

                <Typography
                  variant="body2"
                  sx={{ color: 'rgba(255,255,255,0.65)', lineHeight: 1.55, fontSize: '0.82rem' }}
                >
                  {desc || 'No description.'}
                </Typography>
              </>
            )}
          </Box>

          {/* Action area — hidden while editing */}
          {!editing && (
            <Box sx={{ flexShrink: 0, display: 'flex', alignItems: 'center', pl: 1 }}>
              {loading ? (
                <CircularProgress size={24} color="primary" />
              ) : status === 'approved' ? (
                <Stack direction="row" alignItems="center" gap={0.5}>
                  <CheckCircleIcon sx={{ color: 'primary.main', fontSize: 18 }} />
                  <Typography variant="caption" sx={{ color: 'primary.main', fontWeight: 600 }}>
                    Approved
                  </Typography>
                </Stack>
              ) : status === 'rejected' ? (
                <Stack direction="row" alignItems="center" gap={0.5}>
                  <CancelIcon sx={{ color: 'secondary.main', fontSize: 18 }} />
                  <Typography variant="caption" sx={{ color: 'secondary.main', fontWeight: 600 }}>
                    Rejected
                  </Typography>
                </Stack>
              ) : onValidate ? (
                <Stack direction="column" gap={0.75}>
                  <Button
                    size="small"
                    variant="outlined"
                    color="primary"
                    startIcon={<ThumbUpIcon />}
                    onClick={() => handleValidate(true)}
                    sx={{ minWidth: 100, fontSize: '0.75rem', py: 0.5 }}
                  >
                    Correct
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    color="secondary"
                    startIcon={<ThumbDownIcon />}
                    onClick={() => handleValidate(false)}
                    sx={{ minWidth: 100, fontSize: '0.75rem', py: 0.5 }}
                  >
                    Wrong
                  </Button>
                </Stack>
              ) : null}
            </Box>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

function statusFromValidated(v) {
  if (v === 1)  return 'approved';
  if (v === -1) return 'rejected';
  return 'pending';
}
