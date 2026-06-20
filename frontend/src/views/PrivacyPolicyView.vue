<template>
  <div class="glass-page">
    <div class="ambient" aria-hidden="true">
      <div class="ambient__blob ambient__blob--a" />
      <div class="ambient__blob ambient__blob--b" />
      <div class="ambient__blob ambient__blob--c" />
      <div class="ambient__grid" />
    </div>

    <main class="shell privacy-shell">
      <header class="panel privacy-hero">
        <p class="privacy-eyebrow">Privacy Policy</p>
        <h1 class="privacy-title">Neko歌姬计划隐私政策</h1>
        <p class="privacy-sub">
          本政策说明 Neko歌姬计划在提供网页、Android 与 PC 客户端服务时如何收集、使用、保存、共享和保护您的个人信息。
        </p>
        <div class="privacy-meta">
          <span>发布日期：2026 年 6 月 20 日</span>
          <span>更新日期：2026 年 6 月 21 日</span>
          <span>生效日期：2026 年 6 月 21 日</span>
        </div>
      </header>

      <section class="panel privacy-section">
        <h2>引言</h2>
        <p>
          Neko歌姬计划由 Fantasy Network「梦幻网络」，以下简称“我们” 提供音乐搜索、在线播放、收藏、歌单、上传、客户端播放、会员及相关服务。
        </p>
        <p>
          我们十分重视您的个人信息和隐私保护。本政策将帮助您了解我们在提供服务过程中如何处理您的个人信息，请您在使用我们的产品或服务前仔细阅读并充分理解本政策。
        </p>
        <p class="privacy-note">
          如果您是未满 14 周岁的未成年人，请在父母或其他监护人陪同下阅读本政策，并在取得监护人同意后使用我们的产品或服务。
        </p>
      </section>

      <nav class="panel privacy-toc" aria-label="隐私政策目录">
        <h2>目录</h2>
        <div class="toc-grid">
          <a href="#collect">一、我们如何收集和使用您的个人信息</a>
          <a href="#permissions">二、权限调用说明</a>
          <a href="#storage-tech">三、Cookie、本地存储及同类技术</a>
          <a href="#third-party">四、委托处理、共享、转让和公开披露</a>
          <a href="#protect">五、我们如何保护您的个人信息</a>
          <a href="#retain">六、我们如何存储您的个人信息</a>
          <a href="#rights">七、您的权利</a>
          <a href="#children">八、未成年人个人信息保护</a>
          <a href="#updates">九、隐私政策更新说明</a>
          <a href="#contact">十、如何联系我们</a>
        </div>
      </nav>

      <section id="collect" class="panel privacy-section">
        <h2>一、我们如何收集和使用您的个人信息</h2>
        <p>
          我们收集个人信息的来源主要包括：您主动提交的信息、您使用服务时产生的信息、为实现您选择的功能而从第三方合法获取的信息。我们仅会为本政策声明的目的处理个人信息。
        </p>
        <p class="privacy-note">
          由于网页、Android 与 PC 客户端版本和功能配置可能不同，我们实际收集的信息取决于您使用的端、版本及具体功能。以下加粗并带下划线的内容属于需要特别关注的敏感或重要个人信息。
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
                  收藏记录、歌单名称、歌单歌曲、歌单封面、第三方歌单链接或 ID、从网易云音乐或 QQ 音乐等来源返回的歌单名称和曲目信息。
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
                <td>
                  您主动提供的联系方式、反馈内容、截图或日志、相关账号信息、处理记录。
                </td>
                <td>回复您的咨询、处理投诉、定位故障、验证账号归属、维护服务安全。</td>
                <td>我们可能无法准确处理您的问题或反馈。</td>
              </tr>
            </tbody>
          </table>
        </div>

        <h3>个性化推荐和广告</h3>
        <p>
          目前我们不以第三方广告投放或商业画像为目的使用您的搜索、播放、浏览记录进行个性化广告推荐。若未来新增个性化推荐、个性化广告或类似功能，我们会在上线前更新本政策，并在产品内提供关闭或退出方式。
        </p>
      </section>

      <section id="permissions" class="panel privacy-section">
        <h2>二、权限调用说明</h2>
        <p>
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
      </section>

      <section id="storage-tech" class="panel privacy-section">
        <h2>三、Cookie、本地存储及同类技术</h2>
        <p>
          为保证网站和客户端 WebView 的基本功能，我们可能使用 Cookie、localStorage、sessionStorage 或同类技术保存必要信息，包括登录 token、用户基础资料、当前播放歌曲、播放队列、播放进度、播放模式、移动端下载提示关闭状态、管理员登录状态等。
        </p>
        <p>
          您可以通过浏览器或系统设置清除这些本地数据。清除后，您可能需要重新登录，播放队列、偏好设置或页面状态也可能被重置。
        </p>
      </section>

      <section id="third-party" class="panel privacy-section">
        <h2>四、委托处理、共享、转让和公开披露</h2>
        <p>
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
                <td>网易云音乐、QQ 音乐等歌单来源</td>
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

        <h3>转让和公开披露</h3>
        <p>
          除取得您的明确同意、依法需要、保护用户或公众重大合法权益、公司合并分立或资产转让等法律允许情形外，我们不会转让您的个人信息。发生合并、收购、清算或类似变更时，我们会要求新的持有方继续受本政策约束，否则将要求其重新取得您的授权。
        </p>
        <p>
          我们原则上不会公开披露您的个人信息；确需公开披露时，会取得您的单独同意或依据法律法规、行政机关、司法机关要求进行。
        </p>
      </section>

      <section id="protect" class="panel privacy-section">
        <h2>五、我们如何保护您的个人信息</h2>
        <p>
          我们会采取合理的技术和管理措施保护您的个人信息，包括访问权限控制、身份校验、敏感凭据加密或摘要存储、传输加密、日志审计、异常访问排查、备份和安全事件响应等。
        </p>
        <p>
          互联网环境并非绝对安全。如发生个人信息安全事件，我们将根据法律法规要求采取补救措施，并通过站内公告、弹窗、邮件或其他合理方式告知可能受影响的用户。
        </p>
      </section>

      <section id="retain" class="panel privacy-section">
        <h2>六、我们如何存储您的个人信息</h2>
        <p>
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
      </section>

      <section id="rights" class="panel privacy-section">
        <h2>七、您的权利</h2>
        <p>
          您有权依法访问、更正、补充、复制、删除您的个人信息，也可以撤回授权同意、关闭系统权限、注销或删除账号、要求解释个人信息处理规则。
        </p>
        <ul>
          <li>访问和更正：您可以在个人中心查看和修改部分账号资料；无法自行处理的，可通过联系方式向我们提出请求。</li>
          <li>删除和注销：您可以删除自己创建的歌单、上传内容等信息；如需注销账号或删除账号相关数据，可通过邮箱联系我们。</li>
          <li>撤回同意：您可以通过系统权限设置关闭通知、存储、悬浮窗等授权，也可以停止使用相关功能或清除本地数据。</li>
          <li>复制和导出：在法律法规要求范围内，您可以请求复制与您相关的个人信息，我们会在可行范围内提供。</li>
        </ul>
        <p class="privacy-note">
          为保护账号和数据安全，我们可能会在处理请求前验证您的身份，并要求您提供具体、可执行的请求内容。我们会在 15 个工作日内或法律法规要求的期限内回复；对依法无法响应的请求，我们会说明原因。
        </p>
      </section>

      <section id="children" class="panel privacy-section">
        <h2>八、未成年人个人信息保护</h2>
        <p>
          我们不会主动以未成年人为目标收集个人信息。未满 14 周岁的未成年人使用本服务，应事先取得父母或其他监护人的同意。
        </p>
        <p>
          如果监护人发现我们在未取得监护人同意的情况下收集了未成年人的个人信息，或希望查询、更正、删除未成年人信息，请通过本政策列明的方式联系我们，我们会依法处理。
        </p>
      </section>

      <section id="updates" class="panel privacy-section">
        <h2>九、隐私政策更新说明</h2>
        <p>
          我们可能根据产品功能、法律法规或运营情况更新本政策。更新后，我们会在本页面展示最新版本及生效日期。
        </p>
        <p>
          如果政策变更涉及处理目的、处理方式、个人信息种类、共享对象、用户权利行使方式等重大变化，我们会通过弹窗、公告、站内提示、邮件或其他显著方式通知您，并在法律法规要求时重新取得您的同意。
        </p>
      </section>

      <section id="contact" class="panel privacy-section privacy-section--last">
        <h2>十、如何联系我们</h2>
        <p>
          如果您对本隐私政策、个人信息处理规则或账号数据处理有疑问、意见、投诉或权利请求，可以通过以下方式联系我们：
        </p>
        <ul>
          <li>电子邮件：<a href="mailto:support@cnmsb.xin">support@cnmsb.xin</a></li>
          <li>官网：<a href="https://www.cnmsb.xin/" target="_blank" rel="noopener noreferrer">https://www.cnmsb.xin/</a></li>
          <li>QQ群：<a href="https://qm.qq.com/q/Q9HkDi6Ewk" target="_blank" rel="noopener noreferrer">Fantasy Network交流群</a></li>
        </ul>
        <p>
          我们会在 15 个工作日内或法律法规要求的期限内回复。为保障您的信息安全，我们可能会先核验您的身份，再处理查询、更正、删除、注销、复制或投诉请求。
        </p>
      </section>
    </main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'

