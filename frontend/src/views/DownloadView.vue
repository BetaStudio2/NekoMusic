<template>
  <div class="download-page">
    <AmbientBackdrop />

    <PageShell width="wide">
      <header class="topbar">
        <NButton variant="ghost" size="sm" icon="arrow-left" to="/">返回 Web 播放器</NButton>
        <NTag variant="outline" size="sm">下载客户端</NTag>
      </header>
      <section class="hero" aria-labelledby="download-title">
        <div class="hero__copy">
          <p class="hero__eyebrow">Neko 云音乐</p>
          <h1 id="download-title" class="hero__title">把播放器装进口袋与桌面</h1>
          <p class="hero__lede">
            同一套免费体验：搜索、播放、收藏与歌单。<strong class="hero__lede-strong">Android / PC 支持从网易云、QQ 音乐和酷狗迁入歌单</strong>（链接或歌单 ID，自动匹配站内曲库）。选择你的平台，一键获取安装包。
          </p>
          <ul class="hero__facts">
            <li>完全免费</li>
            <li>开源透明</li>
            <li>网易、QQ、酷狗歌单可迁入</li>
          </ul>
          <p class="hero__anchor-hint">
            <a href="#netease-migrate" class="hero__anchor-link">查看迁入步骤与说明</a>
          </p>
        </div>
        <div class="hero__art">
          <div class="hero__frame">
            <img src="/favicon.ico" alt="" class="hero__logo" height="276" width="256" />
          </div>
          <p class="hero__art-caption">Android · Windows · Linux · macOS</p>
        </div>
      </section>

      <section id="netease-migrate" class="netease-panel" aria-labelledby="netease-migrate-title" tabindex="-1">
        <div class="netease-panel__inner">
          <header class="netease-panel__head">
            <p class="netease-panel__eyebrow">换播放器不用从零攒歌单</p>
            <h2 id="netease-migrate-title" class="netease-panel__title">从网易云 / QQ 音乐 / 酷狗迁入歌单</h2>
            <p class="netease-panel__lede">
              在 <strong>Android</strong> 或 <strong>桌面客户端</strong> 内使用「导入外部歌单」：粘贴网易云 / QQ 音乐 / 酷狗的歌单分享链接或歌单 ID，客户端会拉取曲目列表，并在 Neko 曲库中按歌名与歌手匹配后，导入到你指定的歌单。
            </p>
            <p class="netease-panel__note">
              本页 Web 播放器暂不支持该流程；迁入后能否全部播放入库，取决于站内是否已有对应上传资源以及曲库。开源客户端行为可自查源码，无「背地里同步你网易账号密码」那一套。
            </p>
          </header>
          <ol class="netease-panel__steps">
            <li><span class="netease-panel__step-num">1</span> 在网易云 / QQ 音乐 / 酷狗复制歌单链接，或记下歌单 ID。</li>
            <li><span class="netease-panel__step-num">2</span> 安装并打开本页下方提供的 Android / Windows / Linux / macOS 客户端。</li>
            <li><span class="netease-panel__step-num">3</span> 在客户端内找到「导入外部歌单」，粘贴链接或 ID，选择目标歌单并开始匹配导入。</li>
          </ol>
        </div>
      </section>

      <div v-if="loading" class="state state--loading">
        <div class="state__spinner" aria-hidden="true" />
        <p class="state__text">正在拉取最新版本信息…</p>
      </div>

      <div v-else-if="error" class="state state--error" role="alert">
        <svg class="state__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
          <circle cx="12" cy="12" r="10" stroke-width="2" />
          <path d="M12 8v4M12 16h.01" stroke-width="2" stroke-linecap="round" />
        </svg>
        <p class="state__text">{{ error }}</p>
      </div>

      <template v-else>
        <section class="android" aria-labelledby="android-heading">
          <div class="android__inner">
            <div class="android__icon-wrap" aria-hidden="true">
              <svg class="android__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.75">
                <path d="M17 2H7C5.34 2 4 3.34 4 5v14c0 1.66 1.34 3 3 3h10c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3Z" stroke-linejoin="round" />
                <path d="M12 18h.01" stroke-linecap="round" />
              </svg>
            </div>
            <div class="android__main">
              <div class="android__head">
                <h2 id="android-heading" class="android__title">Android</h2>
                <p class="android__sub">手机与平板 · APK 直链</p>
              </div>
              <dl class="android__meta">
                <div class="android__meta-row">
                  <dt>当前版本</dt>
                  <dd>{{ versionInfo.ver || '-' }}</dd>
                </div>
              </dl>
            </div>
            <a :href="androidDownloadUrl" class="android__cta" download>
              <span class="android__cta-label">
                <svg class="android__cta-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" aria-hidden="true">
                  <path d="M12 16V4m0 12l-4-4m4 4l4-4M4 20h16" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
                下载 APK
              </span>
            </a>
          </div>
        </section>

        <section class="desktop" aria-labelledby="desktop-heading">
          <div class="desktop__intro">
            <h2 id="desktop-heading" class="desktop__title">桌面客户端</h2>
            <p class="desktop__sub">离线能力更强 · 本机音频体验</p>
            <div class="desktop__version">
              <span class="desktop__version-label">最新版本</span>
              <span class="desktop__version-value">v{{ versionInfo.pc?.pc_ver || versionInfo.ver }}</span>
            </div>
          </div>

          <div class="desktop__grid">

            <div class="plat plat--linux">
              <div class="plat__icon plat__icon--linux" aria-hidden="true">
                <svg class="plat__svg" viewBox="0 0 1024 1024" fill="currentColor" aria-hidden="true" xmlns="http://www.w3.org/2000/svg">
                  <path fill="currentColor" d="M452 234.857143q-6.285714 0.571429-8.857143 6t-4.857143 5.428571q-2.857143 0.571429-2.857143-2.857143 0-6.857143 10.857143-8.571429l5.714286 0zm49.714286 8q-2.285714 0.571429-6.571429-3.714286t-10-2.571429q13.714286-6.285714 18.285714 1.142857 1.714286 3.428571-1.714286 5.142857zm-200.571429 244q-2.285714-0.571429-3.428571 1.714286t-2.571429 7.142857-3.142857 7.714286-5.714286 7.428571q-4 5.714286-0.571429 6.857143 2.285714 0.571429 7.142857-4t7.142857-10.285714q0.571429-1.714286 1.142857-4t1.142857-3.428571 0.857143-2.571429 2.857143-2.285714l0-1.714286-0.571429-1.428571-1.714286-1.142857zm488.571429 205.142857q0-10.285714-31.428571-24 2.285714-8.571429 4.285714-15.714286t2.857143-14.857143 1.714286-12.285714 2.857143-12.857143-0.571429-11.142857-2-12.571429-2.285714-11.714286-2.857143-14.285714-3.142857-15.142857q-5.714286-27.428571-26.857143-58.857143t-41.142857-42.857143q13.714286 11.428571 32.571429 47.428571 49.714286 92.571429 30.857143 158.857143-6.285714 22.857143-28.571429 24-17.714286 2.285714-22-10.571429t-4.571429-47.714286-6.571429-61.142857q-5.142857-22.285714-11.142857-39.428571t-11.142857-26-8.857143-14-7.428571-8.571429-4.285714-4q-8-35.428571-17.714286-58.857143t-16.857143-32-13.428571-18.857143-8.571429-22.857143q-2.285714-12 3.428571-30.571429t2.571429-28.285714-25.428571-14.285714q-8.571429-1.714286-25.428571-10.285714t-20.285714-9.142857q-4.571429-0.571429-6.285714-14.857143t4.571429-29.142857 20.571429-15.428571q21.142857-1.714286 29.142857 17.142857t2.285714 33.142857q-6.285714 10.857143-1.142857 15.142857t17.142857 2.857143q7.428571-2.285714 7.428571-20.571429l0-21.142857q-2.857143-17.142857-7.714286-28.571429t-12-17.428571-13.428571-8.571429-15.428571-4.285714q-61.142857 4.571429-50.857143 76.571429 0 8.571429-0.571429 8.571429-5.142857-5.142857-16.857143-6t-18.857143 2.857143-8.857143-2.857143q0.571429-32.571429-9.142857-51.428571t-25.714286-19.428571q-15.428571-0.571429-23.714286 15.714286t-9.428571 34q-0.571429 8.571429 2 21.142857t7.428571 21.428571 8.857143 7.714286q5.714286-1.714286 9.142857-8 2.285714-5.142857-4-4.571429-4 0-8.857143-8.285714t-5.428571-19.142857q-0.571429-12.571429 5.142857-21.142857t19.428571-8q9.714286 0 15.428571 12t5.428571 22.285714-0.857143 12.571429q-12.571429 8.571429-17.714286 16.571429-4.571429 6.857143-15.714286 13.428571t-11.714286 7.142857q-7.428571 8-8.857143 15.428571t4.285714 10.285714q8 4.571429 14.285714 11.142857t9.142857 10.857143 10.571429 7.428571 20.285714 3.714286q26.857143 1.142857 58.285714-8.571429 1.142857-0.571429 13.142857-4t19.714286-6 16.857143-7.428571 12-10q5.142857-8 11.428571-4.571429 2.857143 1.714286 3.714286 4.857143t-1.714286 6.857143-9.428571 5.428571q-11.428571 3.428571-32.285714 12.285714t-26 11.142857q-25.142857 10.857143-40 13.142857-14.285714 2.857143-45.142857-1.142857-5.714286-1.142857-5.142857 1.142857t9.714286 10.857143q14.285714 13.142857 38.285714 12.571429 9.714286-0.571429 20.571429-4t20.571429-8 19.142857-10 17.142857-9.714286 14-6.857143 10-1.428571 4.857143 6.285714q0 1.142857-0.571429 2.571429t-2.285714 2.857143-3.428571 2.571429-4.857143 2.857143-5.142857 2.571429-5.714286 2.857143-5.428571 2.571429q-16 8-38.571429 25.142857t-38 24.571429-28 0.571429q-12-6.285714-36-41.714286-12.571429-17.714286-14.285714-12.571429-0.571429 1.714286-0.571429 5.714286 0 14.285714-8.571429 32.285714t-16.857143 31.714286-12 33.142857 6.571429 36q-13.142857 3.428571-35.714286 51.428571t-27.142857 80.571429q-1.142857 10.285714-0.857143 39.428571t-3.142857 33.714286q-4.571429 13.714286-16.571429 1.714286-18.285714-17.714286-20.571429-53.714286-1.142857-16 2.285714-32 2.285714-10.857143-0.571429-10.285714l-2.285714 2.857143q-20.571429 37.142857 5.714286 94.857143 2.857143 6.857143 14.285714 16t13.714286 11.428571q11.428571 13.142857 59.428571 51.714286t53.142857 43.714286q9.142857 8.571429 10 21.714286t-8 24.571429-26 13.142857q4.571429 8.571429 16.571429 25.428571t16 30.857143 4 40.285714q26.285714-13.714286 4-52.571429-2.285714-4.571429-6-9.142857t-5.428571-6.857143-1.142857-3.428571q1.714286-2.857143 7.428571-5.428571t11.428571 1.428571q26.285714 29.714286 94.857143 20.571429 76-8.571429 101.142857-49.714286 13.142857-21.714286 19.428571-17.142857 6.857143 3.428571 5.714286 29.714286-0.571429 14.285714-13.142857 52.571429-5.142857 13.142857-3.428571 21.428571t13.714286 8.857143q1.714286-10.857143 8.285714-44t7.714286-51.428571q1.142857-12-3.714286-42t-4.285714-55.428571 13.142857-40.285714q8.571429-10.285714 29.142857-10.285714 0.571429-21.142857 19.714286-30.285714t41.428571-6 34.285714 12.857143zm-358.857143-472.571429q1.714286-9.714286-1.428571-17.142857t-6.571429-8.571429q-5.142857-1.142857-5.142857 4 1.142857 2.857143 2.857143 3.428571 5.714286 0 4 8.571429-1.714286 11.428571 4.571429 11.428571 1.714286 0 1.714286-1.714286zm239.428571 112.571429q-1.142857-4.571429-3.714286-6.571429t-7.428571-2.857143-8.285714-3.142857q-2.857143-1.714286-5.428571-4.571429t-4-4.571429-3.142857-3.714286-2.285714-2.285714-2.285714 0.857143q-8 9.142857 4 24.857143t22.285714 18q5.142857 0.571429 8.285714-4.571429t2-11.428571zm-101.714286-121.714286q0-6.285714-2.857143-11.142857t-6.285714-7.142857-5.142857-1.714286q-8 0.571429-4 4l2.285714 1.142857q8 2.285714 10.285714 17.714286 0 1.714286 4.571429-1.142857zm30.857143-133.142857q0-1.142857-1.428571-2.857143t-5.142857-4-5.428571-3.428571q-8.571429-8.571429-13.714286-8.571429-5.142857 0.571429-6.571429 4.285714t-0.571429 7.428571 2.857143 7.142857q-0.571429 2.285714-3.428571 6t-3.428571 5.142857 1.714286 4.857143q2.285714 1.714286 4.571429 0t6.285714-5.142857 8.571429-5.142857q0.571429-0.571429 5.142857-0.571429t8.571429-1.142857 5.142857-4zm322.857143 766.285714q11.428571 6.857143 17.714286 14t6.857143 13.714286-1.428571 12.857143-8.857143 12.571429-13.428571 11.142857-17.142857 10.571429-18 9.428571-18.285714 8.857143-15.428571 7.428571q-21.714286 10.857143-48.857143 32t-43.142857 36.571429q-9.714286 9.142857-38.857143 11.142857t-50.857143-8.285714q-10.285714-5.142857-16.857143-13.428571t-9.428571-14.571429-12.571429-11.142857-26.857143-5.428571q-25.142857-0.571429-74.285714-0.571429-10.857143 0-32.571429 0.857143t-33.142857 1.428571q-25.142857 0.571429-45.428571 8.571429t-30.571429 17.142857-24.857143 16.285714-30.571429 6.571429q-16.571429-0.571429-63.428571-17.714286t-83.428571-24.571429q-10.857143-2.285714-29.142857-5.428571t-28.571429-5.142857-22.571429-5.428571-19.142857-8.285714-9.714286-11.142857q-5.714286-13.142857 4-38t10.285714-31.142857q0.571429-9.142857-2.285714-22.857143t-5.714286-24.285714-2.571429-20.857143 6-15.428571q8-6.857143 32.571429-8t34.285714-6.857143q17.142857-10.285714 24-20t6.857143-29.142857q12 41.714286-18.285714 60.571429-18.285714 11.428571-47.428571 8.571429-19.428571-1.714286-24.571429 5.714286-7.428571 8.571429 2.857143 32.571429 1.142857 3.428571 4.571429 10.285714t4.857143 10.285714 2.571429 9.714286 0.571429 12.571429q0 8.571429-9.714286 28t-8 27.428571q1.714286 9.714286 21.142857 14.857143 11.428571 3.428571 48.285714 10.571429t56.857143 11.714286q13.714286 3.428571 42.285714 12.571429t47.142857 13.142857 31.714286 2.285714q24.571429-3.428571 36.857143-16t13.142857-27.428571-4.285714-33.428571-10.857143-29.714286-11.428571-20.857143q-69.142857-108.571429-96.571429-138.285714-38.857143-42.285714-64.571429-22.857143-6.285714 5.142857-8.571429-8.571429-1.714286-9.142857-1.142857-21.714286 0.571429-16.571429 5.714286-29.714286t13.714286-26.857143 12.571429-24q4.571429-12 15.142857-41.142857t16.857143-44.571429 17.142857-34.857143 22.285714-30.857143q62.857143-81.714286 70.857143-111.428571-6.857143-64-9.142857-177.142857-1.142857-51.428571 13.714286-86.571429t60.571429-59.714286q22.285714-12 59.428571-12 30.285714-0.571429 60.571429 7.714286t50.857143 23.714286q32.571429 24 52.285714 69.428571t16.857143 84.285714q-2.857143 54.285714 17.142857 122.285714 19.428571 64.571429 76 124.571429 31.428571 33.714286 56.857143 93.142857t34 109.142857q4.571429 28 2.857143 48.285714t-6.857143 31.714286-11.428571 12.571429q-5.714286 1.142857-13.428571 10.857143t-15.428571 20.285714-23.142857 19.142857-34.857143 8q-10.285714-0.571429-18-2.857143t-12.857143-7.714286-7.714286-8.857143-6.571429-11.714286-5.142857-11.142857q-12.571429-21.142857-23.428571-17.142857t-16 28 4 55.428571q11.428571 40 0.571429 111.428571-5.714286 37.142857 10.285714 57.428571t41.714286 18.857143 48.571429-20.285714q33.714286-28 51.142857-38t59.142857-24.285714q30.285714-10.285714 44-20.857143t10.571429-19.714286-14.285714-16.285714-29.428571-13.428571q-18.857143-6.285714-28.285714-27.428571t-8.571429-41.428571 8.857143-27.142857q0.571429 17.714286 4.571429 32.285714t8.285714 23.142857 11.714286 16.285714 12 10.857143 12.285714 7.428571 9.428571 5.428571z">
                </path>
                </svg>
              </div>
              <div class="plat__body">
                <span class="plat__name">Linux</span>
                <div class="plat__linux-tabs" role="tablist" aria-label="Linux 发行版">
                  <button
                      type="button"
                      class="plat__linux-tab"
                      :class="{ 'plat__linux-tab--active': linuxVariant === 'debian' }"
                      role="tab"
                      :aria-selected="linuxVariant === 'debian'"
                      @click="linuxVariant = 'debian'"
                  >
                    Debian 系
                  </button>
                  <button
                      type="button"
                      class="plat__linux-tab"
                      :class="{ 'plat__linux-tab--active': linuxVariant === 'arch' }"
                      role="tab"
                      :aria-selected="linuxVariant === 'arch'"
                      @click="linuxVariant = 'arch'"
                  >
                    Arch 系
                  </button>
                </div>
                <template v-if="linuxVariant === 'debian'">
                  <span class="plat__fmt">安装包 · .deb</span>
                  <a :href="linuxDownloadUrl" class="plat__linux-cta" download>下载 .deb</a>
                </template>
                <template v-else>
                  <span class="plat__fmt">AUR · yay / paru</span>
                  <div class="plat__cmd">
                    <code class="plat__cmd-text">{{ archInstallCommand }}</code>
                    <button
                        type="button"
                        class="plat__cmd-copy"
                        :aria-label="archCopied ? '已复制' : '复制安装命令'"
                        @click="copyArchCommand"
                    >
                      {{ archCopied ? '已复制' : '复制' }}
                    </button>
                  </div>
                </template>
              </div>
            </div>

            <a :href="windowsDownloadUrl" class="plat" download>
              <div class="plat__icon plat__icon--win" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="currentColor" class="plat__svg">
                  <path d="M0 3.45L9.75 2.1v9.45H0m10.95-9.6L24 0v11.4H10.95M0 12.6h9.75v9.45L0 20.7m10.95-8.1H24V24l-12.9-1.8" />
                </svg>
              </div>
              <div class="plat__body">
                <span class="plat__name">Windows</span>
                <span class="plat__fmt">可执行安装包 · .exe</span>
              </div>
              <span class="plat__action">获取</span>
            </a>

            <a :href="macDownloadUrl" class="plat" download>
              <div class="plat__icon plat__icon--mac" aria-hidden="true">
                <svg viewBox="0 0 24 24" fill="currentColor" class="plat__svg">
                  <path
                    d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.81-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11z"
                  />
                </svg>
              </div>
              <div class="plat__body">
                <span class="plat__name">macOS</span>
                <span class="plat__fmt">安装包 · .pkg</span>
              </div>
              <span class="plat__action">获取</span>
            </a>
          </div>
        </section>
      </template>

      <footer class="foot">
        <p class="foot__line">安装如遇系统拦截，请在系统设置中允许来自开发者的应用。</p>
        <p class="foot__line foot__line--muted">© {{ year }} NekoMusic · 下载页</p>
      </footer>
    </PageShell>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, nextTick } from 'vue'
