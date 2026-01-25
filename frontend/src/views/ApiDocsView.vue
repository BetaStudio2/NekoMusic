<template>
  <div class="api-docs-view">
    <div class="docs-container">
      <div class="docs-header">
        <h1 class="docs-title">📚 开发者文档</h1>
        <p class="docs-subtitle">Neko云音乐 API 接口文档</p>
      </div>
      <div class="docs-content" v-html="renderedMarkdown"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const renderedMarkdown = ref('')

// 简单的 Markdown 解析器
function parseMarkdown(markdown) {
  let html = markdown
  
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
  
  // 解析标题 (# ## ### ####)
  html = html.replace(/^#### (.+)$/gm, '<h4>$1</h4>')
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>')
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>')
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>')
  
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
  
  return html
}

onMounted(async () => {
  try {
    const response = await fetch('/api-docs.md')
    const markdown = await response.text()
    renderedMarkdown.value = parseMarkdown(markdown)
  } catch (error) {
    console.error('加载文档失败:', error)
    renderedMarkdown.value = '<p>文档加载失败，请稍后重试。</p>'
  }
})
</script>

<style scoped>
.api-docs-view {
  min-height: calc(100vh - 120px);
  padding: 40px 20px;
}

.docs-container {
  max-width: 1200px;
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

.docs-content {
  line-height: 1.8;
  color: #333;
}

.docs-content :deep(h1) {
  font-size: 2.2rem;
  color: #667eea;
  margin: 40px 0 20px 0;
  padding-bottom: 10px;
  border-bottom: 2px solid #eee;
}

.docs-content :deep(h2) {
  font-size: 1.8rem;
  color: #764ba2;
  margin: 35px 0 15px 0;
  padding-bottom: 8px;
  border-bottom: 1px solid #eee;
}

.docs-content :deep(h3) {
  font-size: 1.5rem;
  color: #667eea;
  margin: 30px 0 12px 0;
}

.docs-content :deep(h4) {
  font-size: 1.3rem;
  color: #764ba2;
  margin: 25px 0 10px 0;
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
}

.docs-content :deep(.code-block code) {
  background: transparent;
  padding: 0;
  color: #abb2bf;
  font-size: 0.9em;
  line-height: 1.6;
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
}
</style>