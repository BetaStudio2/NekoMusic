<script setup>
/**
 * PrivacyPolicyView —— 隐私政策
 * ------------------------------------------------------------
 * 长文静态页。保留全部文案；去除左侧高亮条，提示块改为图标 + 淡色表面。
 */
import { ref, onMounted, onUnmounted } from 'vue'
import NIcon from '@/icons/NIcon.vue'
import { NCard, NTag } from '@/ui'
import { PageShell, AmbientBackdrop } from '@/layouts'

const toc = [
  { id: 'collect', label: '一、我们如何收集和使用您的个人信息' },
  { id: 'permissions', label: '二、权限调用说明' },
  { id: 'storage-tech', label: '三、Cookie、本地存储及同类技术' },
  { id: 'third-party', label: '四、委托处理、共享、转让和公开披露' },
  { id: 'protect', label: '五、我们如何保护您的个人信息' },
  { id: 'retain', label: '六、我们如何存储您的个人信息' },
  { id: 'rights', label: '七、您的权利' },
  { id: 'children', label: '八、未成年人个人信息保护' },
  { id: 'updates', label: '九、隐私政策更新说明' },
  { id: 'contact', label: '十、如何联系我们' },
]

/* ===== 目录：滚动高亮（scroll-spy）+ 点击平滑滚动 ===== */
const activeId = ref('')
/** 与 .block 的 scroll-margin-top 对齐的停靠线 */
const STOP_LINE = 80
/** 点击目录后的平滑滚动期间锁定高亮，避免被途经章节覆盖 */
let spyLocked = false
let unlockTimer = null

function unlockSpy() {
  spyLocked = false
  if (unlockTimer) {
    clearTimeout(unlockTimer)
    unlockTimer = null
  }
}

function scrollTo(id) {
  const el = document.getElementById(id)
  if (!el) return

  activeId.value = id
  spyLocked = true
  if (unlockTimer) clearTimeout(unlockTimer)
  unlockTimer = setTimeout(unlockSpy, 800) // 兜底：scrollend 不可靠时恢复跟随

  // scrollIntoView 会遵循 .block 的 scroll-margin-top，无需手算顶栏高度
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches
  el.scrollIntoView({ behavior: reduce ? 'auto' : 'smooth', block: 'start' })

  history.replaceState(null, '', `#${id}`)
}

function onScroll() {
  if (spyLocked || !toc.length) return
  let current = ''
  for (const t of toc) {
    const el = document.getElementById(t.id)
    if (el && el.getBoundingClientRect().top <= STOP_LINE) current = t.id
  }
  if (!current) current = toc[0].id
  activeId.value = current
}

onMounted(() => {
  window.scrollTo(0, 0)
  window.addEventListener('scroll', onScroll, { passive: true })
  onScroll()
})

onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  if (unlockTimer) clearTimeout(unlockTimer)
})
</script>