import axios from 'axios'
import API_CONFIG from '@/config/apiConfig.js'
import NIcon from '@/icons/NIcon.vue'
import { NButton, NTag, NSpinner } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'

const versionInfo = ref({ ver: '', updateUrl: '' })
const loading = ref(true)
const error = ref('')
const linuxVariant = ref('debian')
const archCopied = ref(false)
const archInstallCommand = 'yay -S neko-cloud-music'

const year = new Date().getFullYear()

const fetchVersionInfo = async () => {
  try {
    const response = await axios.get(`${API_CONFIG.BASE_URL}/version`, {
      timeout: 5000
    })
    versionInfo.value = response.data
  } catch (err) {
    console.error('获取版本信息失败:', err)
    error.value = '获取下载信息失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

// /version 可能由反向代理后的后端生成 http 链接；下载必须沿用当前 API 请求的协议。
const getRequestProtocol = () => {
  if (typeof window === 'undefined') return 'http:'
  try {
    return new URL(API_CONFIG.BASE_URL || window.location.origin, window.location.href).protocol
  } catch {
    return window.location.protocol
  }
}

const resolveDownloadUrl = (url) => {
  if (!url) return ''
  try {
    const resolved = new URL(url, API_CONFIG.BASE_URL || window.location.href)
    if (resolved.protocol === 'http:' || resolved.protocol === 'https:') {
      resolved.protocol = getRequestProtocol()
    }
    return resolved.toString()
  } catch {
    return url
  }
}

const replaceVersion = (url) => {
  if (!url) return ''
  const pcVer = versionInfo.value.pc?.pc_ver || versionInfo.value.ver
  return resolveDownloadUrl(url.replace('{pc_ver}', pcVer))
}

const androidDownloadUrl = computed(() => resolveDownloadUrl(versionInfo.value.updateUrl))

const windowsDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.windows || versionInfo.value.pc?.downloadUrl || versionInfo.value.updateUrl
  return replaceVersion(url)
})

const linuxDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.linux
  return replaceVersion(url)
})

const macDownloadUrl = computed(() => {
  const url = versionInfo.value.pc?.mac
  return replaceVersion(url)
})

let archCopyTimer = null
const copyArchCommand = async () => {
  try {
    await navigator.clipboard.writeText(archInstallCommand)
    archCopied.value = true
    if (archCopyTimer) clearTimeout(archCopyTimer)
    archCopyTimer = setTimeout(() => {
      archCopied.value = false
    }, 2000)
  } catch {
    /* 降级：部分环境无 clipboard API */
  }
}

onMounted(() => {
  fetchVersionInfo()
  nextTick(() => {
    if (typeof window !== 'undefined' && window.location.hash === '#netease-migrate') {
      document.getElementById('netease-migrate')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    }
  })
})
</script>

<style scoped>
/* ==================== 顶栏 ==================== */
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--n-space-4);
  margin-bottom: clamp(20px, 3vw, 32px);
}

/* ==================== Hero ==================== */
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 0.8fr);
  align-items: center;
  gap: clamp(24px, 5vw, 56px);
  padding-bottom: clamp(32px, 5vw, 56px);
}

.hero__eyebrow {
  margin: 0 0 var(--n-space-3);
  color: var(--n-accent-strong);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-bold);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero__title {
  margin: 0 0 var(--n-space-4);
  font-size: clamp(1.8rem, 4.5vw, 2.8rem);
  font-weight: var(--n-weight-bold);
  line-height: 1.1;
  letter-spacing: -0.03em;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero__lede {
  max-width: 52ch;
  margin: 0 0 var(--n-space-5);
  color: var(--n-text-muted);
  line-height: var(--n-leading-loose);
}

.hero__lede-strong {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

.hero__facts {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-2) var(--n-space-5);
  margin: 0 0 var(--n-space-5);
  padding: 0;
  list-style: none;
}

.hero__facts li {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.hero__facts li::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--n-accent);
}

