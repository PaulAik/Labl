import { useState } from 'react';
import { createTheme, ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import Tabs from '@mui/material/Tabs';
import Tab from '@mui/material/Tab';
import Box from '@mui/material/Box';
import LocalLaundryServiceIcon from '@mui/icons-material/LocalLaundryService';
import Badge from '@mui/material/Badge';
import { useLabelsCount } from './hooks/useLabelsCount.js';
import UploadTab from './components/UploadTab.jsx';
import ValidateTab from './components/ValidateTab.jsx';

const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#00BFA5' },
    secondary: { main: '#FF4B4B' },
    background: { default: '#0A0A0A', paper: '#161616' },
  },
  typography: {
    fontFamily: '"Inter", -apple-system, BlinkMacSystemFont, sans-serif',
  },
  components: {
    MuiPaper: {
      styleOverrides: {
        root: { backgroundImage: 'none' },
      },
    },
    MuiAppBar: {
      styleOverrides: {
        root: { backgroundImage: 'none', backgroundColor: '#111111' },
      },
    },
  },
});

export default function App() {
  const [tab, setTab] = useState(0);
  const pendingCount = useLabelsCount();

  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />

      <AppBar position="static" elevation={0} sx={{ borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
        <Toolbar>
          <LocalLaundryServiceIcon sx={{ mr: 1.5, color: 'primary.main' }} />
          <Typography
            variant="h6"
            sx={{ flexGrow: 1, fontWeight: 700, letterSpacing: '0.12em', color: 'primary.main' }}
          >
            LABL
          </Typography>
        </Toolbar>
        <Tabs
          value={tab}
          onChange={(_, v) => setTab(v)}
          textColor="primary"
          indicatorColor="primary"
          sx={{ px: 2 }}
        >
          <Tab label="Upload & Classify" />
          <Tab
            label={
              <Badge badgeContent={pendingCount || null} color="primary" sx={{ pr: pendingCount ? 1.5 : 0 }}>
                Review Queue
              </Badge>
            }
          />
        </Tabs>
      </AppBar>

      <Box sx={{ minHeight: 'calc(100vh - 112px)', bgcolor: 'background.default' }}>
        {tab === 0 && <UploadTab />}
        {tab === 1 && <ValidateTab />}
      </Box>
    </ThemeProvider>
  );
}
