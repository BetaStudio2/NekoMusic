<template>
  <div class="upload-view">
    <transition name="upload-notice">
      <div
        v-if="uploadNotice.visible"
        class="upload-result-notice"
        :class="`upload-result-notice--${uploadNotice.type}`"
        role="status"
        aria-live="polite"
      >
        {{ uploadNotice.message }}
      </div>
    </transition>

    <div class="upload-container">
      <!-- 左侧封面 -->
      <div class="cover-side">
        <div 
              class="cover-upload" 
              :class="{ 'has-cover': coverFile, 'drag-over': isCoverDragging }"
              @click="selectCoverFile"
              @dragover.prevent="isCoverDragging = true"
              @dragleave.prevent="isCoverDragging = false"
              @drop.prevent="handleCoverDrop"
            >
          <input
            ref="coverFileInput"
            type="file"
            accept="image/*"
            @change="handleCoverFileChange"
            style="display: none"
          />
          <div v-if="!coverFile" class="cover-placeholder">
            <svg width="64" height="64" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
              <rect x="3" y="3" width="18" height="18" rx="2"></rect>
              <circle cx="8.5" cy="8.5" r="1.5"></circle>
              <polyline points="21 15 16 10 5 21"></polyline>
            </svg>
            <p>上传封面</p>
            <span>建议 800x800</span>
          </div>
          <div v-else class="cover-preview">
            <img :src="coverPreview" alt="封面" />
            <div class="cover-overlay">
              <button type="button" @click.stop="removeCoverFile" class="change-btn">更换</button>
            </div>
          </div>
        </div>

        <!-- 歌词文件 -->
        <div class="lyrics-upload-section">
          <label class="side-label">歌词文件</label>
          <div 
            class="file-upload lyrics-upload" 
            :class="{ 'has-file': lyricsFile, 'drag-over': isLyricsDragging }"
            @click="selectLyricsFile"
            @dragover.prevent="isLyricsDragging = true"
            @dragleave.prevent="isLyricsDragging = false"
            @drop.prevent="handleLyricsFileDrop"
          >
            <input
              ref="lyricsFileInput"
              type="file"
              accept=".lrc"
              @change="handleLyricsFileChange"
              style="display: none"
            />
            <div v-if="!lyricsFile" class="file-placeholder">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
                <line x1="16" y1="13" x2="8" y2="13"></line>
                <line x1="16" y1="17" x2="8" y2="17"></line>
                <polyline points="10 9 9 9 8 9"></polyline>
              </svg>
              <span>选择歌词文件</span>
            </div>
            <div v-else class="file-info">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                <polyline points="14 2 14 8 20 8"></polyline>
              </svg>
              <span>{{ lyricsFile.name }}</span>
              <button type="button" @click.stop="removeLyricsFile" class="remove-btn"></button>
            </div>
          </div>

          <!-- 双语歌词格式说明 -->
          <div class="lyrics-format-guide">
            <h4>双语歌词格式说明</h4>
            <p>系统支持双语歌词，格式如下：</p>
            <div class="lyrics-example">
              <div class="example-title">示例：</div>
              <pre class="example-code">[00:00.389] ざこざこざこざこ くだらない存在 あわれだね
{"杂鱼杂鱼杂鱼杂鱼 无聊的存在 真可怜呢"}

[00:07.546] ざこざこざこざこ ざこのざこ攻撃 効かないよ
{"杂鱼杂鱼杂鱼杂鱼 杂鱼的杂鱼攻击 根本没用喔"}