.hero__anchor-hint {
  margin: 0;
}

.hero__anchor-link {
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
}

.hero__art {
  justify-self: center;
  text-align: center;
}

.hero__frame {
  display: grid;
  place-items: center;
  width: clamp(180px, 26vw, 260px);
  aspect-ratio: 1;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  background: var(--n-surface);
  box-shadow: var(--n-shadow-lg);
}

.hero__logo {
  width: 62%;
  height: auto;
  border-radius: var(--n-radius-lg);
}

.hero__art-caption {
  margin: var(--n-space-4) 0 0;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  letter-spacing: 0.04em;
}

/* ==================== 歌单迁入 ==================== */
.netease-panel {
  margin-bottom: clamp(28px, 4vw, 44px);
}

.netease-panel__inner {
  padding: clamp(22px, 3.5vw, 32px);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  background: var(--n-surface);
}

.netease-panel__eyebrow {
  margin: 0 0 var(--n-space-3);
  color: var(--n-accent-strong);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-semibold);
  letter-spacing: 0.06em;
}

.netease-panel__title {
  margin: 0 0 var(--n-space-3);
  font-size: clamp(1.2rem, 2.6vw, 1.5rem);
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.02em;
}

.netease-panel__lede {
  max-width: 72ch;
  margin: 0 0 var(--n-space-3);
  color: var(--n-text-muted);
  line-height: var(--n-leading-loose);
}