<template>
  <AmbientBackdrop />

  <PageShell width="xwide">
    <div class="doc">
      <!-- 目录：桌面端 sticky 侧栏，窄屏置顶 -->
      <aside class="doc__toc" aria-label="隐私政策目录">
        <p class="doc__toc-title">目录</p>
        <nav class="doc__toc-nav">
          <a
            v-for="t in toc"
            :key="t.id"
            :href="`#${t.id}`"
            class="doc__toc-link"
            :class="{ 'doc__toc-link--active': activeId === t.id }"
            @click.prevent="scrollTo(t.id)"
          >
            {{ t.label }}
          </a>
        </nav>
      </aside>

      <div class="doc__main">
    <!-- 头部 -->
    <NCard pad="lg" class="block hero">
      <p class="hero__eyebrow">Privacy Policy</p>
      <h1 class="hero__title">Neko歌姬计划隐私政策</h1>
      <p class="hero__sub">
        本政策说明 Neko歌姬计划在提供网页、Android 与 PC 客户端服务时如何收集、使用、保存、共享和保护您的个人信息。
      </p>
      <div class="hero__meta">
        <NTag size="sm">发布日期：2026 年 6 月 20 日</NTag>
        <NTag size="sm">更新日期：2026 年 6 月 21 日</NTag>
        <NTag size="sm">生效日期：2026 年 6 月 21 日</NTag>
      </div>
    </NCard>

    <!-- 引言 -->
    <NCard pad="lg" class="block">
      <h2 class="block__title">引言</h2>
      <p class="para">
        Neko歌姬计划由 Fantasy Network「梦幻网络」，以下简称“我们” 提供音乐搜索、在线播放、收藏、歌单、上传、客户端播放、会员及相关服务。
      </p>
      <p class="para">
        我们十分重视您的个人信息和隐私保护。本政策将帮助您了解我们在提供服务过程中如何处理您的个人信息，请您在使用我们的产品或服务前仔细阅读并充分理解本政策。
      </p>
      <p class="note">
        <NIcon name="info" :size="16" class="note__icon" />
        <span>如果您是未满 14 周岁的未成年人，请在父母或其他监护人陪同下阅读本政策，并在取得监护人同意后使用我们的产品或服务。</span>
      </p>
    </NCard>

    <!-- 一 -->
    <NCard id="collect" pad="lg" class="block">
      <h2 class="block__title">一、我们如何收集和使用您的个人信息</h2>
      <p class="para">
        我们收集个人信息的来源主要包括：您主动提交的信息、您使用服务时产生的信息、为实现您选择的功能而从第三方合法获取的信息。我们仅会为本政策声明的目的处理个人信息。
      </p>
      <p class="note">
        <NIcon name="info" :size="16" class="note__icon" />
        <span>由于网页、Android 与 PC 客户端版本和功能配置可能不同，我们实际收集的信息取决于您使用的端、版本及具体功能。以下加粗并带下划线的内容属于需要特别关注的敏感或重要个人信息。</span>
      </p>

      <div class="table-wrap">
        <table class="info-table">
          <thead>
            <tr>
              <th>功能或场景</th>
              <th>可能收集的信息</th>
              <th>目的和使用方式</th>
              <th>不提供的影响</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>注册、登录、找回密码</td>
              <td>
                用户名、昵称、头像、<strong class="sensitive">电子邮箱地址</strong>、<strong class="sensitive">密码或密码加密摘要</strong>、登录 token、账号状态、注册和登录时间。
              </td>
              <td>创建账号、验证身份、维持登录状态、找回密码、保障账号安全。</td>
              <td>您将无法使用收藏、歌单、上传、会员等需要登录的功能。</td>
            </tr>
            <tr>
              <td>音乐搜索、播放、下载</td>
              <td>
                搜索关键词、歌曲 ID、播放和下载请求、播放进度、播放列表、播放模式、访问时间、<strong class="sensitive">IP 地址</strong>、设备类型、浏览器或客户端版本、错误日志。
              </td>
              <td>提供搜索、播放、下载、断点恢复、故障排查、服务安全和统计分析。</td>
              <td>部分播放、下载、历史状态恢复或问题定位能力可能无法正常使用。</td>
            </tr>
            <tr>
              <td>收藏、歌单和歌单迁入</td>
              <td>
                收藏记录、歌单名称、歌单歌曲、歌单封面、第三方歌单链接或 ID、从网易云音乐、QQ 音乐或酷狗音乐等来源返回的歌单名称和曲目信息。
              </td>
              <td>保存您的音乐偏好，按您主动提交的链接或 ID 完成歌单匹配和导入。</td>
              <td>您将无法保存收藏、管理歌单或完成第三方歌单迁入。</td>
            </tr>
            <tr>
              <td>音乐上传和头像上传</td>
              <td>
                上传的音乐文件、歌词、封面、歌曲名称、歌手、专辑、上传记录、审核状态、<strong class="sensitive">您主动上传的图片、音频或歌词内容</strong>。
              </td>
              <td>保存和展示您主动上传的内容，进行必要的格式处理、审核、管理和问题排查。</td>
              <td>您将无法上传音乐、修改头像或管理已上传内容。</td>
            </tr>
            <tr>
              <td>会员和支付</td>
              <td>
                用户 ID、会员套餐、订单号、支付方式、支付状态、支付回调结果、订单创建和完成时间。我们不会收集您的完整银行卡号或支付账户密码。
              </td>
              <td>创建订单、确认支付结果、开通或延长会员权益、处理售后和对账。</td>
              <td>您将无法购买或使用需要支付确认的会员权益。</td>
            </tr>
            <tr>
              <td>Android 本地音乐、桌面歌词、通知和更新</td>
              <td>
                <strong class="sensitive">本地音频文件信息、文件名、音频元数据或文件路径</strong>、通知状态、更新包下载状态、桌面歌词开关和显示状态。
              </td>
              <td>扫描和播放本地音乐、显示播放通知、保持后台播放、展示桌面歌词、下载和安装客户端更新。</td>
              <td>您可以拒绝相关权限；拒绝后仅影响对应功能，不影响基础在线浏览和播放。</td>
            </tr>
            <tr>
              <td>客服、反馈和安全处理</td>
              <td>您主动提供的联系方式、反馈内容、截图或日志、相关账号信息、处理记录。</td>
              <td>回复您的咨询、处理投诉、定位故障、验证账号归属、维护服务安全。</td>
              <td>我们可能无法准确处理您的问题或反馈。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <h3 class="block__sub">个性化推荐和广告</h3>
      <p class="para">
        目前我们不以第三方广告投放或商业画像为目的使用您的搜索、播放、浏览记录进行个性化广告推荐。若未来新增个性化推荐、个性化广告或类似功能，我们会在上线前更新本政策，并在产品内提供关闭或退出方式。
      </p>
    </NCard>

    <!-- 二 -->
    <NCard id="permissions" pad="lg" class="block">
      <h2 class="block__title">二、权限调用说明</h2>
      <p class="para">
        Android 客户端会在实现特定功能时申请系统权限。您可以在系统设置中管理授权；拒绝或关闭授权通常只会影响对应功能，不影响其他不依赖该权限的功能。
      </p>

      <div class="table-wrap">
        <table class="info-table">
          <thead>
            <tr>
              <th>权限或能力</th>
              <th>使用目的</th>
              <th>触发场景</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>网络访问、网络状态</td>
              <td>连接服务器，完成登录、搜索、播放、下载、上传、支付和更新检查。</td>
              <td>打开应用、访问在线功能时。</td>
            </tr>
            <tr>
              <td>通知权限</td>
              <td>展示播放状态、下载或更新状态等必要通知。</td>
              <td>您使用播放、下载、更新等功能时。</td>
            </tr>
            <tr>
              <td>前台服务、媒体播放前台服务、唤醒锁</td>
              <td>在锁屏、后台或切换应用时维持音乐播放和媒体控制。</td>
              <td>您启动播放并允许后台播放时。</td>
            </tr>
            <tr>
              <td>读取音频媒体或外部存储</td>
              <td>扫描、选择和播放本地音乐，或在您主动上传时读取被选择的文件。</td>
              <td>您使用本地音乐、文件选择或上传功能时。</td>
            </tr>
            <tr>
              <td>悬浮窗权限</td>
              <td>在其他应用上方显示桌面歌词或悬浮播放控件。</td>
              <td>您主动开启桌面歌词或悬浮显示功能时。</td>
            </tr>
            <tr>
              <td>安装未知应用、无通知下载、FileProvider 临时读取</td>
              <td>下载客户端更新包，并在您确认后调起系统安装流程。</td>
              <td>您下载或安装应用更新时。</td>
            </tr>
            <tr>
              <td>查询微信、支付宝应用及相关 URL Scheme</td>
              <td>判断是否可拉起对应支付应用或浏览器支付页面。</td>
              <td>您选择微信或支付宝等支付方式时。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </NCard>

    <!-- 三 -->
    <NCard id="storage-tech" pad="lg" class="block">
      <h2 class="block__title">三、Cookie、本地存储及同类技术</h2>
      <p class="para">
        为保证网站和客户端 WebView 的基本功能，我们可能使用 Cookie、localStorage、sessionStorage 或同类技术保存必要信息，包括登录 token、用户基础资料、当前播放歌曲、播放队列、播放进度、播放模式、移动端下载提示关闭状态、管理员登录状态等。
      </p>
      <p class="para">
        您可以通过浏览器或系统设置清除这些本地数据。清除后，您可能需要重新登录，播放队列、偏好设置或页面状态也可能被重置。
      </p>
    </NCard>

    <!-- 四 -->
    <NCard id="third-party" pad="lg" class="block">
      <h2 class="block__title">四、委托处理、共享、转让和公开披露</h2>
      <p class="para">
        我们不会出售您的个人信息。为了实现特定功能，我们可能在必要范围内向第三方服务提供者委托处理或共享必要信息，并要求其按照约定目的和安全要求处理信息。
      </p>

      <div class="table-wrap">
        <table class="info-table">
          <thead>
            <tr>
              <th>第三方或服务类型</th>
              <th>涉及信息</th>
              <th>使用目的</th>
              <th>触发场景</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>支付服务和支付渠道</td>
              <td>订单号、套餐、金额、支付方式、支付状态、必要的支付回调信息。</td>
              <td>完成会员订单创建、支付确认、权益发放和对账。</td>
              <td>您主动购买会员并选择支付方式时。</td>
            </tr>
            <tr>
              <td>邮件、验证码或消息服务</td>
              <td><strong class="sensitive">电子邮箱地址</strong>、验证码发送状态、必要的请求日志。</td>
              <td>注册验证、找回密码、账号安全通知和客服回复。</td>
              <td>您注册、重置密码或联系我们时。</td>
            </tr>
            <tr>
              <td>网易云音乐、QQ 音乐、酷狗音乐等歌单来源</td>
              <td>您主动提交的歌单链接或 ID、返回的歌单名称和曲目信息。</td>
              <td>完成歌单迁入和站内曲库匹配。</td>
              <td>您主动使用第三方歌单迁入功能时。</td>
            </tr>
            <tr>
              <td>文件存储、CDN、下载和应用分发服务</td>
              <td>上传或下载的文件、访问请求、必要的网络日志。</td>
              <td>存储音乐、封面、头像、安装包，提升访问和下载稳定性。</td>
              <td>您上传、播放、下载或更新客户端时。</td>
            </tr>
            <tr>
              <td>应用商店、平台审核或监管要求</td>
              <td>应用安装包、隐私政策链接、权限说明、必要的合规材料。</td>
              <td>完成应用分发审核、合规检查或依法配合监管。</td>
              <td>应用上架、更新审核或依法需要时。</td>
            </tr>
          </tbody>
        </table>
      </div>

      <h3 class="block__sub">转让和公开披露</h3>
      <p class="para">
        除取得您的明确同意、依法需要、保护用户或公众重大合法权益、公司合并分立或资产转让等法律允许情形外，我们不会转让您的个人信息。发生合并、收购、清算或类似变更时，我们会要求新的持有方继续受本政策约束，否则将要求其重新取得您的授权。
      </p>
      <p class="para">
        我们原则上不会公开披露您的个人信息；确需公开披露时，会取得您的单独同意或依据法律法规、行政机关、司法机关要求进行。
      </p>
    </NCard>

    <!-- 五 -->
    <NCard id="protect" pad="lg" class="block">
      <h2 class="block__title">五、我们如何保护您的个人信息</h2>
      <p class="para">
        我们会采取合理的技术和管理措施保护您的个人信息，包括访问权限控制、身份校验、敏感凭据加密或摘要存储、传输加密、日志审计、异常访问排查、备份和安全事件响应等。
      </p>
      <p class="para">
        互联网环境并非绝对安全。如发生个人信息安全事件，我们将根据法律法规要求采取补救措施，并通过站内公告、弹窗、邮件或其他合理方式告知可能受影响的用户。
      </p>
    </NCard>

    <!-- 六 -->
    <NCard id="retain" pad="lg" class="block">
      <h2 class="block__title">六、我们如何存储您的个人信息</h2>
      <p class="para">
        我们在中华人民共和国境内收集和产生的个人信息将存储在中华人民共和国境内。如未来涉及跨境传输，我们会按照法律法规要求履行相应程序，并向您说明境外接收方、处理目的、处理方式、信息种类及您行使权利的方式。
      </p>

      <div class="table-wrap">
        <table class="info-table">
          <thead>
            <tr>
              <th>信息类型</th>
              <th>保存期限</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>账号资料、登录凭据、会员状态</td>
              <td>在账号存续期间保存；账号注销或删除后，在法律要求或安全审计必要期限届满后删除或匿名化。</td>
            </tr>
            <tr>
              <td>收藏、歌单、上传内容和审核记录</td>
              <td>保存至您主动删除、账号注销，或为处理争议、安全、版权和合规问题所需的合理期限届满。</td>
            </tr>
            <tr>
              <td>支付订单和交易记录</td>
              <td>按照支付、财税、会计、争议处理等法律法规要求保存必要期限。</td>
            </tr>
            <tr>
              <td>网络安全日志、操作日志和错误日志</td>
              <td>通常保存不超过 3 年；法律法规另有要求或安全事件处理需要的除外。</td>
            </tr>
            <tr>
              <td>本地播放状态、播放队列和偏好</td>
              <td>主要保存在您的设备本地，直至您清除浏览器/应用数据、卸载应用或重置相关设置。</td>
            </tr>
          </tbody>
        </table>
      </div>
    </NCard>

    <!-- 七 -->
    <NCard id="rights" pad="lg" class="block">
      <h2 class="block__title">七、您的权利</h2>
      <p class="para">
        您有权依法访问、更正、补充、复制、删除您的个人信息，也可以撤回授权同意、关闭系统权限、注销或删除账号、要求解释个人信息处理规则。
      </p>
      <ul class="list">
        <li>访问和更正：您可以在个人中心查看和修改部分账号资料；无法自行处理的，可通过联系方式向我们提出请求。</li>
        <li>删除和注销：您可以删除自己创建的歌单、上传内容等信息；如需注销账号或删除账号相关数据，可通过邮箱联系我们。</li>
        <li>撤回同意：您可以通过系统权限设置关闭通知、存储、悬浮窗等授权，也可以停止使用相关功能或清除本地数据。</li>
        <li>复制和导出：在法律法规要求范围内，您可以请求复制与您相关的个人信息，我们会在可行范围内提供。</li>
      </ul>
      <p class="note">
        <NIcon name="info" :size="16" class="note__icon" />
        <span>为保护账号和数据安全，我们可能会在处理请求前验证您的身份，并要求您提供具体、可执行的请求内容。我们会在 15 个工作日内或法律法规要求的期限内回复；对依法无法响应的请求，我们会说明原因。</span>
      </p>
    </NCard>

    <!-- 八 -->
    <NCard id="children" pad="lg" class="block">
      <h2 class="block__title">八、未成年人个人信息保护</h2>
      <p class="para">
        我们不会主动以未成年人为目标收集个人信息。未满 14 周岁的未成年人使用本服务，应事先取得父母或其他监护人的同意。
      </p>
      <p class="para">
        如果监护人发现我们在未取得监护人同意的情况下收集了未成年人的个人信息，或希望查询、更正、删除未成年人信息，请通过本政策列明的方式联系我们，我们会依法处理。
      </p>
    </NCard>

    <!-- 九 -->
    <NCard id="updates" pad="lg" class="block">
      <h2 class="block__title">九、隐私政策更新说明</h2>
      <p class="para">
        我们可能根据产品功能、法律法规或运营情况更新本政策。更新后，我们会在本页面展示最新版本及生效日期。
      </p>
      <p class="para">
        如果政策变更涉及处理目的、处理方式、个人信息种类、共享对象、用户权利行使方式等重大变化，我们会通过弹窗、公告、站内提示、邮件或其他显著方式通知您，并在法律法规要求时重新取得您的同意。
      </p>
    </NCard>

    <!-- 十 -->
    <NCard id="contact" pad="lg" class="block block--last">
      <h2 class="block__title">十、如何联系我们</h2>
      <p class="para">
        如果您对本隐私政策、个人信息处理规则或账号数据处理有疑问、意见、投诉或权利请求，可以通过以下方式联系我们：
      </p>
      <ul class="list">
        <li>电子邮件：<a href="mailto:support@cnmsb.xin">support@cnmsb.xin</a></li>
        <li>官网：<a href="https://www.cnmsb.xin/" target="_blank" rel="noopener noreferrer">https://www.cnmsb.xin/</a></li>
        <li>QQ群：<a href="https://qm.qq.com/q/Q9HkDi6Ewk" target="_blank" rel="noopener noreferrer">Fantasy Network交流群</a></li>
      </ul>
      <p class="para">
        我们会在 15 个工作日内或法律法规要求的期限内回复。为保障您的信息安全，我们可能会先核验您的身份，再处理查询、更正、删除、注销、复制或投诉请求。
      </p>
    </NCard>
      </div>
    </div>
  </PageShell>