[00:14.225] ざぁこ
{"杂~鱼~"}</pre>
            </div>
            <div class="lyrics-tips">
              <p><strong>格式规则：</strong></p>
              <ul>
                <li>第一行：时间戳 + 原文歌词</li>
                <li>第二行：JSON格式的翻译 <code>{"翻译内容"}</code></li>
                <li>如果没有翻译，可以只保留原文行</li>
                <li>时间戳格式：<code>[分:秒.毫秒]</code></li>
              </ul>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧表单 -->
      <div class="form-side">
        <h2 class="page-title">上传音乐</h2>

        <form @submit.prevent="handleSubmit" class="upload-form">
          <!-- 音乐文件 -->
          <div class="form-group">
            <div 
              class="file-upload" 
              :class="{ 'has-file': musicFile, 'drag-over': isDragging }"
              @click="selectMusicFile"
              @dragover.prevent="isDragging = true"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="handleDrop"
            >
              <input
                ref="musicFileInput"
                type="file"
                accept="audio/*"
                @change="handleMusicFileChange"
                style="display: none"
              />
              <div v-if="!musicFile" class="file-placeholder">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
                  <polyline points="17 8 12 3 7 8"></polyline>
                  <line x1="12" y1="3" x2="12" y2="15"></line>
                </svg>
                <span>选择音频文件</span>
              </div>
              <div v-else class="file-info">
                <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                  <path d="M9 18V5l12-2v13"></path>
                  <circle cx="6" cy="18" r="3"></circle>
                  <circle cx="18" cy="16" r="3"></circle>
                </svg>
                <span>{{ musicFile.name }}</span>
                <button type="button" @click.stop="removeMusicFile" class="remove-btn"></button>
              </div>
            </div>
          </div>

          <!-- 歌曲标题 -->
          <div class="form-group">
            <label class="label">歌曲标题 <span class="required">*</span></label>
            <input
              v-model="formData.title"
              type="text"
              class="input"
              placeholder="输入歌曲标题"
              required
            />
          </div>

          <!-- 歌手 -->
          <div class="form-group">
            <label class="label">歌手 <span class="required">*</span></label>
            <input
              v-model="formData.artist"
              type="text"
              class="input"
              placeholder="输入歌手名称"
              required
            />
          </div>

          <!-- 专辑 -->
          <div class="form-group">
            <label class="label">专辑</label>
            <input
              v-model="formData.album"
              type="text"
              class="input"
              placeholder="输入专辑名称"
            />
          </div>

          <!-- 标签 -->
          <div class="form-group">
            <label class="label">标签</label>
            <input
              v-model="formData.tags"
              type="text"
              class="input"
              placeholder="输入标签，多个标签用逗号分隔"
            />
          </div>

          <!-- 语言 -->
          <div class="form-group">
            <label class="label">语言 <span class="required">*</span></label>
            <select v-model="formData.language" class="input" required>
              <option value="" disabled>请选择语言</option>
              <option value="中文">中文</option>
              <option value="粤语">粤语</option>
              <option value="上海语">上海语</option>
              <option value="英文">英文</option>
              <option value="日语">日语</option>
              <option value="韩语">韩语</option>
              <option value="法语">法语</option>
              <option value="德语">德语</option>
              <option value="俄语">俄语</option>
              <option value="纯音乐">纯音乐</option>
            </select>
          </div>

          <!-- 时长（可编辑，支持自动/手动解析） -->
          <div class="form-group">
            <label class="label">音乐时长（秒）</label>
            <div class="duration-input-group">
              <input
                v-model.number="formData.duration"
                type="number"
                class="input duration-input"
                min="0"
                step="1"
                placeholder="输入时长或点击解析"
                :disabled="parsingDuration"
              />
              <button
                type="button"
                class="parse-duration-btn"
                @click="parseDuration"
                :disabled="!musicFile || parsingDuration"
                :title="!musicFile ? '请先选择音乐文件' : '解析音频时长'"
              >
                {{ parsingDuration ? '解析中...' : '解析时长' }}
              </button>
            </div>
            <div class="input-hint">
              当前时长: {{ formatDuration(formData.duration) }}
              <span v-if="formData.duration === 0" class="warning-text">⚠️ 请填写音乐时长</span>
            </div>
          </div>

          <!-- 提交按钮 -->
          <button type="submit" class="submit-btn" :disabled="uploading">
            <span v-if="!uploading">发布音乐</span>
            <span v-else>上传中 {{ uploadProgress }}%</span>
          </button>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'

const toast = useToast()
const router = useRouter()

const musicFileInput = ref(null)
const coverFileInput = ref(null)
const lyricsFileInput = ref(null)

const musicFile = ref(null)
const coverFile = ref(null)
const coverPreview = ref(null)
const lyricsFile = ref(null)

const formData = ref({
  title: '',
  artist: '',
  album: '',
  tags: '',
  language: '',
  duration: 0
})

const uploading = ref(false)
const uploadProgress = ref(0)
const parsingDuration = ref(false)
const uploadNotice = ref({
  visible: false,
  type: 'error',
  message: ''
})
let uploadNoticeTimer = null

const isDragging = ref(false)
const isCoverDragging = ref(false)
const isLyricsDragging = ref(false)

const selectMusicFile = () => {
  musicFileInput.value.click()
}

const selectCoverFile = () => {
  coverFileInput.value.click()
}