.netease-panel__note {
  max-width: 72ch;
  margin: 0 0 var(--n-space-5);
  padding: var(--n-space-3) var(--n-space-4);
  border: 1px solid var(--n-accent-line);
  border-radius: var(--n-radius);
  background: var(--n-accent-soft);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.netease-panel__steps {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-3);
  margin: 0;
  padding: 0;
  list-style: none;
  counter-reset: step;
}

.netease-panel__steps li {
  display: flex;
  align-items: flex-start;
  gap: var(--n-space-3);
  color: var(--n-text-muted);
  line-height: var(--n-leading-normal);
}

.netease-panel__step-num {
  display: grid;
  place-items: center;
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: var(--n-radius-xs);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-bold);
}

/* ==================== 状态 ==================== */
.state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-16) 0;
  color: var(--n-text-muted);
}

.state__spinner {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 3px solid var(--n-line);
  border-top-color: var(--n-accent);
  animation: dlSpin 0.85s linear infinite;
}

@keyframes dlSpin {
  to { transform: rotate(360deg); }
}

.state--error {
  color: var(--n-danger);
}

.state__icon {
  width: 32px;
  height: 32px;
}

/* ==================== Android ==================== */
.android {
  margin-bottom: clamp(24px, 3.5vw, 36px);
}

