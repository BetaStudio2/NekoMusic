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