// 手动解析时长
const parseDuration = async () => {
  if (!musicFile.value) {
    toast.error('请先选择音乐文件')
    return
  }

  if (parsingDuration.value) {
    return
  }

  parsingDuration.value = true
  console.log('========== 开始手动解析时长 ==========')
  console.log('文件名:', musicFile.value.name)
  console.log('文件类型:', musicFile.value.type)
  console.log('文件大小:', musicFile.value.size, 'bytes')

  try {
    const audio = new Audio()
    const objectUrl = URL.createObjectURL(musicFile.value)
    audio.src = objectUrl

    let metadataLoaded = false
    let canPlayLoaded = false

    // 设置超时（10秒）
    const timeout = setTimeout(() => {
      console.warn('[手动时长解析] 10秒超时')
      console.log('[手动时长解析] audio.readyState:', audio.readyState)
      console.log('[手动时长解析] audio.duration:', audio.duration)
      if (!metadataLoaded && !canPlayLoaded) {
        URL.revokeObjectURL(objectUrl)
        toast.error('解析超时，请尝试手动输入时长')
      }
      parsingDuration.value = false
    }, 10000)

    // 监听多个事件
    audio.onloadedmetadata = () => {
      clearTimeout(timeout)
      metadataLoaded = true
      console.log('[手动时长解析] onloadedmetadata 触发')
      console.log('[手动时长解析] audio.duration:', audio.duration)
      checkAndSaveDurationManual(audio, objectUrl)
    }

    audio.oncanplay = () => {
      if (!metadataLoaded && !canPlayLoaded) {
        clearTimeout(timeout)
        canPlayLoaded = true
        console.log('[手动时长解析] oncanplay 触发')
        console.log('[手动时长解析] audio.duration:', audio.duration)
        checkAndSaveDurationManual(audio, objectUrl)
      }
    }

    audio.oncanplaythrough = () => {
      if (!metadataLoaded && !canPlayLoaded) {
        clearTimeout(timeout)
        canPlayLoaded = true
        console.log('[手动时长解析] oncanplaythrough 触发')
        console.log('[手动时长解析] audio.duration:', audio.duration)
        checkAndSaveDurationManual(audio, objectUrl)
      }
    }

    audio.onerror = (error) => {
      clearTimeout(timeout)
      console.error('[手动时长解析] 音频加载错误')
      console.error('[手动时长解析] audio.error:', audio.error)
      console.error('[手动时长解析] audio.error.code:', audio.error?.code)
      console.error('[手动时长解析] audio.error.message:', audio.error?.message)
      URL.revokeObjectURL(objectUrl)
      toast.error('无法解析音频时长，请手动输入')
      parsingDuration.value = false
    }

    audio.onloadeddata = () => {
      console.log('[手动时长解析] onloadeddata 触发')
      console.log('[手动时长解析] audio.duration:', audio.duration)
    }

  } catch (error) {
    console.error('[手动时长解析] 解析出错:', error)
    toast.error('解析失败: ' + error.message)
    parsingDuration.value = false
  }
}

// 检查并保存时长（手动版本）
const checkAndSaveDurationManual = (audio, objectUrl) => {
  console.log('[手动时长解析] 开始检查时长...')
  console.log('[手动时长解析] audio.duration:', audio.duration)
  console.log('[手动时长解析] audio.readyState:', audio.readyState)

  // 尝试多次读取duration
  const checkDuration = (attempts = 0) => {
    const duration = audio.duration
    console.log(`[手动时长解析] 尝试 ${attempts + 1}: duration = ${duration}`)

    if (duration && duration > 0 && duration !== Infinity && !isNaN(duration)) {
      formData.value.duration = Math.round(duration)
      console.log('[手动时长解析] ✓✓✓ 解析成功:', duration, '秒')
      console.log('[手动时长解析] ✓✓✓ 格式化时长:', formatDuration(formData.value.duration))
      console.log('[手动时长解析] ✓✓✓ 已保存到formData.duration')
      URL.revokeObjectURL(objectUrl)
      toast.success(`时长解析成功: ${formatDuration(formData.value.duration)}`)
      parsingDuration.value = false
      return true
    }

    // 如果还没成功，继续尝试
    if (attempts < 10) {
      setTimeout(() => checkDuration(attempts + 1), 100)
    } else {
      console.warn('[手动时长解析] ✗✗✗ 多次尝试后仍然无法获取有效时长')
      console.warn('[手动时长解析] 最后的duration值:', duration)
      URL.revokeObjectURL(objectUrl)
      toast.error('无法解析音频时长，请手动输入')
      parsingDuration.value = false
    }
    return false
  }

  checkDuration()
}

const handleMusicFileChange = async (event) => {
  const file = event.target.files[0]
  if (file) {
    // 检查文件格式
    const fileName = file.name.toLowerCase()
    const fileExtension = fileName.split('.').pop()
    const isValidFormat = ['.mp3', '.flac', '.wav'].includes('.' + fileExtension)

    if (!isValidFormat) {
      toast.error('请选择 MP3、FLAC 或 WAV 格式的音乐文件')
      event.target.value = ''
      return
    }

    musicFile.value = file

    console.log('========== 音乐文件解析开始 ==========')
    console.log('文件名:', file.name)
    console.log('文件大小:', (file.size / 1024 / 1024).toFixed(2), 'MB')
    console.log('文件类型:', fileExtension.toUpperCase())

    // 立即解析元数据
    await parseMetadata(fileExtension, file)
  }
}

