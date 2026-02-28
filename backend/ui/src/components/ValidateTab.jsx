import { useState, useEffect, useCallback } from 'react';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Typography from '@mui/material/Typography';
import Stack from '@mui/material/Stack';
import Button from '@mui/material/Button';
import ButtonGroup from '@mui/material/ButtonGroup';
import IconButton from '@mui/material/IconButton';
import CircularProgress from '@mui/material/CircularProgress';
import Alert from '@mui/material/Alert';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Tooltip from '@mui/material/Tooltip';
import RefreshIcon from '@mui/icons-material/Refresh';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import { fetchLabels, validateLabel, updateLabel } from '../api.js';
import LabelCard from './LabelCard.jsx';

const FILTERS = ['all', 'washing', 'drying', 'ironing', 'bleaching', 'dishwasher', 'oven', 'other'];

export default function ValidateTab() {
  const [labels, setLabels]       = useState([]);
  const [loading, setLoading]     = useState(true);
  const [error, setError]         = useState('');
  const [filter, setFilter]       = useState('all');
  const [lastRefresh, setRefresh] = useState(Date.now());

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const data = await fetchLabels('pending');
      setLabels(data);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load, lastRefresh]);

  async function handleValidate(id, approved) {
    await validateLabel(id, approved);
    // Remove from local list after a brief delay so the user sees the confirmed state
    setTimeout(() => {
      setLabels(prev => prev.filter(l => l.id !== id));
    }, 800);
  }

  const visible = filter === 'all'
    ? labels
    : labels.filter(l => l.category?.toLowerCase() === filter);

  return (
    <Container maxWidth="md" sx={{ py: 4 }}>

      {/* Header */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h6" fontWeight={700}>
            Review Queue
          </Typography>
          {!loading && (
            <Typography variant="body2" color="text.secondary">
              {labels.length === 0
                ? 'No pending labels'
                : `${labels.length} label${labels.length !== 1 ? 's' : ''} awaiting review`}
            </Typography>
          )}
        </Box>
        <Tooltip title="Refresh">
          <IconButton onClick={() => setRefresh(Date.now())} disabled={loading} color="primary">
            <RefreshIcon />
          </IconButton>
        </Tooltip>
      </Stack>

      {/* Category filter */}
      {labels.length > 0 && (
        <Stack direction="row" gap={1} flexWrap="wrap" sx={{ mb: 3 }}>
          {FILTERS.map(f => {
            const count = f === 'all'
              ? labels.length
              : labels.filter(l => l.category?.toLowerCase() === f).length;
            if (f !== 'all' && count === 0) return null;
            return (
              <Chip
                key={f}
                label={`${f === 'all' ? 'All' : f.charAt(0).toUpperCase() + f.slice(1)} ${count}`}
                onClick={() => setFilter(f)}
                variant={filter === f ? 'filled' : 'outlined'}
                color={filter === f ? 'primary' : 'default'}
                size="small"
                sx={{ textTransform: 'capitalize', fontWeight: filter === f ? 700 : 400 }}
              />
            );
          })}
        </Stack>
      )}

      <Divider sx={{ mb: 3 }} />

      {/* States */}
      {loading && (
        <Stack alignItems="center" sx={{ py: 6 }} gap={2}>
          <CircularProgress color="primary" />
          <Typography color="text.secondary">Loading labels…</Typography>
        </Stack>
      )}

      {!loading && error && (
        <Alert severity="error" action={
          <Button color="inherit" size="small" onClick={() => setRefresh(Date.now())}>Retry</Button>
        }>
          {error}
        </Alert>
      )}

      {!loading && !error && labels.length === 0 && (
        <Stack alignItems="center" sx={{ py: 8 }} gap={2}>
          <CheckCircleOutlineIcon sx={{ fontSize: 56, color: 'primary.main', opacity: 0.6 }} />
          <Typography variant="h6" fontWeight={600}>All done!</Typography>
          <Typography color="text.secondary" textAlign="center">
            No labels pending review. Collect more training data or wait for new uploads to be classified.
          </Typography>
          <Button variant="outlined" color="primary" startIcon={<RefreshIcon />}
            onClick={() => setRefresh(Date.now())}>
            Refresh
          </Button>
        </Stack>
      )}

      {!loading && !error && visible.length === 0 && labels.length > 0 && (
        <Typography color="text.secondary" textAlign="center" sx={{ py: 4 }}>
          No pending labels in this category.
        </Typography>
      )}

      {!loading && !error && visible.length > 0 && (
        <>
          {/* Bulk actions */}
          <Stack direction="row" justifyContent="flex-end" sx={{ mb: 2 }}>
            <ButtonGroup size="small" variant="outlined">
              <Button
                color="primary"
                onClick={() => visible.forEach(l => handleValidate(l.id, true))}
              >
                Approve all visible
              </Button>
              <Button
                color="secondary"
                onClick={() => visible.forEach(l => handleValidate(l.id, false))}
              >
                Reject all
              </Button>
            </ButtonGroup>
          </Stack>

          <Stack gap={1.5}>
            {visible.map(label => (
              <LabelCard
                key={label.id}
                label={label}
                initialStatus="pending"
                onValidate={handleValidate}
                onUpdate={updateLabel}
              />
            ))}
          </Stack>
        </>
      )}
    </Container>
  );
}