.android__inner {
  display: flex;
  align-items: center;
  gap: clamp(16px, 3vw, 28px);
  padding: clamp(20px, 3vw, 28px);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xl);
  background: var(--n-surface);
}

.android__icon-wrap {
  display: grid;
  place-items: center;
  flex: none;
  width: 56px;
  height: 56px;
  border-radius: var(--n-radius);
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}

.android__icon {
  width: 28px;
  height: 28px;
}

.android__main {
  flex: 1;
  min-width: 0;
}

.android__title {
  margin: 0 0 2px;
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
}

.android__sub {
  margin: 0;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.android__meta {
  margin: var(--n-space-3) 0 0;
}

.android__meta-row {
  display: flex;
  align-items: baseline;
  gap: var(--n-space-3);
}

.android__meta-row dt {
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.android__meta-row dd {
  margin: 0;
  color: var(--n-text);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  font-variant-numeric: tabular-nums;
}

.android__cta {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 44px;
  padding: 0 var(--n-space-6);
  border-radius: var(--n-radius-control);
  background: var(--n-accent);
  color: var(--n-text-inverse);
  font-weight: var(--n-weight-semibold);
  transition: background var(--n-duration-fast) var(--n-ease), transform var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .android__cta:hover {
    background: var(--n-accent-strong);
    transform: translateY(-1px);
  }
}

.android__cta-label {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
}

.android__cta-icon {
  width: 18px;
  height: 18px;
}

/* ==================== 桌面 ==================== */
.desktop__intro {
  margin-bottom: var(--n-space-5);
}

.desktop__title {
  margin: 0 0 var(--n-space-1);
  font-size: clamp(1.2rem, 2.6vw, 1.5rem);
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.02em;
}

.desktop__sub {
  margin: 0;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.desktop__version {
  display: inline-flex;
  align-items: center;
  gap: var(--n-space-2);
  margin-top: var(--n-space-3);
  padding: 5px 12px;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-xs);
  background: var(--n-surface-soft);
}

.desktop__version-label {
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.desktop__version-value {
  color: var(--n-text);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  font-variant-numeric: tabular-nums;
}

.desktop__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--n-space-4);
}

.plat {
  display: flex;
  align-items: center;
  gap: var(--n-space-4);
  padding: var(--n-space-5);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-lg);
  background: var(--n-surface);
  color: inherit;
  transition: border-color var(--n-duration-fast) var(--n-ease), background var(--n-duration-fast) var(--n-ease), transform var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  a.plat:hover {
    border-color: var(--n-line-strong);
    background: var(--n-surface-hover);
    transform: translateY(-2px);
  }
}

