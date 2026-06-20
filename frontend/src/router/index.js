import { createRouter, createWebHistory } from 'vue-router'

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

        // 审核员不能访问音乐/歌词管理页面
        if (role === 'auditor' && (to.path.startsWith('/admin/music') || to.path.startsWith('/admin/lyrics'))) {
          next('/admin') // 重定向到管理首页
          return
        }
        if (role === 'auditor' && to.path.startsWith('/admin/vip-pricing')) {
          next('/admin')
          return
        }
        if (role === 'auditor' && to.path.startsWith('/admin/releases')) {
          next('/admin')
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
      component: () => import('@/views/HomeView.vue'),
      meta: {
        title: '首页 - Neko歌姬计划 | 完全免费的音乐平台',
        description:
          'Neko歌姬计划 - 完全免费的在线音乐播放平台；Android / PC 客户端支持从网易云迁入歌单（粘贴链接或歌单 ID，匹配站内曲库）。开源、搜索与播放免费使用。',
        keywords:
          'Neko歌姬计划,免费音乐,在线音乐,音乐播放,免费听歌,永久免费,无广告音乐,网易云歌单迁移,开源音乐'
      }
    },
    {
      path: '/about',
      name: 'about',
      component: () => import('@/views/AboutView.vue'),
      meta: {
        title: '关于我们 - Neko歌姬计划 | 免费音乐平台',
        description: '了解Neko歌姬计划项目，一个完全免费的在线音乐播放平台。无需付费，永久免费，享受高品质音乐体验。',
        keywords: '关于我们,Neko歌姬计划介绍,免费音乐平台,音乐项目'
      }
    },
    {
      path: '/privacy',
      name: 'privacy',
      component: () => import('@/views/PrivacyPolicyView.vue'),
      meta: {
        title: '隐私政策 - Neko歌姬计划',
        description: '了解 Neko歌姬计划如何收集、使用、保存和保护用户信息。',
        keywords: '隐私政策,个人信息保护,Neko歌姬计划'
      }
    },
    {
      path: '/download',
      name: 'download',
      component: () => import('@/views/DownloadView.vue'),
      meta: {
        title: '下载客户端 - Neko歌姬计划 | 免费音乐应用',
        description:
          '下载 Neko 云音乐 Android / PC 客户端；支持从网易云音乐迁入歌单（链接或 ID）。完全免费、开源透明，多端一致体验。',
        keywords: 'Neko歌姬计划下载,APP下载,免费音乐APP,移动音乐,PC下载,桌面音乐,网易云导入歌单'
      }
    },

    {
      path: '/upload',
      name: 'upload',
      component: () => import('@/views/UploadMusicView.vue'),
      meta: {
        title: '上传音乐 - Neko歌姬计划 | 免费音乐上传',
        description: '上传您的音乐到Neko歌姬计划平台，分享您的作品给更多人。完全免费，无需付费。',
        keywords: '音乐上传,上传歌曲,分享音乐,免费上传'
      }
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/views/SearchResultsView.vue'),
      props: true,
      meta: {
        title: '搜索音乐 - Neko歌姬计划 | 免费音乐搜索',
        description: '在Neko歌姬计划免费搜索您喜爱的音乐，发现更多精彩免费音乐内容。完全免费，无需付费。',
        keywords: '音乐搜索,免费音乐搜索,在线搜索,免费听歌'
      }
    },
    {
      path: '/search/:query',
      name: 'search-query',
      component: () => import('@/views/SearchResultsView.vue'),
      props: true,
      meta: {
        title: '搜索结果 - Neko歌姬计划 | 免费音乐',
        description: '查看免费音乐搜索结果，找到您想要的免费音乐。Neko歌姬计划提供完全免费的音乐播放服务。',
        keywords: '音乐搜索,搜索结果,免费音乐,免费听歌'
      }
    },
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/UserLoginView.vue'),
      meta: {
        title: '用户登录 - Neko歌姬计划 | 免费音乐平台',
        description: '登录您的Neko歌姬计划账户，享受完全免费的个性化音乐服务。无需付费，永久免费。',
        keywords: '用户登录,账户登录,免费音乐账户'
      }
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/UserRegisterView.vue'),
      meta: {
        title: '用户注册 - Neko歌姬计划 | 免费音乐平台',
        description: '注册Neko歌姬计划账户，开启您的免费音乐之旅。完全免费，无需付费，永久免费。',
        keywords: '用户注册,账户注册,免费音乐注册'
      }
    },
    {
      path: '/forgot-password',
      name: 'forgot-password',
      component: () => import('@/views/ForgotPasswordView.vue'),
      meta: {
        title: '忘记密码 - Neko歌姬计划 | 免费音乐平台',
        description: '忘记密码？通过邮箱验证码重置您的Neko歌姬计划账户密码。完全免费，无需付费。',
        keywords: '忘记密码,重置密码,找回密码'
      }
    },
    {
      path: '/favorites',
      name: 'favorites',
      component: () => import('@/views/UserFavoritesView.vue'),
      meta: {
        title: '我的收藏 - Neko歌姬计划 | 免费音乐收藏',
        description: '查看您在Neko歌姬计划收藏的免费音乐，管理您的个人音乐收藏夹。完全免费，无需付费。',
        keywords: '音乐收藏,我的收藏,免费收藏,个人音乐'
      }
    },
    {
      path: '/playlists',
      name: 'playlists',
      component: () => import('@/views/UserPlaylistsView.vue'),
      meta: {
        title: '我的歌单 - Neko歌姬计划 | 免费音乐歌单',
        description: '查看和管理您的歌单，创建个性化音乐播放列表。完全免费，无需付费。',
        keywords: '歌单,我的歌单,音乐歌单,播放列表'
      }
    },
    {
      path: '/account',
      name: 'account',
      component: () => import('@/views/UserProfileView.vue'),
      meta: {
        title: '个人中心 - Neko歌姬计划',
        description: '查看账户信息与会员状态，管理密码与安全设置。',
        keywords: '个人中心,账户,会员'
      }
    },
    {
      path: '/vip',
      name: 'vip',
      component: () => import('@/views/UserVipView.vue'),
      meta: {
        title: '会员中心 - Neko歌姬计划',
        description: '查看会员状态与到期时间。',
        keywords: '会员,VIP,会员中心'
      }
    },
    {
      path: '/ranking',
      name: 'ranking',
      component: () => import('@/views/RankingView.vue'),
      meta: {
        title: '热门音乐排行榜 - Neko歌姬计划 | 免费音乐排行',
        description: '查看基于播放次数排序的热门音乐排行榜，发现最受欢迎的免费音乐。完全免费，无需付费。',
        keywords: '热门音乐,音乐排行榜,免费音乐排行,热门排行'
      }
    },
    {
      path: '/latest',
      name: 'latest',
      component: () => import('@/views/LatestView.vue'),
      meta: {
        title: '最新音乐 - Neko歌姬计划 | 免费新歌',
        description: '查看刚刚上传的最新音乐，发现最新的免费音乐资源。完全免费，无需付费。',
        keywords: '最新音乐,新歌上线,免费新歌,音乐上新'
      }
    },
    {
      path: '/playlist/create',
      name: 'create-playlist',
      component: () => import('@/views/CreatePlaylistView.vue'),
      meta: {
        title: '创建歌单 - Neko歌姬计划 | 免费音乐歌单',
        description: '创建新的歌单，整理您喜爱的免费音乐。完全免费，无需付费。',
        keywords: '创建歌单,新建歌单,音乐歌单'
      }
    },
    {
      path: '/playlist/:id',
      name: 'playlist-detail',
      component: () => import('@/views/PlaylistDetailView.vue'),
      props: true,
      meta: {
        title: '歌单详情 - Neko歌姬计划 | 免费音乐歌单',
        description: '查看歌单详情，播放歌单中的免费音乐。完全免费，无需付费。',
        keywords: '歌单详情,歌单播放,音乐歌单'
      }
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/AdminLoginView.vue'),
      meta: {
        title: '管理员登录 - Neko歌姬计划',
        description: '管理员登录页面，管理免费音乐平台内容。',
        keywords: '管理员登录,后台管理'
      }
    },
    {
      path: '/admin',
      name: 'admin',
      component: () => import('@/views/admin/AdminView.vue'),
      beforeEnter: adminGuard,
      meta: {
        title: '管理后台 - Neko歌姬计划',
        description: '管理后台首页，管理免费音乐平台各项功能。',
        keywords: '管理后台,后台管理'
      }
    },
    {
      path: '/admin/music',
      name: 'admin-music',
      component: () => import('@/views/admin/AdminMusicView.vue'),
      beforeEnter: adminGuard,
      meta: {
        title: '音乐管理 - Neko歌姬计划',
        description: '管理平台免费音乐资源，上传、编辑、删除免费音乐。',
        keywords: '音乐管理,音乐上传,免费音乐管理'
      }
    },
    {
      path: '/admin/lyrics',
      name: 'admin-lyrics',
      component: () => import('@/views/admin/AdminLyricsEditorView.vue'),
      beforeEnter: adminGuard,
      meta: {
        title: '歌词编辑 - Neko歌姬计划',
        description: '以文件管理器方式在线编辑平台歌词文件。',
        keywords: '歌词编辑,在线编辑,LRC,后台管理'
      }
    },
    {
      path: '/admin/audit',
      name: 'admin-audit',
      component: () => import('@/views/admin/AdminAuditView.vue'),
      beforeEnter: adminGuard,
      meta: {
        title: '审核管理 - Neko歌姬计划',
        description: '审核用户上传的音乐，管理待审核的免费音乐内容。',
        keywords: '审核管理,音乐审核,待审核,免费音乐审核'
      }
    },
    {
      path: '/admin/users',
      name: 'admin-users',
      component: () => import('@/views/admin/AdminUsersView.vue'),
      beforeEnter: adminGuard,
      meta: {
        title: '用户管理 - Neko歌姬计划',
        description: '管理平台免费音乐用户，查看用户信息和统计数据。',
        keywords: '用户管理,用户统计,免费音乐用户'
      }
    },
    {
      path: '/admin/vip-pricing',
      name: 'admin-vip-pricing',
      component: () => import('@/views/admin/AdminVipPricingView.vue'),
      beforeEnter: adminGuard,
      meta: {
        title: 'VIP 价目 - Neko歌姬计划',
        description: '管理 VIP 套餐价目表。',
        keywords: 'VIP,价目,管理'
      }
    },
    {
      path: '/admin/releases',
      name: 'admin-releases',
      component: () => import('@/views/admin/AdminReleasesView.vue'),
      beforeEnter: adminGuard,
      meta: {
        title: '客户端更新 - Neko歌姬计划',
        description: '管理客户端版本号与安装包上传。',
        keywords: '客户端,更新,安装包,管理'
      }
    },
    {
      path: '/detail/:id',
      name: 'detail',
      component: () => import('@/views/PlayerView.vue'),
      props: true,
      meta: {
        title: '音乐详情 - Neko歌姬计划 | 免费音乐播放',
        description: '查看免费音乐详细信息，免费播放高品质音乐。Neko歌姬计划提供完全免费的音乐播放服务。',
        keywords: '音乐详情,音乐播放,免费音乐播放,免费听歌'
      }
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/ErrorView.vue'),
      meta: {
        title: '404 - 页面未找到 - Neko歌姬计划',
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
      to.path !== '/privacy' &&
      !to.path.startsWith('/detail/') &&
      !to.path.startsWith('/playlist/') &&
      !to.path.startsWith('/account') &&
      !to.path.startsWith('/vip') &&
      !to.path.startsWith('/admin')) {
    next('/download')
    return
  }

  // 设置页面标题
  document.title = to.meta.title || 'Neko歌姬计划 - 完全免费的在线音乐播放平台'

  // 设置页面描述
  const description = to.meta.description || 'Neko歌姬计划 - 完全免费的在线音乐播放平台，提供海量免费音乐资源、高品质音频播放、个性化收藏等功能。无需付费，永久免费。'
  let descriptionMeta = document.querySelector('meta[name="description"]')
  if (!descriptionMeta) {
    descriptionMeta = document.createElement('meta')
    descriptionMeta.name = 'description'
    document.head.appendChild(descriptionMeta)
  }
  descriptionMeta.content = description

  // 设置页面关键词
  const keywords = to.meta.keywords || 'Neko歌姬计划,免费音乐,在线音乐,音乐播放,音乐搜索,音乐收藏,免费听歌,高品质音乐,无广告音乐,永久免费'
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
