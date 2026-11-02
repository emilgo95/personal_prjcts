import { Fragment, useState, type ReactNode } from 'react';
import { Link as RouterLink, useLocation, useNavigate } from 'react-router-dom';
import {
  AppBar,
  Box,
  Divider,
  Drawer,
  IconButton,
  List,
  ListItemButton,
  ListItemIcon,
  ListItemText,
  Toolbar,
  Tooltip,
  Typography,
} from '@mui/material';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import MenuIcon from '@mui/icons-material/Menu';
import KeyIcon from '@mui/icons-material/Key';
import LogoutIcon from '@mui/icons-material/Logout';
import DashboardIcon from '@mui/icons-material/SpaceDashboard';
import AgricultureIcon from '@mui/icons-material/Agriculture';
import FactCheckIcon from '@mui/icons-material/FactCheck';
import DescriptionIcon from '@mui/icons-material/Description';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import GavelIcon from '@mui/icons-material/Gavel';
import HistoryIcon from '@mui/icons-material/History';
import GroupIcon from '@mui/icons-material/Group';
import PrivacyTipIcon from '@mui/icons-material/PrivacyTip';
import FolderSpecialIcon from '@mui/icons-material/FolderSpecial';
import CompareArrowsIcon from '@mui/icons-material/CompareArrows';
import AssignmentLateIcon from '@mui/icons-material/AssignmentLate';
import { useAuth } from '../auth/AuthContext';
import { label, roleLabels } from '../utils/labels';

const drawerWidth = 248;
/** Ancho del menú contraído: solo iconos. */
const collapsedWidth = 72;
const COLLAPSED_KEY = 'agriaudit.navCollapsed';

/** Preferencia del menú contraído, guardada en este navegador (si el almacenamiento no está disponible, abierto). */
function readCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSED_KEY) === 'true';
  } catch {
    return false;
  }
}

function saveCollapsed(value: boolean) {
  try {
    localStorage.setItem(COLLAPSED_KEY, String(value));
  } catch {
    // Sin almacenamiento (modo privado o bloqueado): la preferencia dura solo esta visita.
  }
}

interface NavItem {
  to: string;
  label: string;
  icon: ReactNode;
  adminOnly?: boolean;
  /** Si se indica, solo estos roles ven la entrada. */
  roles?: Array<'ADMIN' | 'AUDITOR' | 'QUALITY' | 'VIEWER'>;
  /** Entradas que cuelgan de esta (se muestran debajo, con sangría). */
  children?: Array<{ to: string; label: string; icon: ReactNode }>;
}

const navItems: NavItem[] = [
  { to: '/', label: 'Panel', icon: <DashboardIcon /> },
  { to: '/farms', label: 'Explotaciones', icon: <AgricultureIcon /> },
  {
    to: '/audits',
    label: 'Auditorías',
    icon: <FactCheckIcon />,
    children: [
      { to: '/audits/findings', label: 'No conformidades', icon: <AssignmentLateIcon fontSize="small" /> },
      { to: '/audits/evidence', label: 'Evidencias', icon: <FolderSpecialIcon fontSize="small" /> },
      { to: '/audits/equivalences', label: 'Equivalencias entre normas', icon: <CompareArrowsIcon fontSize="small" /> },
    ],
  },
  { to: '/documents', label: 'Centro de documentos', icon: <DescriptionIcon /> },
  { to: '/standards', label: 'Estándares', icon: <MenuBookIcon /> },
  { to: '/regulations', label: 'Normativa', icon: <GavelIcon /> },
  { to: '/trace', label: 'Trazabilidad', icon: <HistoryIcon />, roles: ['ADMIN', 'QUALITY'] },
  { to: '/users', label: 'Usuarios', icon: <GroupIcon />, adminOnly: true },
  { to: '/privacy', label: 'Privacidad', icon: <PrivacyTipIcon />, adminOnly: true },
];