.plat__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 46px;
  height: 46px;
  border-radius: var(--n-radius-sm);
  background: var(--n-surface-soft);
  border: 1px solid var(--n-line);
  color: var(--n-text-muted);
}

.plat__svg {
  width: 24px;
  height: 24px;
}

.plat__body {
  flex: 1;
  min-width: 0;
}

.plat__name {
  display: block;
  color: var(--n-text);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
}

.plat__fmt {
  display: block;
  margin-top: 2px;
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

.plat__action {
  flex: none;
  color: var(--n-accent-strong);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
}

/* Linux 变体 */
.plat--linux {
  grid-column: span 2;
  flex-direction: column;
  align-items: stretch;
  gap: var(--n-space-4);
}

.plat--linux .plat__icon {
  align-self: flex-start;
}

.plat__linux-tabs {
  display: flex;
  gap: var(--n-space-1);
  padding: 3px;
  margin: var(--n-space-3) 0;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-soft);
  width: fit-content;
}

.plat__linux-tab {
  padding: 6px 14px;
  border-radius: var(--n-radius-xs);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-medium);
  transition: background var(--n-duration-fast) var(--n-ease), color var(--n-duration-fast) var(--n-ease);
}

.plat__linux-tab--active {
  background: var(--n-accent-soft);
  color: var(--n-accent-strong);
}

