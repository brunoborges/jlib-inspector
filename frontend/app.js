const express = require('express');
const cors = require('cors');
const axios = require('axios');
const WebSocket = require('ws');
const cron = require('node-cron');
const path = require('path');

const app = express();
const PORT = process.env.PORT || 3000;
let JLIB_SERVER_URL = process.env.JLIB_SERVER_URL || 'http://localhost:8080';

// Middleware
app.use(cors());
app.use(express.json());

// Disable caching for API routes
app.use('/api', (req, res, next) => {
  res.set({
    'Cache-Control': 'no-cache, no-store, must-revalidate',
    'Pragma': 'no-cache',
    'Expires': '0',
    'Last-Modified': new Date().toUTCString()
  });
  next();
});

// Serve static files - prioritize React build over public directory
app.use(express.static(path.join(__dirname, 'dist')));
app.use(express.static(path.join(__dirname, 'public')));

// In-memory storage for dashboard data
let dashboardData = {
  applications: [],
  lastUpdated: null,
  serverStatus: 'unknown'
};

// WebSocket server for real-time updates
const wss = new WebSocket.Server({ port: 3001 });

// Broadcast data to all connected WebSocket clients
function broadcastUpdate(data) {
  wss.clients.forEach(client => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(JSON.stringify(data));
    }
  });
}

// Fetch data from JLib Server
async function fetchApplicationData() {
  try {
    console.log('Fetching data from JLib Server...');
    
    // Check server health first
    const healthResponse = await axios.get(`${JLIB_SERVER_URL}/health`, { timeout: 5000 });
    dashboardData.serverStatus = 'connected';
    
    // Fetch applications
    const appsResponse = await axios.get(`${JLIB_SERVER_URL}/api/apps`, { timeout: 5000 });
    dashboardData.applications = appsResponse.data.applications || [];
    
    // Fetch detailed JAR info for each application
    for (let app of dashboardData.applications) {
      try {
        const jarsResponse = await axios.get(`${JLIB_SERVER_URL}/api/apps/${app.appId}/jars`, { timeout: 5000 });
        app.jars = jarsResponse.data.jars || [];
      } catch (error) {
        console.error(`Failed to fetch JARs for app ${app.appId}:`, error.message);
        app.jars = [];
      }
    }
    
    dashboardData.lastUpdated = new Date().toISOString();
    console.log(`Updated data for ${dashboardData.applications.length} applications`);
    
    // Broadcast update to connected clients
    broadcastUpdate({
      type: 'data-update',
      data: dashboardData
    });
    
  } catch (error) {
    console.error('Failed to fetch data from JLib Server:', error.message);
    dashboardData.serverStatus = 'disconnected';
    
    // Still broadcast the update to inform clients of the disconnection
    broadcastUpdate({
      type: 'data-update',
      data: dashboardData
    });
  }
}

// API Routes
app.get('/api/dashboard', (req, res) => {
  res.json(dashboardData);
});

app.get('/api/server-config', (req, res) => {
  res.json({
    jlibServerUrl: JLIB_SERVER_URL,
    lastUpdated: dashboardData.lastUpdated,
    serverStatus: dashboardData.serverStatus
  });
});

app.post('/api/server-config', (req, res) => {
  const { jlibServerUrl } = req.body;
  
  if (!jlibServerUrl) {
    return res.status(400).json({ error: 'jlibServerUrl is required' });
  }
  
  // Validate URL format
  try {
    new URL(jlibServerUrl);
  } catch (error) {
    return res.status(400).json({ error: 'Invalid URL format' });
  }
  
  const oldUrl = JLIB_SERVER_URL;
  JLIB_SERVER_URL = jlibServerUrl;
  
  console.log(`JLib Server URL changed from ${oldUrl} to ${JLIB_SERVER_URL}`);
  
  // Reset server status and trigger immediate data fetch
  dashboardData.serverStatus = 'connecting';
  dashboardData.applications = [];
  
  // Broadcast configuration change to all clients
  broadcastUpdate({
    type: 'config-update',
    data: {
      jlibServerUrl: JLIB_SERVER_URL,
      serverStatus: dashboardData.serverStatus
    }
  });
  
  // Trigger immediate data fetch with new URL
  setTimeout(fetchApplicationData, 100);
  
  res.json({
    success: true,
    jlibServerUrl: JLIB_SERVER_URL,
    message: 'JLib Server URL updated successfully'
  });
});

app.get('/api/applications', (req, res) => {
  res.json(dashboardData.applications);
});

app.get('/api/applications/:appId', (req, res) => {
  const app = dashboardData.applications.find(a => a.appId === req.params.appId);
  if (!app) {
    return res.status(404).json({ error: 'Application not found' });
  }
  res.json(app);
});