</template>

<style scoped>
.block {
  margin-bottom: var(--n-space-4);
  scroll-margin-top: calc(var(--n-header-height) + var(--n-space-4));
}

.block--last {
  margin-bottom: 0;
}

.block__title {
  margin: 0 0 var(--n-space-4);
  font-size: var(--n-text-lg);
  font-weight: var(--n-weight-semibold);
  letter-spacing: -0.01em;
}

.block__sub {
  margin: var(--n-space-6) 0 var(--n-space-3);
  font-size: var(--n-text-md);
  font-weight: var(--n-weight-semibold);
}

.para {
  margin: 0 0 var(--n-space-3);
  color: var(--n-text-muted);
  line-height: var(--n-leading-loose);
}

.para:last-child {
  margin-bottom: 0;
}

/* ===== 提示块（原紫色左边条 → 图标 + 淡色表面）===== */
.note {
  display: flex;
  gap: var(--n-space-3);
  margin: var(--n-space-4) 0 0;
  padding: var(--n-space-4);
  border: 1px solid var(--n-accent-line);
  border-radius: var(--n-radius);
  background: var(--n-accent-soft);
  color: var(--n-text);
  font-size: var(--n-text-base);
  line-height: var(--n-leading-normal);
}

.note__icon {
  flex: none;
  margin-top: 3px;
  color: var(--n-accent-strong);
}