onMounted(() => {
  window.scrollTo(0, 0)
})
</script>

<style scoped>
.privacy-shell {
  max-width: 1080px;
}

.panel {
  border-radius: var(--radius-lg);
  border: 1px solid var(--line);
  background: linear-gradient(145deg, rgba(139, 92, 246, 0.12), rgba(255, 255, 255, 0.04));
  box-shadow: var(--shadow);
  padding: clamp(20px, 3vw, 28px) clamp(18px, 3vw, 28px);
  margin-bottom: 16px;
}

.privacy-hero {
  padding: clamp(30px, 5vw, 46px) clamp(20px, 4vw, 36px);
}

.privacy-eyebrow {
  margin: 0 0 10px;
  color: var(--accent2);
  font-size: 0.8rem;
  font-weight: 800;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.privacy-title {
  margin: 0 0 12px;
  color: var(--text);
  font-size: clamp(2rem, 5vw, 3rem);
  line-height: 1.05;
}

.privacy-sub {
  max-width: 760px;
  margin: 0 0 14px;
  color: var(--muted);
  font-size: 1rem;
  line-height: 1.75;
}

.privacy-updated {
  margin: 0;
  color: rgba(255, 255, 255, 0.48);
  font-size: 0.88rem;
}

.privacy-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-top: 18px;
}

