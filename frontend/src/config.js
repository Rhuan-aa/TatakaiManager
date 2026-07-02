// Configuração modular de URLs (API e WebSocket).
//
// Ordem de resolução:
//   1. window.__ENV__  -> injetado em runtime (env.js gerado pelo container no deploy)
//   2. import.meta.env -> variáveis VITE_* embutidas no build (dev / build local)
//   3. fallback local
//
// Isso permite trocar os endpoints no deploy sem rebuildar a imagem.

const runtime = (typeof window !== 'undefined' && window.__ENV__) || {};

export const API_URL =
  runtime.API_URL || import.meta.env.VITE_API_URL || 'http://localhost:8080';

export const WS_URL =
  runtime.WS_URL || import.meta.env.VITE_WS_URL || 'http://localhost:8080/ws';
