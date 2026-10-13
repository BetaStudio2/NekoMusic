/**
 * 操作日志明细（operation_log.detail）的解析。
 * ------------------------------------------------------------
 * 后端在处理器里比对新旧值后写入多行文本，每行一条「标签：原值 → 新值」（见后端 OperationAudit）。
 * 老记录、以及没有新旧值可比的删除类操作里，detail 是路径推断出的目标编号（如 `#148`）或空，
 * 一行都解析不出来就返回空数组，由调用方退回纯文本展示——不要把这类内容硬塞进「原值 → 新值」的三列表里。
 * 明细被截断时后端会补一行「…（共 N 项，其余省略）」，这行不是变更，跳过即可，不影响其余条目。
 */

const LINE = /^([^：]+)：(.*) → (.*)$/

/**
 * @param {string} detail
 * @returns {Array<{label: string, before: string, after: string}>} 解析失败返回空数组
 */
export function parseOperationDetail(detail) {
  if (typeof detail !== 'string' || !detail.trim()) return []
  const lines = detail.split('\n').filter((line) => line.trim())
  if (!lines.length) return []
  const changes = []
  for (const line of lines) {
    const matched = LINE.exec(line)
    if (matched) changes.push({ label: matched[1], before: matched[2], after: matched[3] })
  }
  return changes
}