.plat__linux-cta {
  display: inline-flex;
  align-items: center;
  padding: 8px 16px;
  border-radius: var(--n-radius-control);
  background: var(--n-accent-soft);
  border: 1px solid var(--n-accent-line);
  color: var(--n-accent-strong);
  font-size: var(--n-text-sm);
  font-weight: var(--n-weight-semibold);
  width: fit-content;
}

.plat__cmd {
  display: flex;
  align-items: center;
  gap: var(--n-space-2);
  padding: var(--n-space-2) var(--n-space-2) var(--n-space-2) var(--n-space-4);
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius-control);
  background: var(--n-surface-sunken);
}

.plat__cmd-text {
  flex: 1;
  min-width: 0;
  color: var(--n-text-muted);
  font-family: var(--n-font-mono);
  font-size: var(--n-text-xs);
  overflow-x: auto;
  white-space: nowrap;
}

.plat__cmd-copy {
  flex: none;
  padding: 5px 12px;
  border-radius: var(--n-radius-xs);
  background: var(--n-surface-hover);
  color: var(--n-text);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-semibold);
}

/* ==================== 页脚 ==================== */
.foot {
  margin-top: clamp(32px, 5vw, 56px);
  padding-top: var(--n-space-5);
  border-top: 1px solid var(--n-line-subtle);
}

.foot__line {
  margin: 0 0 var(--n-space-1);
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
}

.foot__line--muted {
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
}

/* ==================== 响应式 ==================== */
@media (max-width: 900px) {
  .hero {
    grid-template-columns: 1fr;
    gap: var(--n-space-8);
  }

  .hero__art {
    justify-self: start;
  }

  .plat--linux {
    grid-column: auto;
  }
}

@media (max-width: 640px) {
  .android__inner {
    flex-direction: column;
    align-items: flex-start;
  }

  .android__cta {
    width: 100%;
  }
}

@media (prefers-reduced-motion: reduce) {
  .state__spinner {
    animation-duration: 2s;
  }
}
</style>
