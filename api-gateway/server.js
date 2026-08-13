require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { createProxyMiddleware } = require('http-proxy-middleware');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(
  cors({
    origin: process.env.FRONTEND_URL || 'http://localhost:5173',
    credentials: true,
  })
);

// Route to GenAI Python Service
app.use(
  '/api/genai',
  createProxyMiddleware({
    target: process.env.FASTAPI_URL || 'http://localhost:8083',
    changeOrigin: true,
    pathRewrite: { '^/api/genai': '' },
  })
);

// Route to Spring Boot Monolith
app.use(
  '/api',
  createProxyMiddleware({
    target: process.env.SPRING_BOOT_URL || 'http://localhost:8081',
    changeOrigin: true,
    // PRESERVE /api prefix when sending to Spring Boot
    pathRewrite: (path, req) => '/api' + path,
  })
);





app.listen(PORT, () => console.log(`Gateway running on port ${PORT}`));