/* ===== 列表（原青色左边条 → 中性圆点）===== */
.list {
  margin: var(--n-space-3) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--n-space-3);
}

.list li {
  position: relative;
  padding-left: var(--n-space-5);
  color: var(--n-text-muted);
  line-height: var(--n-leading-normal);
}

.list li::before {
  content: '';
  position: absolute;
  left: 2px;
  top: 0.62em;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--n-accent);
  opacity: 0.8;
}

.list a {
  font-weight: var(--n-weight-semibold);
}

/* ===== 头部 ===== */
.hero__eyebrow {
  margin: 0 0 var(--n-space-3);
  color: var(--n-accent-strong);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-bold);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.hero__title {
  margin: 0 0 var(--n-space-3);
  font-size: clamp(1.9rem, 4.5vw, 2.8rem);
  font-weight: var(--n-weight-bold);
  line-height: 1.08;
  letter-spacing: -0.03em;
  background: var(--n-gradient-text);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero__sub {
  max-width: 760px;
  margin: 0;
  color: var(--n-text-muted);
  line-height: var(--n-leading-loose);
}

.hero__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--n-space-2);
  margin-top: var(--n-space-5);
}

/* ===== 布局：正文 + 目录侧栏 ===== */
.doc {
  display: flex;
  flex-direction: column;
  gap: var(--n-space-5);
}