// 解析MP3文件的元数据
const parseMP3Metadata = async (file) => {
  console.log('[MP3解析] 开始解析MP3文件元数据')
  try {
    const arrayBuffer = await file.arrayBuffer()
    const dataView = new DataView(arrayBuffer)

    // 检查文件头
    const header = dataView.getString(0, 3)
    console.log('[MP3解析] 文件头:', header)

    if (header === 'ID3') {
      const size = dataView.getUint32(6)
      const headerSize = 10
      let offset = headerSize

      const metadata = {
        title: '',
        artist: '',
        album: '',
        cover: null
      }

      while (offset < headerSize + size) {
        const frameId = dataView.getString(offset, 4)
        const frameSize = dataView.getUint32(offset + 4)

        if (frameSize === 0) break

        const frameDataOffset = offset + 10
        const frameDataSize = frameSize

        if (frameId === 'TIT2') {
          metadata.title = dataView.decodeTextFrame(frameDataOffset, frameDataSize)
        } else if (frameId === 'TPE1') {
          metadata.artist = dataView.decodeTextFrame(frameDataOffset, frameDataSize)
        } else if (frameId === 'TALB') {
          metadata.album = dataView.decodeTextFrame(frameDataOffset, frameDataSize)
        } else if (frameId === 'APIC') {
          // 解析封面图片
          let currentOffset = frameDataOffset
          const textEncoding = dataView.getUint8(currentOffset)
          currentOffset += 1

          // 读取MIME类型
          let mimeTypeEnd = currentOffset
          while (dataView.getUint8(mimeTypeEnd) !== 0) {
            mimeTypeEnd++
          }
          const mimeType = dataView.getString(currentOffset, mimeTypeEnd - currentOffset)
          currentOffset = mimeTypeEnd + 1

          // 跳过图片类型
          currentOffset += 1

          // 读取描述
          let descEnd = currentOffset
          while (dataView.getUint8(descEnd) !== 0) {
            descEnd++
          }
          currentOffset = descEnd + 1

          // 读取图片数据
          const imageSize = frameDataSize - (currentOffset - frameDataOffset)
          if (imageSize > 0) {
            const imageData = new Uint8Array(arrayBuffer, currentOffset, imageSize)
            metadata.cover = new Blob([imageData], { type: mimeType })
          }
        }

        offset += 10 + frameSize
      }

      // 自动填充表单
      if (metadata.title) formData.value.title = metadata.title
      if (metadata.artist) formData.value.artist = metadata.artist
      if (metadata.album) formData.value.album = metadata.album
      if (metadata.cover) {
        coverFile.value = new File([metadata.cover], 'cover.jpg', { type: metadata.cover.type })
        coverPreview.value = URL.createObjectURL(metadata.cover)
      }

      console.log('[MP3解析] 解析结果:', {
        title: metadata.title,
        artist: metadata.artist,
        album: metadata.album,
        hasCover: !!metadata.cover
      })
      toast.success('已自动解析MP3文件信息')
    } else {
      console.log('[MP3解析] 文件不是ID3格式，跳过元数据解析')
    }
  } catch (error) {
    console.error('[MP3解析] 解析失败:', error)
  }
}

// 解析FLAC文件的元数据
const parseFlacMetadata = async (file) => {
  console.log('[FLAC解析] 开始解析FLAC文件元数据')
  try {
    const arrayBuffer = await file.arrayBuffer()
    const dataView = new DataView(arrayBuffer)
    const textDecoder = new TextDecoder('utf-8')

    // 检查FLAC文件头
    const header = String.fromCharCode(
      dataView.getUint8(0),
      dataView.getUint8(1),
      dataView.getUint8(2),
      dataView.getUint8(3)
    )

    console.log('[FLAC解析] 文件头:', header)

    if (header !== 'fLaC') {
      console.log('[FLAC解析] 不是有效的FLAC文件')
      toast.warning('该FLAC文件不包含元数据标签')
      return
    }

    let offset = 4
    const metadata = {
      title: '',
      artist: '',
      album: '',
      cover: null
    }

    const maxBlocks = 100

    while (offset < arrayBuffer.byteLength - 4) {
      // 读取块头
      const blockHeader = dataView.getUint8(offset)
      const blockType = blockHeader & 0x7F
      const isLast = (blockHeader & 0x80) !== 0

      // 读取块大小（3字节，大端序）
      const blockSize = (dataView.getUint8(offset + 1) << 16) | 
                        (dataView.getUint8(offset + 2) << 8) | 
                        dataView.getUint8(offset + 3)

      offset += 4

      // 检查边界
      if (offset + blockSize > arrayBuffer.byteLength) {
        console.warn('FLAC块大小超出文件范围，停止解析')
        break
      }

      // VORBIS_COMMENT块
      if (blockType === 4 && blockSize > 8) {
        try {
          let dataOffset = 0
          
          // 读取vendor length（小端序）
          const vendorLength = dataView.getUint32(offset + dataOffset, true)
          dataOffset += 4 + vendorLength

          // 检查边界
          if (dataOffset + 4 > blockSize) break

          // 读取comments count（小端序）
          const commentsCount = dataView.getUint32(offset + dataOffset, true)
          dataOffset += 4

          // 解析每个comment
          for (let i = 0; i < commentsCount && dataOffset + 4 <= blockSize; i++) {
            const commentLength = dataView.getUint32(offset + dataOffset, true)
            dataOffset += 4

            if (dataOffset + commentLength > blockSize) break

            const commentBytes = new Uint8Array(arrayBuffer, offset + dataOffset, commentLength)
            const comment = textDecoder.decode(commentBytes)
            dataOffset += commentLength

            const equalIndex = comment.indexOf('=')
            if (equalIndex !== -1) {
              const field = comment.substring(0, equalIndex).toUpperCase()
              const value = comment.substring(equalIndex + 1)

              if (field === 'TITLE') metadata.title = value
              else if (field === 'ARTIST') metadata.artist = value
              else if (field === 'ALBUM') metadata.album = value
            }
          }
        } catch (e) {
          console.warn('解析VORBIS_COMMENT块失败:', e)
        }
      }
      // PICTURE块
      else if (blockType === 6 && blockSize > 32) {
        try {
          let picOffset = offset

          // 读取图片类型（大端序）
          const pictureType = dataView.getUint32(picOffset, false)
          picOffset += 4

          // 读取MIME类型长度（大端序）
          const mimeLength = dataView.getUint32(picOffset, false)
          picOffset += 4

          // 检查边界
          if (picOffset + mimeLength > offset + blockSize) {
            console.warn('FLAC图片MIME类型超出范围')
            break
          }

          const mimeBytes = new Uint8Array(arrayBuffer, picOffset, mimeLength)
          const mimeType = textDecoder.decode(mimeBytes)
          picOffset += mimeLength

          // 读取描述长度（大端序）
          const descLength = dataView.getUint32(picOffset, false)
          picOffset += 4 + descLength

          // 跳过宽度、高度、颜色深度、颜色数（各4字节）
          picOffset += 16

          // 读取图片数据长度（大端序）
          const pictureLength = dataView.getUint32(picOffset, false)
          picOffset += 4

          if (pictureLength > 0 && picOffset + pictureLength <= offset + blockSize) {
            const imageData = new Uint8Array(arrayBuffer, picOffset, pictureLength)
            metadata.cover = new Blob([imageData], { type: mimeType })
          }
        } catch (e) {
          console.warn('解析PICTURE块失败:', e)
        }
      }

      // 如果是最后一个块，停止
      if (isLast) break

      // 移动到下一个块
      offset += blockSize
    }

    // 自动填充表单
    if (metadata.title) formData.value.title = metadata.title
    if (metadata.artist) formData.value.artist = metadata.artist
    if (metadata.album) formData.value.album = metadata.album
    if (metadata.cover) {
      coverFile.value = new File([metadata.cover], 'cover.jpg', { type: metadata.cover.type })
      coverPreview.value = URL.createObjectURL(metadata.cover)
    }

    console.log('[FLAC解析] 解析结果:', {
      title: metadata.title,
      artist: metadata.artist,
      album: metadata.album,
      hasCover: !!metadata.cover
    })
    toast.success('已自动解析FLAC文件信息')
  } catch (error) {
    console.error('[FLAC解析] 解析失败:', error)
    toast.warning('无法自动解析FLAC文件信息，请手动填写')
  }
}

