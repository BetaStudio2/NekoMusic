// API 基址：与前端同源（由当前访问的协议 + 主机 + 端口决定），部署时需将 /api 反代到后端
const API_CONFIG = {
  get BASE_URL() {
    return typeof window !== 'undefined' ? window.location.origin : '';
  },
};

export default API_CONFIG;