/* 窄屏：目录置顶（无容器外观，仅标题 + 链接） */
.doc__toc {
  order: -1;
}

.doc__main {
  order: 1;
  min-width: 0;
}

.doc__toc-title {
  margin: 0 0 var(--n-space-3);
  color: var(--n-text-faint);
  font-size: var(--n-text-xs);
  font-weight: var(--n-weight-bold);
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.doc__toc-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.doc__toc-link {
  display: block;
  padding: var(--n-space-2) var(--n-space-3);
  border-radius: var(--n-radius-xs);
  color: var(--n-text-faint);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
  transition:
    color var(--n-duration-fast) var(--n-ease),
    background var(--n-duration-fast) var(--n-ease);
}

@media (hover: hover) {
  .doc__toc-link:hover {
    color: var(--n-text-muted);
    background: var(--n-surface-soft);
  }
}

.doc__toc-link--active {
  color: var(--n-accent-strong);
  background: var(--n-accent-soft);
}

/* 桌面端：正文限宽在左，目录在右并随滚动常驻 */
@media (min-width: 1024px) {
  .doc {
    flex-direction: row;
    justify-content: center;
    align-items: flex-start;
    gap: clamp(32px, 4vw, 56px);
  }

  .doc__main {
    order: 1;
    flex: 1 1 auto;
    /* 长文限宽，避免每行过长难以阅读 */
    max-width: 1100px;
  }

  /* 目录必须在正文之后（order 2），否则会排到左侧 */
  .doc__toc {
    order: 2;
    flex: 0 0 240px;
    position: sticky;
    top: calc(var(--n-header-height) + var(--n-space-5));
    max-height: calc(100dvh - var(--n-header-height) - var(--n-space-10));
    overflow-y: auto;
  }
}

/* ===== 表格 ===== */
.table-wrap {
  width: 100%;
  margin: var(--n-space-5) 0;
  overflow-x: auto;
  border: 1px solid var(--n-line);
  border-radius: var(--n-radius);
  background: var(--n-surface-sunken);
}

.info-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  color: var(--n-text-muted);
  font-size: var(--n-text-sm);
  line-height: var(--n-leading-normal);
}

.info-table th,
.info-table td {
  border-bottom: 1px solid var(--n-line-subtle);
  padding: 13px 14px;
  text-align: left;
  vertical-align: top;
}

.info-table th {
  background: var(--n-accent-soft);
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
  white-space: nowrap;
}

.info-table tr:last-child td {
  border-bottom: 0;
}

.info-table td:first-child {
  color: var(--n-text);
  font-weight: var(--n-weight-semibold);
}

.sensitive {
  color: var(--n-warning);
  font-weight: var(--n-weight-semibold);
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
