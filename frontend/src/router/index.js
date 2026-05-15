import { createRouter, createWebHistory } from 'vue-router'
import HomeView from '@/views/HomeView.vue'
import AboutView from '@/views/AboutView.vue'
import SearchResultsView from '@/views/SearchResultsView.vue'
import PlayerView from '@/views/PlayerView.vue'
import UserLoginView from '@/views/UserLoginView.vue'
import UserRegisterView from '@/views/UserRegisterView.vue'
import ForgotPasswordView from '@/views/ForgotPasswordView.vue'
import UserFavoritesView from '@/views/UserFavoritesView.vue'
import UserPlaylistsView from '@/views/UserPlaylistsView.vue'
import CreatePlaylistView from '@/views/CreatePlaylistView.vue'
import PlaylistDetailView from '@/views/PlaylistDetailView.vue'
import RankingView from '@/views/RankingView.vue'
import LatestView from '@/views/LatestView.vue'
import AdminLoginView from '@/views/admin/AdminLoginView.vue'
import AdminView from '@/views/admin/AdminView.vue'
import AdminMusicView from '@/views/admin/AdminMusicView.vue'
import AdminAuditView from '@/views/admin/AdminAuditView.vue'
import AdminUsersView from '@/views/admin/AdminUsersView.vue'
import AdminSettingsView from '@/views/admin/AdminSettingsView.vue'
import DownloadView from '@/views/DownloadView.vue'

import ErrorView from '@/views/ErrorView.vue'
import UploadMusicView from '@/views/UploadMusicView.vue'
import UserProfileView from '@/views/UserProfileView.vue'

// 检查是否是移动设备
function isMobileDevice() {
  const userAgent = navigator.userAgent || navigator.vendor || window.opera
  return /android|ipad|iphone|ipod/i.test(userAgent)
}

// 检查管理员是否已登录
function isAdminLoggedIn() {
  return localStorage.getItem('isAdminLoggedIn') === 'true';
}

