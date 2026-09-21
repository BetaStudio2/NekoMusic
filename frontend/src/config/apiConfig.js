// API 基址：
//  - 生产：与前端同源（后端同时提供前端静态资源与 API）
//  - 开发：同源到 Vite dev server，由 vite.config 的 server.proxy 转发到后端（默认 :65535）
function isLocalHostname(hostname) {
  return (
    hostname === 'localhost' ||
    hostname === '127.0.0.1' ||
    hostname === '[::1]' ||
    hostname === '::1'
  );
}

function getApiBaseUrl() {
  if (typeof window === 'undefined') return '';

  // 开发联调：可在 .env.development.local 指定后端地址（例如线上站点取真实媒体）。
  // 仅本地生效，且优先于下面的同源推导；生产构建未设置该变量时行为不变。
  const override = import.meta.env.VITE_API_BASE;
  if (override) return String(override).replace(/\/+$/, '');

  // 开发环境统一走 dev server 同源，由 vite.config 的 server.proxy 转发到后端。
  // 直接指向其它端口会被浏览器当成跨域，带 Authorization 的请求会卡在预检。
  if (import.meta.env.DEV) {
    return window.location.origin;
  }

  const { protocol, hostname, port } = window.location;
  if (isLocalHostname(hostname) && port && port !== '80' && port !== '443') {
    return `${protocol}//${hostname}`;
  }
  return window.location.origin;
}

const API_CONFIG = {
  get BASE_URL() {
    return getApiBaseUrl();
  },
};

export default API_CONFIG;
