// 防重放挑战解题器（web 端）
// ------------------------------------------------------------
// 与后端约定一致：找一个十进制计数器 counter，使 SHA-256(`${seed}:${counter}`) 的
// 前导零比特数 ≥ difficulty，把 counter 的十进制字符串作为 proof 回传。
// 服务端只验一次哈希，客户端要试 2^difficulty 量级的次数——这种成本不对称正是该
// 方案的基础，所以这里要够快。
//
// 消息很短（seed + ':' + 计数器必然落在一个 512 位块内），因此这里自带一份
// 「单块 SHA-256」，用复用的 TypedArray 做无分配计算：比 crypto.subtle 的异步
// digest 快一个量级（后者每次调用都要跨线程并新建 Promise，几万次就拖到秒级）。
const K = new Uint32Array([
  0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5,
  0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
  0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3,
  0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
  0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc,
  0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
  0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7,
  0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
  0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13,
  0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
  0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3,
  0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
  0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5,
  0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
  0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208,
  0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
])

const INIT = new Uint32Array([
  0x6a09e667, 0xbb67ae85, 0x3c6ef372, 0xa54ff53a,
  0x510e527f, 0x9b05688c, 0x1f83d9ab, 0x5be0cd19
])

/** 单块运算要求消息（含 ':' 与计数器）不超过 55 字节 */
const MAX_MESSAGE_BYTES = 55
/** 难度上限：服务端远低于此值，这里只是防止异常输入把页面卡死 */
const MAX_DIFFICULTY_BITS = 64

const encoder = new TextEncoder()

function rotr(value, bits) {
  return (value >>> bits) | (value << (32 - bits))
}

function ch(x, y, z) {
  return (x & y) ^ (~x & z)
}

function maj(x, y, z) {
  return (x & y) ^ (x & z) ^ (y & z)
}

function sigma0(x) {
  return rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3)
}

function sigma1(x) {
  return rotr(x, 17) ^ rotr(x, 19) ^ (x >>> 10)
}

function bigSigma0(x) {
  return rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22)
}

function bigSigma1(x) {
  return rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25)
}

/** 摘要（大端 8 个字）的前导零比特数是否达到 bits。 */
function meetsBits(digest, bits) {
  let remaining = bits
  for (let i = 0; i < digest.length && remaining > 0; i += 1) {
    if (remaining >= 32) {
      if (digest[i] !== 0) return false
      remaining -= 32
      continue
    }
    return digest[i] >>> (32 - remaining) === 0
  }
  return remaining <= 0
}

/**
 * 解出 seed 对应的 proof。
 *
 * @param {string} seed 服务端下发的随机串
 * @param {number} difficulty 目标前导零比特数（服务端下发，勿写死）
 * @returns {string} 十进制计数器，作为 proof 回传
 */
export function solveProof(seed, difficulty) {
  const bits = Math.floor(Number(difficulty))
  if (!Number.isFinite(bits) || bits < 0 || bits > MAX_DIFFICULTY_BITS) {
    throw new Error(`挑战难度非法：${difficulty}`)
  }

  const prefix = encoder.encode(`${seed}:`)
  if (prefix.length > MAX_MESSAGE_BYTES) {
    throw new Error('挑战 seed 过长')
  }

  const block = new Uint8Array(64)
  block.set(prefix)
  const schedule = new Uint32Array(16)
  const digest = new Uint32Array(8)

  for (let counter = 0; ; counter += 1) {
    const digits = String(counter)
    const length = prefix.length + digits.length
    if (length > MAX_MESSAGE_BYTES) {
      throw new Error('挑战计数器过长')
    }

    // 复用同一个块：只覆盖计数器数字，其余补零后写填充与位长度
    block.fill(0, prefix.length, 64)
    for (let i = 0; i < digits.length; i += 1) {
      block[prefix.length + i] = digits.charCodeAt(i)
    }
    block[length] = 0x80
    const messageBits = length * 8
    block[62] = (messageBits >>> 8) & 0xff
    block[63] = messageBits & 0xff

    for (let i = 0; i < 16; i += 1) {
      const offset = i * 4
      schedule[i] =
        (block[offset] << 24) |
        (block[offset + 1] << 16) |
        (block[offset + 2] << 8) |
        block[offset + 3]
    }

    let a = INIT[0]
    let b = INIT[1]
    let c = INIT[2]
    let d = INIT[3]
    let e = INIT[4]
    let f = INIT[5]
    let g = INIT[6]
    let h = INIT[7]

    for (let i = 0; i < 64; i += 1) {
      const w = i < 16
        ? schedule[i]
        : (schedule[i & 15] =
            (sigma1(schedule[(i - 2) & 15]) +
              schedule[(i - 7) & 15] +
              sigma0(schedule[(i - 15) & 15]) +
              schedule[i & 15]) | 0)
      const t1 = (h + bigSigma1(e) + ch(e, f, g) + K[i] + w) | 0
      const t2 = (bigSigma0(a) + maj(a, b, c)) | 0
      h = g
      g = f
      f = e
      e = (d + t1) | 0
      d = c
      c = b
      b = a
      a = (t1 + t2) | 0
    }

    digest[0] = (INIT[0] + a) | 0
    digest[1] = (INIT[1] + b) | 0
    digest[2] = (INIT[2] + c) | 0
    digest[3] = (INIT[3] + d) | 0
    digest[4] = (INIT[4] + e) | 0
    digest[5] = (INIT[5] + f) | 0
    digest[6] = (INIT[6] + g) | 0
    digest[7] = (INIT[7] + h) | 0

    if (meetsBits(digest, bits)) return String(counter)
  }
}