// 管理员路由守卫
const adminGuard = (to, from, next) => {
  if (to.path.startsWith('/admin') && to.path !== '/admin/login') {
    if (isAdminLoggedIn()) {
      // 检查用户权限
      const adminInfo = localStorage.getItem('adminInfo')
      if (adminInfo) {
        const parsedInfo = JSON.parse(adminInfo)
        const role = parsedInfo.role || 'admin'
        
        // 审核员不能访问音乐管理页面
        if (role === 'auditor' && to.path.startsWith('/admin/music')) {
          next('/admin') // 重定向到管理首页
          return
        }
      }
      next(); // 如果已登录且有权限，允许访问
    } else {
      next('/admin/login'); // 如果未登录，重定向到登录页面
    }
  } else {
    next(); // 其他路由正常访问
  }
};

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
      meta: {
        title: '首页 - Neko云音乐 | 完全免费的音乐平台',
        description: 'Neko云音乐 - 完全免费的在线音乐播放平台，提供海量免费音乐资源，无需付费，永久免费。发现海量音乐资源，享受高品质音乐体验。',
        keywords: 'Neko云音乐,免费音乐,在线音乐,音乐播放,免费听歌,永久免费,无广告音乐'
      }
    },
    {
      path: '/about',
      name: 'about',
      component: AboutView,
      meta: {
        title: '关于我们 - Neko云音乐 | 免费音乐平台',
        description: '了解Neko云音乐项目，一个完全免费的在线音乐播放平台。无需付费，永久免费，享受高品质音乐体验。',
        keywords: '关于我们,Neko云音乐介绍,免费音乐平台,音乐项目'
      }
    },
    {
      path: '/download',
      name: 'download',
      component: DownloadView,
      meta: {
        title: '下载客户端 - Neko云音乐 | 免费音乐应用',
        description: '下载Neko云音乐客户端，享受完全免费的音乐体验。支持移动端和PC端。无需付费，永久免费。',
        keywords: 'Neko云音乐下载,APP下载,免费音乐APP,移动音乐,PC下载,桌面音乐'
      }
    },
    {
      path: '/dev-docs',
      name: 'dev-docs',
      redirect: 'https://github.com/NyaNyagulugulu/NekoMusicDocs',
      meta: {
        title: '开发者文档 - Neko云音乐 | API接口文档',
        description: 'Neko云音乐开发者文档，提供完整的API接口文档和使用说明。包括用户认证、音乐搜索、收藏等功能。',
        keywords: 'Neko云音乐API,开发者文档,API文档,接口文档,RESTful API'
      }
    },
    {
      path: '/upload',
      name: 'upload',
      component: UploadMusicView,
      meta: {
        title: '上传音乐 - Neko云音乐 | 免费音乐上传',
        description: '上传您的音乐到Neko云音乐平台，分享您的作品给更多人。完全免费，无需付费。',
        keywords: '音乐上传,上传歌曲,分享音乐,免费上传'
      }
    },
    {
      path: '/search',
      name: 'search',
      component: SearchResultsView,
      props: true,
      meta: {
        title: '搜索音乐 - Neko云音乐 | 免费音乐搜索',
        description: '在Neko云音乐免费搜索您喜爱的音乐，发现更多精彩免费音乐内容。完全免费，无需付费。',
        keywords: '音乐搜索,免费音乐搜索,在线搜索,免费听歌'
      }
    },
    {
      path: '/search/:query',
      name: 'search-query',
      component: SearchResultsView,
      props: true,
      meta: {
        title: '搜索结果 - Neko云音乐 | 免费音乐',
        description: '查看免费音乐搜索结果，找到您想要的免费音乐。Neko云音乐提供完全免费的音乐播放服务。',
        keywords: '音乐搜索,搜索结果,免费音乐,免费听歌'
      }
    },
    {
      path: '/login',
      name: 'login',
      component: UserLoginView,
      meta: {
        title: '用户登录 - Neko云音乐 | 免费音乐平台',
        description: '登录您的Neko云音乐账户，享受完全免费的个性化音乐服务。无需付费，永久免费。',
        keywords: '用户登录,账户登录,免费音乐账户'
      }
    },
    {
      path: '/register',
      name: 'register',
      component: UserRegisterView,
      meta: {
        title: '用户注册 - Neko云音乐 | 免费音乐平台',
        description: '注册Neko云音乐账户，开启您的免费音乐之旅。完全免费，无需付费，永久免费。',
        keywords: '用户注册,账户注册,免费音乐注册'
      }
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: ForgotPasswordView,
      meta: {
        title: '忘记密码 - Neko云音乐 | 免费音乐平台',
        description: '忘记密码？通过邮箱验证码重置您的Neko云音乐账户密码。完全免费，无需付费。',
        keywords: '忘记密码,重置密码,找回密码'
      }
    },
    {
      path: '/favorites',
      name: 'favorites',
      component: UserFavoritesView,
      meta: {
        title: '我的收藏 - Neko云音乐 | 免费音乐收藏',
        description: '查看您在Neko云音乐收藏的免费音乐，管理您的个人音乐收藏夹。完全免费，无需付费。',
        keywords: '音乐收藏,我的收藏,免费收藏,个人音乐'
      }
    },
    {
      path: '/playlists',
      name: 'playlists',
      component: UserPlaylistsView,
      meta: {
        title: '我的歌单 - Neko云音乐 | 免费音乐歌单',
        description: '查看和管理您的歌单，创建个性化音乐播放列表。完全免费，无需付费。',
        keywords: '歌单,我的歌单,音乐歌单,播放列表'
      }
    },
    {
      path: '/account',
      name: 'account',
      component: UserProfileView,
      meta: {
        title: '个人中心 - Neko云音乐',
        description: '查看账户信息与会员状态，管理密码与安全设置。',
        keywords: '个人中心,账户,会员'
      }
    },
    {
      path: '/ranking',
      name: 'ranking',
      component: RankingView,
      meta: {
        title: '热门音乐排行榜 - Neko云音乐 | 免费音乐排行',
        description: '查看基于播放次数排序的热门音乐排行榜，发现最受欢迎的免费音乐。完全免费，无需付费。',
        keywords: '热门音乐,音乐排行榜,免费音乐排行,热门排行'
      }
    },
    {
      path: '/latest',
      name: 'latest',
      component: LatestView,
      meta: {
        title: '最新音乐 - Neko云音乐 | 免费新歌',
        description: '查看刚刚上传的最新音乐，发现最新的免费音乐资源。完全免费，无需付费。',
        keywords: '最新音乐,新歌上线,免费新歌,音乐上新'
      }
    },
    {
      path: '/playlist/create',
      name: 'create-playlist',
      component: CreatePlaylistView,
      meta: {
        title: '创建歌单 - Neko云音乐 | 免费音乐歌单',
        description: '创建新的歌单，整理您喜爱的免费音乐。完全免费，无需付费。',
        keywords: '创建歌单,新建歌单,音乐歌单'
      }
    },
    {
      path: '/playlist/:id',
      name: 'playlist-detail',
      component: PlaylistDetailView,
      props: true,
      meta: {
        title: '歌单详情 - Neko云音乐 | 免费音乐歌单',
        description: '查看歌单详情，播放歌单中的免费音乐。完全免费，无需付费。',
        keywords: '歌单详情,歌单播放,音乐歌单'
      }
    },
    {
      path: '/favorites',
      name: 'favorites',
      component: UserFavoritesView,
      meta: {
        title: '我的收藏 - Neko云音乐 | 免费音乐收藏',
        description: '查看和管理您收藏的免费音乐，随时播放喜爱的免费歌曲。Neko云音乐提供完全免费的收藏功能。',
        keywords: '音乐收藏,我的收藏,免费音乐收藏'
      }
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: AdminLoginView,
      meta: {
        title: '管理员登录 - Neko云音乐',
        description: '管理员登录页面，管理免费音乐平台内容。',
        keywords: '管理员登录,后台管理'
      }
    },
    {
      path: '/admin',
      name: 'admin',
      component: AdminView,
      beforeEnter: adminGuard,
      meta: {
        title: '管理后台 - Neko云音乐',
        description: '管理后台首页，管理免费音乐平台各项功能。',
        keywords: '管理后台,后台管理'
      }
    },
    {
      path: '/admin/music',
      name: 'admin-music',
      component: AdminMusicView,
      beforeEnter: adminGuard,
      meta: {
        title: '音乐管理 - Neko云音乐',
        description: '管理平台免费音乐资源，上传、编辑、删除免费音乐。',
        keywords: '音乐管理,音乐上传,免费音乐管理'
      }
    },
    {
      path: '/admin/audit',
      name: 'admin-audit',
      component: AdminAuditView,
      beforeEnter: adminGuard,
      meta: {
        title: '审核管理 - Neko云音乐',
        description: '审核用户上传的音乐，管理待审核的免费音乐内容。',
        keywords: '审核管理,音乐审核,待审核,免费音乐审核'
      }
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: AdminUsersView,
      beforeEnter: adminGuard,
      meta: {
        title: '用户管理 - Neko云音乐',
        description: '管理平台免费音乐用户，查看用户信息和统计数据。',
        keywords: '用户管理,用户统计,免费音乐用户'
      }
    },
    {
      path: '/detail/:id',
      name: 'detail',
      component: PlayerView,
      props: true,
      meta: {
        title: '音乐详情 - Neko云音乐 | 免费音乐播放',
        description: '查看免费音乐详细信息，免费播放高品质音乐。Neko云音乐提供完全免费的音乐播放服务。',
        keywords: '音乐详情,音乐播放,免费音乐播放,免费听歌'
      }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: ErrorView,
      meta: {
        title: '404 - 页面未找到 - Neko云音乐',
        description: '您访问的页面不存在或已被移除。返回首页继续探索免费音乐。',
        keywords: '404,页面未找到,错误页面'
      }
    }
  ]
})