// Proxy: global JAR list (deduplicated) from JLib Server
app.get('/api/jars', async (req, res) => {
  try {
    const response = await axios.get(`${JLIB_SERVER_URL}/api/jars`, { timeout: 5000 });
    res.set({ 'Cache-Control': 'no-cache, no-store, must-revalidate' });
    res.json(response.data);
  } catch (error) {
    console.error('Failed to proxy /api/jars:', error.message);
    res.status(502).json({ error: 'Failed to retrieve jars list from JLib Server' });
  }
});

// Proxy: single JAR detail (includes manifest & application references)
app.get('/api/jars/:jarId', async (req, res) => {
  const { jarId } = req.params;
  try {
    const response = await axios.get(`${JLIB_SERVER_URL}/api/jars/${jarId}`, { timeout: 5000 });
    res.set({ 'Cache-Control': 'no-cache, no-store, must-revalidate' });
    res.json(response.data);
  } catch (error) {
    if (error.response && error.response.status === 404) {
      return res.status(404).json({ error: 'Jar not found' });
    }
    console.error(`Failed to proxy /api/jars/${jarId}:`, error.message);
    res.status(502).json({ error: 'Failed to retrieve jar detail from JLib Server' });
  }
});

// Update application metadata (name, description, tags)
app.put('/api/applications/:appId/metadata', async (req, res) => {
  const { appId } = req.params;
  const { name, description, tags } = req.body || {};
  try {
    const payload = { name, description, tags };
    const response = await axios.put(`${JLIB_SERVER_URL}/api/apps/${appId}/metadata`, payload, { timeout: 5000 });
    // Update local cache if app exists
    const idx = dashboardData.applications.findIndex(a => a.appId === appId);
    if (idx !== -1) {
      const app = dashboardData.applications[idx];
      app.name = name !== undefined ? name : app.name;
      app.description = description !== undefined ? description : app.description;
      app.tags = Array.isArray(tags) ? tags : (app.tags || []);
      dashboardData.lastUpdated = new Date().toISOString();
      broadcastUpdate({ type: 'data-update', data: dashboardData });
    }
    res.json(response.data);
  } catch (error) {
    console.error('Failed updating metadata:', error.message);
    res.status(500).json({ error: 'Failed to update metadata' });
  }
});

app.get('/api/health', (req, res) => {
  res.json({
    status: 'healthy',
    timestamp: new Date().toISOString(),
    jlibServerStatus: dashboardData.serverStatus,
    applicationsCount: dashboardData.applications.length
  });
});

// Serve the main application
app.get('/', (req, res) => {
  // Try to serve React build first, fallback to public/index.html
  const reactIndexPath = path.join(__dirname, 'dist', 'index.html');
  const publicIndexPath = path.join(__dirname, 'public', 'index.html');
  if (require('fs').existsSync(reactIndexPath)) {
    res.sendFile(reactIndexPath);
  } else {
    res.sendFile(publicIndexPath);
  }
});

// Catch-all middleware for client-side routing (Express 5.x compatible)
app.use((req, res, next) => {
  // Skip API routes
  if (req.path.startsWith('/api')) {
    return res.status(404).json({ error: 'API endpoint not found' });
  }
  // Try to serve React build first, fallback to public/index.html
  const reactIndexPath = path.join(__dirname, 'dist', 'index.html');
  const publicIndexPath = path.join(__dirname, 'public', 'index.html');
  if (require('fs').existsSync(reactIndexPath)) {
    res.sendFile(reactIndexPath);
  } else {
    res.sendFile(publicIndexPath);
  }
});

// WebSocket connection handling
wss.on('connection', (ws) => {
  console.log('Client connected to WebSocket');
  
  // Send current data to newly connected client
  ws.send(JSON.stringify({
    type: 'data-update',
    data: dashboardData
  }));
  
  ws.on('close', () => {
    console.log('Client disconnected from WebSocket');
  });
  
  ws.on('error', (error) => {
    console.error('WebSocket error:', error);
  });
});

// Initial data fetch with small delay to allow WebSocket connections
console.log('Performing initial data fetch...');
fetchApplicationData(); // Immediate fetch
setTimeout(fetchApplicationData, 500); // Wait 500ms for a second check

// Schedule periodic data fetching  
console.log('Setting up data fetching schedule...');
cron.schedule('*/10 * * * * *', fetchApplicationData); // Every 10 seconds

// Start server
app.listen(PORT, () => {
  console.log(`JLib Dashboard running on http://localhost:${PORT}`);
  console.log(`WebSocket server running on port 3001`);
  console.log(`Connecting to JLib Server at ${JLIB_SERVER_URL}`);
});

// Graceful shutdown
process.on('SIGINT', () => {
  console.log('\nShutting down gracefully...');
  wss.close();
  process.exit(0);
});