export default function Layout({ children }: { children: ReactNode }) {
  const [mobileOpen, setMobileOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const location = useLocation();
  const navigate = useNavigate();
  const { user, logout, hasRole } = useAuth();
  const width = collapsed ? collapsedWidth : drawerWidth;

  const toggleCollapsed = () => {
    setCollapsed((c) => {
      saveCollapsed(!c);
      return !c;
    });
  };

  /** Contenido del menú; contraído, solo iconos con el nombre en una etiqueta flotante. */
  const renderDrawer = (compact: boolean) => (
    <div>
      <Toolbar sx={{ justifyContent: compact ? 'center' : 'space-between', px: compact ? 1 : 2 }}>
        {!compact && (
          <Typography variant="h6" color="primary" sx={{ fontWeight: 800 }}>
            AgriAudit Docs
          </Typography>
        )}
        <IconButton
          onClick={toggleCollapsed}
          aria-label={compact ? 'Expandir el menú' : 'Contraer el menú'}
          aria-expanded={!compact}
          title={compact ? 'Expandir el menú' : 'Contraer el menú'}
          size="small"
          sx={{ display: { xs: 'none', sm: 'inline-flex' } }}
        >
          {compact ? <ChevronRightIcon /> : <ChevronLeftIcon />}
        </IconButton>
      </Toolbar>
      <Divider />
      <List component="div">
        {navItems
          .filter((item) => !item.adminOnly || hasRole('ADMIN'))
          .filter((item) => !item.roles || hasRole(...item.roles))
          .map((item) => {
            const inChild = item.children?.some((c) => location.pathname.startsWith(c.to)) ?? false;
            const selected =
              item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to) && !inChild;
            return (
              <Fragment key={item.to}>
                <Tooltip title={compact ? item.label : ''} placement="right">
                  <ListItemButton
                    component={RouterLink}
                    to={item.to}
                    selected={selected}
                    onClick={() => setMobileOpen(false)}
                    aria-label={compact ? item.label : undefined}
                    sx={compact ? { justifyContent: 'center', px: 1 } : undefined}
                  >
                    <ListItemIcon sx={compact ? { minWidth: 0 } : undefined}>{item.icon}</ListItemIcon>
                    {!compact && <ListItemText primary={item.label} />}
                  </ListItemButton>
                </Tooltip>
                {item.children?.map((child) => (
                  <Tooltip key={child.to} title={compact ? child.label : ''} placement="right">
                    <ListItemButton
                      component={RouterLink}
                      to={child.to}
                      selected={location.pathname.startsWith(child.to)}
                      onClick={() => setMobileOpen(false)}
                      aria-label={compact ? child.label : undefined}
                      sx={compact ? { justifyContent: 'center', px: 1, py: 0.5 } : { pl: 4.5, py: 0.5 }}
                    >
                      <ListItemIcon sx={{ minWidth: compact ? 0 : 36 }}>{child.icon}</ListItemIcon>
                      {!compact && (
                        <ListItemText primary={child.label} slotProps={{ primary: { variant: 'body2' } }} />
                      )}
                    </ListItemButton>
                  </Tooltip>
                ))}
              </Fragment>
            );
          })}
      </List>
    </div>
  );

  return (
    <Box sx={{ display: 'flex' }}>
      <AppBar
        position="fixed"
        sx={{
          width: { sm: `calc(100% - ${width}px)` },
          ml: { sm: `${width}px` },
          transition: (t) => t.transitions.create(['width', 'margin'], { duration: t.transitions.duration.shorter }),
        }}
      >
        <Toolbar>
          <IconButton
            color="inherit"
            edge="start"
            onClick={() => setMobileOpen(!mobileOpen)}
            aria-label="Abrir el menú"
            sx={{ mr: 2, display: { sm: 'none' } }}
          >
            <MenuIcon />
          </IconButton>
          <Box sx={{ flexGrow: 1 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700, lineHeight: 1.1 }}>
              {user?.organizationName}
            </Typography>
            <Typography variant="caption">
              {user?.fullName || user?.email} · {label(roleLabels, user?.role)}
            </Typography>
          </Box>
          <IconButton
            color="inherit"
            onClick={() => navigate('/account/password')}
            title="Cambiar contraseña"
            aria-label="Cambiar contraseña"
          >
            <KeyIcon />
          </IconButton>
          <IconButton
            color="inherit"
            onClick={() => {
              logout();
              navigate('/login');
            }}
            title="Cerrar sesión"
            aria-label="Cerrar sesión"
          >
            <LogoutIcon />
          </IconButton>
        </Toolbar>
      </AppBar>

      <Box component="nav" aria-label="Menú principal" sx={{ width: { sm: width }, flexShrink: { sm: 0 } }}>
        <Drawer
          variant="temporary"
          open={mobileOpen}
          onClose={() => setMobileOpen(false)}
          ModalProps={{ keepMounted: true }}
          sx={{
            display: { xs: 'block', sm: 'none' },
            '& .MuiDrawer-paper': { boxSizing: 'border-box', width: drawerWidth },
          }}
        >
          {renderDrawer(false)}
        </Drawer>
        <Drawer
          variant="permanent"
          open
          sx={{
            display: { xs: 'none', sm: 'block' },
            '& .MuiDrawer-paper': {
              boxSizing: 'border-box',
              width,
              overflowX: 'hidden',
              transition: (t) => t.transitions.create('width', { duration: t.transitions.duration.shorter }),
            },
          }}
        >
          {renderDrawer(collapsed)}
        </Drawer>
      </Box>

      <Box
        component="main"
        sx={{
          flexGrow: 1,
          minWidth: 0, // sin esto, una tabla ancha estira la página en lugar de desplazarse dentro de su caja
          p: { xs: 2, md: 3 },
          width: { sm: `calc(100% - ${width}px)` },
        }}
      >
        <Toolbar />
        {children}
      </Box>
    </Box>
  );
}