// 全局路由守卫 - 更新页面标题和元数据 + 移动设备检测
router.beforeEach((to, from, next) => {
  // 如果是移动设备访问非下载页面、非播放页面、非歌单详情页面、非管理员页面，重定向到下载页面
  if (isMobileDevice() && 
      to.path !== '/download' && 
      !to.path.startsWith('/detail/') && 
      !to.path.startsWith('/playlist/') &&
      !to.path.startsWith('/account') &&
      !to.path.startsWith('/admin')) {
    next('/download')
    return
  }

  // 设置页面标题
  document.title = to.meta.title || 'Neko云音乐 - 完全免费的在线音乐播放平台'

  // 设置页面描述
  const description = to.meta.description || 'Neko云音乐 - 完全免费的在线音乐播放平台，提供海量免费音乐资源、高品质音频播放、个性化收藏等功能。无需付费，永久免费。'
  let descriptionMeta = document.querySelector('meta[name="description"]')
  if (!descriptionMeta) {
    descriptionMeta = document.createElement('meta')
    descriptionMeta.name = 'description'
    document.head.appendChild(descriptionMeta)
  }
  descriptionMeta.content = description

  // 设置页面关键词
  const keywords = to.meta.keywords || 'Neko云音乐,免费音乐,在线音乐,音乐播放,音乐搜索,音乐收藏,免费听歌,高品质音乐,无广告音乐,永久免费'
  let keywordsMeta = document.querySelector('meta[name="keywords"]')
  if (!keywordsMeta) {
    keywordsMeta = document.createElement('meta')
    keywordsMeta.name = 'keywords'
    document.head.appendChild(keywordsMeta)
  }
  keywordsMeta.content = keywords

  next()
})

export default router