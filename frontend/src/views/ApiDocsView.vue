<template>
  <div class="api-docs-view">
    <div class="docs-container">
      <div class="docs-header">
        <h1 class="docs-title">📚 开发者文档</h1>
        <p class="docs-subtitle">Neko云音乐 API 接口文档</p>
      </div>
      <div class="docs-layout">
        <!-- 目录侧边栏 -->
        <div class="docs-sidebar" v-if="tocItems.length > 0">
          <div class="sidebar-header">📑 目录</div>
          <ul class="toc-list">
            <li v-for="(item, index) in tocItems" :key="index" 
                :class="['toc-item', `toc-level-${item.level}`]"
                @click="scrollToSection(item.id)">
              {{ item.text }}
            </li>
          </ul>
        </div>
        <!-- 文档内容 -->
        <div class="docs-content" v-html="renderedMarkdown" ref="contentRef"></div>
      </div>
    </div>
    <!-- 回到顶部按钮 -->
    <button class="back-to-top" @click="scrollToTop" v-show="showBackToTop">
      ↑
    </button>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'

const renderedMarkdown = ref('')
const contentRef = ref(null)
const tocItems = ref([])
const showBackToTop = ref(false)

// 简单的 Markdown 解析器
function parseMarkdown(markdown) {
  let html = markdown
  const headers = []
  
  // 转义 HTML 特殊字符
  html = html.replace(/&/g, '&amp;')
             .replace(/</g, '&lt;')
             .replace(/>/g, '&gt;')
  
  // 解析代码块 (```code```)
  html = html.replace(/```(\w*)\n([\s\S]*?)```/g, (match, lang, code) => {
    return `<pre class="code-block"><code class="language-${lang}">${code.trim()}</code></pre>`
  })
  
  // 解析行内代码 (`code`)
  html = html.replace(/`([^`]+)`/g, '<code class="inline-code">$1</code>')
  
  // 解析标题 (# ## ### ####) 并收集目录
  html = html.replace(/^#### (.+)$/gm, (match, text) => {
    const id = generateId(text)
    headers.push({ text, level: 4, id })
    return `<h4 id="${id}">${text}</h4>`
  })
  html = html.replace(/^### (.+)$/gm, (match, text) => {
    const id = generateId(text)
    headers.push({ text, level: 3, id })
    return `<h3 id="${id}">${text}</h3>`
  })
  html = html.replace(/^## (.+)$/gm, (match, text) => {
    const id = generateId(text)
    headers.push({ text, level: 2, id })
    return `<h2 id="${id}">${text}</h2>`
  })
  html = html.replace(/^# (.+)$/gm, (match, text) => {
    const id = generateId(text)
    headers.push({ text, level: 1, id })
    return `<h1 id="${id}">${text}</h1>`
  })
  
  // 解析粗体 (**text**)
  html = html.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  
  // 解析斜体 (*text*)
  html = html.replace(/\*([^*]+)\*/g, '<em>$1</em>')
  
  // 解析链接 [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
  
  // 解析表格
  html = html.replace(/\|(.+)\|/g, (match) => {
    const cells = match.split('|').filter(cell => cell.trim())
    if (cells.length > 0) {
      const cellHtml = cells.map(cell => `<td>${cell.trim()}</td>`).join('')
      return `<tr>${cellHtml}</tr>`
    }
    return match
  })
  
  // 将连续的 tr 包裹在 table 中
  html = html.replace(/(<tr>[\s\S]*?<\/tr>)+/g, '<table>$&</table>')
  
  // 解析列表
  html = html.replace(/^\- (.+)$/gm, '<li>$1</li>')
  html = html.replace(/(<li>.*<\/li>)/g, '<ul>$1</ul>')
  // 合并相邻的 ul
  html = html.replace(/<\/ul>\s*<ul>/g, '')
  
  // 解析水平线
  html = html.replace(/^---$/gm, '<hr>')
  
  // 解析段落 (双换行)
  html = html.replace(/\n\n/g, '</p><p>')
  
  // 包裹所有内容在 p 标签中
  html = `<p>${html}</p>`
  
  // 清理空的 p 标签
  html = html.replace(/<p>\s*<\/p>/g, '')
  
  // 清理 pre 和 code 周围的 p 标签
  html = html.replace(/<p>\s*(<pre[\s\S]*?<\/pre>)\s*<\/p>/g, '$1')
  html = html.replace(/<p>\s*(<h[1-6]>[\s\S]*?<\/h[1-6]>)\s*<\/p>/g, '$1')
  html = html.replace(/<p>\s*(<table[\s\S]*?<\/table>)\s*<\/p>/g, '$1')
  html = html.replace(/<p>\s*(<ul[\s\S]*?<\/ul>)\s*<\/p>/g, '$1')
  html = html.replace(/<p>\s*(<hr>)\s*<\/p>/g, '$1')
  
  tocItems.value = headers
  return html
}

// 生成唯一ID
function generateId(text) {
  return text.toLowerCase()
    .replace(/[^\w\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-')
}

// 滚动到指定章节
function scrollToSection(id) {
  const element = document.getElementById(id)
  if (element) {
    const offset = 20
    const elementPosition = element.getBoundingClientRect().top
    const offsetPosition = elementPosition + window.pageYOffset - offset
    
    window.scrollTo({
      top: offsetPosition,
      behavior: 'smooth'
    })
  }
}

// 滚动到顶部
function scrollToTop() {
  window.scrollTo({
    top: 0,
    behavior: 'smooth'
  })
}

// 监听滚动事件，显示/隐藏回到顶部按钮
function handleScroll() {
  showBackToTop.value = window.pageYOffset > 300
}

onMounted(async () => {
  try {
    const response = await fetch('/api-docs.md')
    const markdown = await response.text()
    renderedMarkdown.value = parseMarkdown(markdown)
    
    // 添加代码复制功能
    setTimeout(() => {
      addCopyButtons()
    }, 100)
  } catch (error) {
    console.error('加载文档失败:', error)
    renderedMarkdown.value = '<p>文档加载失败，请稍后重试。</p>'
  }
  
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})

// 添加代码复制按钮
function addCopyButtons() {
  const codeBlocks = document.querySelectorAll('.code-block')
  codeBlocks.forEach((block, index) => {
    const button = document.createElement('button')
    button.className = 'copy-button'
    button.textContent = '复制'
    button.onclick = async () => {
      const code = block.querySelector('code').textContent
      try {
        await navigator.clipboard.writeText(code)
        button.textContent = '已复制!'
        button.classList.add('copied')
        setTimeout(() => {
          button.textContent = '复制'
          button.classList.remove('copied')
        }, 2000)
      } catch (err) {
        console.error('复制失败:', err)
      }
    }
    block.appendChild(button)
  })
}
</script>

<style scoped>
.api-docs-view {
  min-height: calc(100vh - 120px);
  padding: 40px 20px;
}

.docs-container {
  max-width: 1400px;
  margin: 0 auto;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  padding: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.docs-header {
  text-align: center;
  margin-bottom: 40px;
  padding-bottom: 20px;
  border-bottom: 3px solid #667eea;
}

.docs-title {
  font-size: 2.5rem;
  color: #667eea;
  margin-bottom: 10px;
  font-weight: bold;
}

.docs-subtitle {
  font-size: 1.2rem;
  color: #666;
}

.docs-layout {
  display: flex;
  gap: 40px;
  position: relative;
}

.docs-sidebar {
  width: 280px;
  flex-shrink: 0;
  position: sticky;
  top: 20px;
  max-height: calc(100vh - 40px);
  overflow-y: auto;
  background: #f8f9fa;
  border-radius: 12px;
  padding: 20px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.sidebar-header {
  font-size: 1.2rem;
  font-weight: bold;
  color: #667eea;
  margin-bottom: 15px;
  padding-bottom: 10px;
  border-bottom: 2px solid #e0e0e0;
}

.toc-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.toc-item {
  padding: 8px 12px;
  margin: 4px 0;
  cursor: pointer;
  border-radius: 6px;
  transition: all 0.3s ease;
  color: #555;
  font-size: 0.95rem;
}

.toc-item:hover {
  background: #667eea;
  color: white;
  transform: translateX(5px);
}

.toc-level-1 {
  font-weight: bold;
  font-size: 1.1rem;
}

.toc-level-2 {
  padding-left: 20px;
}

.toc-level-3 {
  padding-left: 35px;
  font-size: 0.9rem;
}

.toc-level-4 {
  padding-left: 50px;
  font-size: 0.85rem;
}

.docs-content {
  flex: 1;
  line-height: 1.8;
  color: #333;
  min-width: 0;
}

.docs-content :deep(h1) {
  font-size: 2.2rem;
  color: #667eea;
  margin: 40px 0 20px 0;
  padding-bottom: 10px;
  border-bottom: 2px solid #eee;
  scroll-margin-top: 20px;
}

.docs-content :deep(h2) {
  font-size: 1.8rem;
  color: #764ba2;
  margin: 35px 0 15px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
  scroll-margin-top: 20px;
}

.docs-content :deep(h3) {
  font-size: 1.5rem;
  color: #667eea;
  margin: 30px 0 12px 0;
  scroll-margin-top: 20px;
}

.docs-content :deep(h4) {
  font-size: 1.3rem;
  color: #764ba2;
  margin: 25px 0 10px 0;
  scroll-margin-top: 20px;
}

.docs-content :deep(p) {
  margin: 15px 0;
  font-size: 1rem;
}

.docs-content :deep(code) {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
  color: #e83e8c;
}

.docs-content :deep(.inline-code) {
  background: #f5f5f5;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 0.9em;
  color: #e83e8c;
}

.docs-content :deep(pre) {
  background: #282c34;
  padding: 20px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 20px 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  position: relative;
}

.docs-content :deep(pre code) {
  background: transparent;
  padding: 0;
  color: #abb2bf;
  font-size: 0.9em;
  line-height: 1.6;
}

.docs-content :deep(.code-block) {
  background: #282c34;
  padding: 20px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 20px 0;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  position: relative;
}

.docs-content :deep(.code-block code) {
  background: transparent;
  padding: 0;
  color: #abb2bf;
  font-size: 0.9em;
  line-height: 1.6;
}

.docs-content :deep(.copy-button) {
  position: absolute;
  top: 10px;
  right: 10px;
  background: rgba(255, 255, 255, 0.1);
  color: #abb2bf;
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 6px 12px;
  border-radius: 4px;
  cursor: pointer;
  font-size: 0.85rem;
  transition: all 0.3s ease;
}

.docs-content :deep(.copy-button:hover) {
  background: rgba(255, 255, 255, 0.2);
  color: white;
}

.docs-content :deep(.copy-button.copied) {
  background: #4caf50;
  color: white;
  border-color: #4caf50;
}

.docs-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 20px 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.docs-content :deep(th),
.docs-content :deep(td) {
  padding: 12px 15px;
  text-align: left;
  border: 1px solid #e0e0e0;
}

.docs-content :deep(th) {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-weight: bold;
}

.docs-content :deep(tr:nth-child(even)) {
  background: #f8f9fa;
}

.docs-content :deep(tr:hover) {
  background: #e9ecef;
}

.docs-content :deep(ul) {
  margin: 15px 0;
  padding-left: 30px;
}

.docs-content :deep(li) {
  margin: 8px 0;
  font-size: 1rem;
}

.docs-content :deep(hr) {
  border: none;
  height: 2px;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  margin: 30px 0;
  opacity: 0.5;
}

.docs-content :deep(a) {
  color: #667eea;
  text-decoration: none;
  font-weight: 500;
  transition: color 0.3s ease;
}

.docs-content :deep(a:hover) {
  color: #764ba2;
  text-decoration: underline;
}

.docs-content :deep(strong) {
  color: #667eea;
  font-weight: bold;
}

.back-to-top {
  position: fixed;
  bottom: 40px;
  right: 40px;
  width: 50px;
  height: 50px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  font-size: 1.5rem;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(102, 126, 234, 0.4);
  transition: all 0.3s ease;
  z-index: 1000;
}

.back-to-top:hover {
  transform: translateY(-5px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.6);
}

@media (max-width: 1024px) {
  .docs-layout {
    flex-direction: column;
  }
  
  .docs-sidebar {
    width: 100%;
    position: static;
    max-height: none;
    margin-bottom: 30px;
  }
}

@media (max-width: 768px) {
  .docs-container {
    padding: 20px;
  }
  
  .docs-title {
    font-size: 1.8rem;
  }
  
  .docs-subtitle {
    font-size: 1rem;
  }
  
  .docs-content :deep(h1) {
    font-size: 1.6rem;
  }
  
  .docs-content :deep(h2) {
    font-size: 1.4rem;
  }
  
  .docs-content :deep(h3) {
    font-size: 1.2rem;
  }
  
  .docs-content :deep(h4) {
    font-size: 1.1rem;
  }
  
  .docs-content :deep(table) {
    font-size: 0.9em;
  }
  
  .docs-content :deep(th),
  .docs-content :deep(td) {
    padding: 8px 10px;
  }
  
  .back-to-top {
    bottom: 20px;
    right: 20px;
    width: 45px;
    height: 45px;
    font-size: 1.3rem;
  }
}
</style>