// 解析WAV文件的元数据（简化版）
const parseWavMetadata = async (file) => {
  console.log('[WAV解析] WAV文件暂不支持自动解析元数据')
  // WAV文件通常不包含ID3标签，这里只做简单处理
  toast.warning('WAV文件暂不支持自动解析元数据，请手动填写')
}

const handleCoverFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    coverFile.value = file
    coverPreview.value = URL.createObjectURL(file)
  }
}

const removeMusicFile = () => {
  musicFile.value = null
  musicFileInput.value.value = ''
}

const removeCoverFile = () => {
  coverFile.value = null
  coverPreview.value = null
  coverFileInput.value.value = ''
}

const selectLyricsFile = () => {
  lyricsFileInput.value.click()
}

const handleLyricsFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    lyricsFile.value = file
  }
}

const removeLyricsFile = () => {
  lyricsFile.value = null
  lyricsFileInput.value.value = ''
}

const handleDrop = async (event) => {
  event.preventDefault()
  isDragging.value = false
  
  const files = event.dataTransfer.files
  if (files.length > 0) {
    const file = files[0]
    if (file.type.startsWith('audio/')) {
      musicFile.value = file
      
      // 读取音频时长
      const audio = new Audio()
      audio.src = URL.createObjectURL(file)
      audio.onloadedmetadata = async () => {
        // 自动解析元数据
        const fileName = file.name.toLowerCase()
        const fileExtension = fileName.split('.').pop()
        
        if (fileExtension === 'mp3') {
          await parseMP3Metadata(file)
        } else if (fileExtension === 'flac') {
          await parseFlacMetadata(file)
        } else if (fileExtension === 'wav') {
          await parseWavMetadata(file)
        }
        URL.revokeObjectURL(audio.src)
      }
    } else {
      toast.error('请拖入音频文件')
    }
  }
}