.privacy-meta span {
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  padding: 7px 12px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 0.84rem;
}

.privacy-section--last {
  margin-bottom: 0;
}

.privacy-section h2 {
  margin: 0 0 14px;
  color: var(--text);
  font-size: 1.18rem;
}

.privacy-section h3 {
  margin: 20px 0 10px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 1rem;
}

.privacy-section p {
  margin: 0 0 12px;
  color: var(--muted);
  font-size: 0.95rem;
  line-height: 1.8;
}

.privacy-section p:last-child {
  margin-bottom: 0;
}

.privacy-note {
  border-left: 3px solid rgba(165, 180, 252, 0.78);
  border-radius: 0 8px 8px 0;
  background: rgba(165, 180, 252, 0.08);
  padding: 12px 14px;
  color: rgba(255, 255, 255, 0.72) !important;
}

.privacy-section ul {
  margin: 0;
  padding: 0;
  list-style: none;
}

.privacy-section li {
  margin-bottom: 8px;
  padding: 10px 0 10px 12px;
  border-left: 3px solid rgba(34, 211, 238, 0.55);
  color: var(--muted);
  font-size: 0.94rem;
  line-height: 1.55;
}

.privacy-section a {
  color: var(--accent2);
  font-weight: 700;
  text-decoration: none;
}

.privacy-section a:hover {
  text-decoration: underline;
}

.privacy-toc h2 {
  margin: 0 0 16px;
  color: var(--text);
  font-size: 1.12rem;
}

.toc-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.toc-grid a {
  display: block;
  min-height: 44px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.05);
  padding: 12px 14px;
  color: rgba(255, 255, 255, 0.76);
  font-size: 0.92rem;
  line-height: 1.4;
  text-decoration: none;
  transition: border-color 0.2s ease, background 0.2s ease, color 0.2s ease;
}

.toc-grid a:hover {
  border-color: rgba(34, 211, 238, 0.42);
  background: rgba(34, 211, 238, 0.1);
  color: #dffbff;
}

.table-wrap {
  width: 100%;
  margin: 16px 0;
  overflow-x: auto;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  background: rgba(7, 6, 13, 0.34);
}

.info-table {
  width: 100%;
  min-width: 760px;
  border-collapse: collapse;
  color: var(--muted);
  font-size: 0.88rem;
  line-height: 1.65;
}

.info-table th,
.info-table td {
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  padding: 13px 14px;
  text-align: left;
  vertical-align: top;
}

.info-table th {
  background: rgba(255, 255, 255, 0.07);
  color: rgba(255, 255, 255, 0.86);
  font-weight: 800;
  white-space: nowrap;
}

.info-table tr:last-child td {
  border-bottom: 0;
}

.info-table td:first-child {
  color: rgba(255, 255, 255, 0.82);
  font-weight: 700;
}

.sensitive {
  color: #fef3c7;
  font-weight: 800;
  text-decoration: underline;
  text-underline-offset: 3px;
}

@media (max-width: 768px) {
  .privacy-shell {
    max-width: none;
  }

  .toc-grid {
    grid-template-columns: 1fr;
  }

  .privacy-meta span {
    width: 100%;
  }
}
</style>
