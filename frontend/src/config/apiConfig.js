// API 基址：与前端同源；本地开发时去掉 Vite 等非默认端口，请求落到 http(s)://主机 的 80/443
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