// 格式化时长（秒转分:秒）
const formatDuration = (seconds) => {
  if (!seconds || seconds <= 0) return '未设置'
  const minutes = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${minutes}:${secs.toString().padStart(2, '0')}`
}

// 解析元数据的辅助函数
const parseMetadata = async (fileExtension, file) => {
  console.log('---------- 开始解析元数据 ----------')
  
  try {
    if (fileExtension === 'mp3') {
      await parseMP3Metadata(file)
    } else if (fileExtension === 'flac') {
      await parseFlacMetadata(file)
    } else if (fileExtension === 'wav') {
      await parseWavMetadata(file)
    }
    
    console.log('---------- 元数据解析完成 ----------')
    console.log('解析后的formData:', JSON.stringify(formData.value, null, 2))
    console.log('========== 音乐文件解析完成 ==========')
  } catch (error) {
    console.error('[元数据解析] 解析过程出错:', error)
  }
}

// 扩展DataView以支持读取字符串
DataView.prototype.getString = function(offset, length) {
  let result = ''
  for (let i = 0; i < length; i++) {
    const byte = this.getUint8(offset + i)
    if (byte === 0) break
    result += String.fromCharCode(byte)
  }
  return result
}

// 解码文本帧
DataView.prototype.decodeTextFrame = function(offset, length) {
  if (length === 0) return ''

  const encoding = this.getUint8(offset)
  const textData = new Uint8Array(this.buffer, this.byteOffset + offset + 1, length - 1)

  switch (encoding) {
    case 0:
      return this.decodeISO88591(textData)
    case 1:
      return this.decodeUTF16(textData)
    case 2:
      return this.decodeUTF16BE(textData)
    case 3:
      return this.decodeUTF8(textData)
    default:
      return this.decodeISO88591(textData)
  }
}

DataView.prototype.decodeISO88591 = function(data) {
  let result = ''
  for (let i = 0; i < data.length; i++) {
    result += String.fromCharCode(data[i])
  }
  return result
}

DataView.prototype.decodeUTF16 = function(data) {
  if (data.length < 2) return ''

  const bom = (data[0] << 8) | data[1]

  if (bom === 0xFEFF) {
    return this.decodeUTF16BE(data)
  } else if (bom === 0xFFFE) {
    return this.decodeUTF16LE(data)
  } else {
    return this.decodeUTF16BE(data)
  }
}

DataView.prototype.decodeUTF16BE = function(data) {
  let result = ''
  for (let i = 0; i < data.length; i += 2) {
    if (i + 1 < data.length) {
      const codePoint = (data[i] << 8) | data[i + 1]
      if (codePoint === 0) break
      result += String.fromCharCode(codePoint)
    }
  }
  return result
}

DataView.prototype.decodeUTF16LE = function(data) {
  let result = ''
  for (let i = 0; i < data.length; i += 2) {
    if (i + 1 < data.length) {
      const codePoint = data[i] | (data[i + 1] << 8)
      if (codePoint === 0) break
      result += String.fromCharCode(codePoint)
    }
  }
  return result
}

DataView.prototype.decodeUTF8 = function(data) {
  let result = ''
  let i = 0

  while (i < data.length) {
    const byte1 = data[i]

    if (byte1 === 0) break

    if (byte1 < 0x80) {
      result += String.fromCharCode(byte1)
      i++
    } else if ((byte1 & 0xE0) === 0xC0) {
      if (i + 1 < data.length) {
        const codePoint = ((byte1 & 0x1F) << 6) | (data[i + 1] & 0x3F)
        result += String.fromCharCode(codePoint)
        i += 2
      } else {
        i++
      }
    } else if ((byte1 & 0xF0) === 0xE0) {
      if (i + 2 < data.length) {
        const codePoint = ((byte1 & 0x0F) << 12) | ((data[i + 1] & 0x3F) << 6) | (data[i + 2] & 0x3F)
        result += String.fromCharCode(codePoint)
        i += 3
      } else {
        i++
      }
    } else if ((byte1 & 0xF8) === 0xF0) {
      if (i + 3 < data.length) {
        const codePoint = ((byte1 & 0x07) << 18) | ((data[i + 1] & 0x3F) << 12) | ((data[i + 2] & 0x3F) << 6) | (data[i + 3] & 0x3F)
        result += String.fromCodePoint(codePoint)
        i += 4
      } else {
        i++
      }
    } else {
      i++
    }
  }

  return result
}

const handleCoverDrop = (event) => {
  isCoverDragging.value = false
  const file = event.dataTransfer.files[0]
  if (file && file.type.startsWith('image/')) {
    coverFile.value = file
    coverPreview.value = URL.createObjectURL(file)
  }
}

const handleLyricsFileDrop = (event) => {
  event.preventDefault()
  
  const files = event.dataTransfer.files
  if (files.length > 0) {
    const file = files[0]
    const fileName = file.name.toLowerCase()
    if (fileName.endsWith('.lrc')) {
      lyricsFile.value = file
    } else {
      toast.error('请拖入.lrc格式的歌词文件')
    }
  }
}

const showUploadNotice = (type, message) => {
  const normalizedType = type === 'success' ? 'success' : 'error'
  const normalizedMessage = message || (normalizedType === 'success' ? '上传成功' : '上传失败')

  uploadNotice.value = {
    visible: true,
    type: normalizedType,
    message: normalizedMessage
  }

  if (uploadNoticeTimer) {
    clearTimeout(uploadNoticeTimer)
  }
  uploadNoticeTimer = setTimeout(() => {
    uploadNotice.value.visible = false
  }, 5000)

  if (normalizedType === 'success') {
    toast.success(normalizedMessage)
  } else {
    toast.error(normalizedMessage)
  }
}

const showUploadResultToast = (result, fallbackMessage) => {
  const message = result?.message || fallbackMessage || '上传失败'
  console.log('上传接口解析结果:', result)
  if (result?.success === true) {
    showUploadNotice('success', message)
    return true
  }
  showUploadNotice('error', message)
  return false
}

const handleSubmit = async () => {
  console.log('========== 开始提交上传 ==========')
  console.log('提交的数据:', JSON.stringify(formData.value, null, 2))
  console.log('音乐文件:', musicFile.value ? musicFile.value.name : '未选择')
  console.log('封面文件:', coverFile.value ? coverFile.value.name : '未选择')
  console.log('歌词文件:', lyricsFile.value ? lyricsFile.value.name : '未选择')
  
  if (!musicFile.value) {
    showUploadNotice('error', '请选择音乐文件')
    return
  }

  if (!formData.value.title || !formData.value.artist || !formData.value.language) {
    showUploadNotice('error', '请填写歌曲标题、歌手和语言')
    return
  }

  uploading.value = true
  uploadProgress.value = 0

  try {
    const form = new FormData()
    form.append('title', formData.value.title)
    form.append('artist', formData.value.artist)
    form.append('language', formData.value.language)
    form.append('tags', formData.value.tags || '')
    form.append('album', formData.value.album || '')
    form.append('duration', formData.value.duration)
    form.append('uploadUserId', 0)
    form.append('musicFile', musicFile.value)
    
    if (coverFile.value) {
      form.append('coverFile', coverFile.value)
    }
    
    if (lyricsFile.value) {
      form.append('lyricsFile', lyricsFile.value)
    }

    const uploadUrl = `${API_CONFIG.BASE_URL}/api/user/upload`
    const token = localStorage.getItem('userToken')
    const headers = {}
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }

    uploadProgress.value = 10
    const response = await fetch(uploadUrl, {
      method: 'POST',
      headers,
      body: form
    })
    uploadProgress.value = 100

    const responseText = await response.text()
    let result = {}
    try {
      result = responseText ? JSON.parse(responseText) : {}
    } catch (parseError) {
      console.error('上传接口返回内容不是JSON:', responseText)
    }

    const fallbackMessage = result.error || responseText || `上传失败（HTTP ${response.status}）`

    if (showUploadResultToast(result, fallbackMessage)) {
      setTimeout(() => {
        router.push('/')
      }, 1500)
    }

  } catch (error) {
    console.error('上传错误:', error)
    showUploadNotice('error', '上传失败，请稍后重试')
  } finally {
    uploading.value = false
  }
}
</script>

<style scoped>
.upload-view {
  min-height: calc(100vh - 80px);
  padding: 40px 20px;
}

.upload-result-notice {
  position: fixed;
  top: 92px;
  right: 24px;
  max-width: min(420px, calc(100vw - 32px));
  padding: 14px 18px;
  border-radius: 12px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  box-shadow: 0 18px 45px rgba(0, 0, 0, 0.32);
  z-index: 2147483647;
  pointer-events: auto;
  word-break: break-word;
}

.upload-result-notice--success {
  background: linear-gradient(135deg, #16a34a, #059669);
}

.upload-result-notice--error {
  background: linear-gradient(135deg, #69c8df, #be123c);
}

.upload-notice-enter-active,
.upload-notice-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.upload-notice-enter-from,
.upload-notice-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.upload-container {
  max-width: 1000px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 50px;
  align-items: start;
}

/* 左侧封面 */
.cover-side {
  position: sticky;
  top: 20px;
}

.cover-upload {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 20px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.3);
  border: 2px dashed rgba(102, 126, 234, 0.3);
}

.cover-upload:hover,
.cover-upload.drag-over {
  border-color: #667eea;
  transform: scale(1.02);
  background: rgba(102, 126, 234, 0.1);
}

.cover-upload.has-cover {
  border-style: solid;
  border-color: rgba(102, 126, 234, 0.5);
}

.cover-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #667eea;
  text-align: center;
  padding: 20px;
}

.cover-placeholder svg {
  opacity: 0.8;
}

.cover-placeholder p {
  font-size: 16px;
  font-weight: 600;
  margin: 0;
}

.cover-placeholder span {
  font-size: 14px;
  opacity: 0.7;
}

.cover-preview {
  width: 100%;
  height: 100%;
  position: relative;
}

.cover-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-overlay {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s ease;
}

.cover-preview:hover .cover-overlay {
  opacity: 1;
}

.change-btn {
  padding: 10px 24px;
  background: white;
  color: #333;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.change-btn:hover {
  transform: scale(1.05);
}

/* 左侧歌词上传 */
.lyrics-upload-section {
  margin-top: 24px;
}

.side-label {
  color: #333;
  font-size: 14px;
  font-weight: 600;
  display: block;
  margin-bottom: 8px;
}

.lyrics-upload {
  padding: 20px;
}

/* 双语歌词格式说明 */
.lyrics-format-guide {
  margin-top: 16px;
  padding: 16px;
  background: rgba(105, 200, 223, 0.05);
  border-radius: 12px;
  border: 1px solid rgba(105, 200, 223, 0.15);
}

.lyrics-format-guide h4 {
  color: #69c8df;
  margin: 0 0 8px 0;
  font-size: 15px;
  font-weight: 600;
}

.lyrics-format-guide p {
  color: #666;
  margin: 0 0 12px 0;
  font-size: 13px;
  line-height: 1.5;
}

.lyrics-example {
  margin: 12px 0;
  padding: 12px;
  background: rgba(255, 255, 255, 0.8);
  border-radius: 8px;
  border: 1px solid rgba(105, 200, 223, 0.1);
}

.example-title {
  color: #69c8df;
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 8px;
}

.example-code {
  margin: 0;
  padding: 10px;
  background: #f8f7ff;
  border-radius: 6px;
  font-size: 11px;
  line-height: 1.6;
  color: #555;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.lyrics-tips {
  margin-top: 12px;
  padding: 10px;
  background: rgba(105, 200, 223, 0.08);
  border-radius: 8px;
}

.lyrics-tips p {
  color: #555;
  margin: 0 0 8px 0;
  font-size: 13px;
}

.lyrics-tips ul {
  margin: 0;
  padding-left: 18px;
  color: #666;
  font-size: 12px;
  line-height: 1.8;
}

.lyrics-tips li {
  margin-bottom: 4px;
}

.lyrics-tips code {
  background: rgba(105, 200, 223, 0.15);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Courier New', monospace;
  font-size: 11px;
  color: #69c8df;
}

/* 右侧表单 */
.form-side {
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  border-radius: 20px;
  padding: 40px;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.page-title {
  color: #333;
  font-size: 28px;
  font-weight: 600;
  margin: 0 0 30px 0;
}

.upload-form {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.label {
  color: #333;
  font-size: 14px;
  font-weight: 600;
}

.required {
  color: #ef4444;
  margin-left: 2px;
}

.info-hint {
  color: #999;
  font-size: 12px;
  font-weight: normal;
  margin-left: 4px;
}

.input:read-only,
.input:disabled {
  background: rgba(240, 240, 240, 0.5);
  cursor: not-allowed;
  color: #666;
}

.input:read-only::placeholder,
.input:disabled::placeholder {
  color: #aaa;
}

.input-hint {
  margin-top: 6px;
  font-size: 12px;
  color: #666;
  display: flex;
  align-items: center;
  gap: 8px;
}

.warning-text {
  color: #f59e0b;
  font-weight: 500;
}

.duration-input-group {
  display: flex;
  gap: 10px;
}

.duration-input {
  flex: 1;
}

.parse-duration-btn {
  padding: 12px 20px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 10px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.3s ease;
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.3);
}

.parse-duration-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.parse-duration-btn:active:not(:disabled) {
  transform: translateY(0);
}

.parse-duration-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  background: #ccc;
  box-shadow: none;
}

.file-upload {
  border: 2px dashed rgba(102, 126, 234, 0.3);
  border-radius: 16px;
  padding: 30px;
  text-align: center;
  cursor: pointer;
  transition: all 0.3s ease;
  background: rgba(255, 255, 255, 0.2);
}

.file-upload:hover,
.file-upload.drag-over {
  border-color: #667eea;
  background: rgba(102, 126, 234, 0.1);
}

.file-upload.has-file {
  border-style: solid;
  border-color: #667eea;
  padding: 20px;
  text-align: left;
}

.file-placeholder {
  color: #667eea;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
}

.file-placeholder span {
  font-size: 15px;
  font-weight: 500;
}

.file-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.file-info svg {
  color: #667eea;
  flex-shrink: 0;
}

.file-info span {
  flex: 1;
  font-size: 15px;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.remove-btn {
  width: 24px;
  height: 24px;
  border: none;
  background: #ef4444;
  color: white;
  border-radius: 50%;
  cursor: pointer;
  position: relative;
  flex-shrink: 0;
}

.remove-btn::before,
.remove-btn::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 10px;
  height: 2px;
  background: white;
  transform: translate(-50%, -50%) rotate(45deg);
}

.remove-btn::after {
  transform: translate(-50%, -50%) rotate(-45deg);
}

.input,
.textarea {
  padding: 14px 18px;
  border: 2px solid rgba(255, 255, 255, 0.5);
  border-radius: 14px;
  font-size: 15px;
  background: rgba(255, 255, 255, 0.5);
  backdrop-filter: blur(5px);
  transition: all 0.3s ease;
}

.input::placeholder,
.textarea::placeholder {
  color: #999;
}

.input:focus,
.textarea:focus {
  outline: none;
  border-color: #667eea;
  background: rgba(255, 255, 255, 0.7);
}

.textarea {
  resize: vertical;
  min-height: 100px;
  font-family: inherit;
}

.submit-btn {
  padding: 16px 48px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  border: none;
  border-radius: 14px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-top: 10px;
}

.submit-btn:hover:not(:disabled) {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(102, 126, 234, 0.4);
}

.submit-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

@media (max-width: 900px) {
  .upload-container {
    grid-template-columns: 1fr;
    gap: 30px;
  }

  .cover-side {
    position: static;
    display: flex;
    justify-content: center;
  }

  .cover-upload {
    max-width: 300px;
  }
}

@media (max-width: 600px) {
  .form-side {
    padding: 30px 20px;
  }

  .page-title {
    font-size: 24px;
  }
}
</style>
