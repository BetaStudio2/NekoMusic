<template>
  <div class="admin-layout">
    <AdminSidebar />
    
    <div class="admin-main-content">
      <div class="admin-header">
        <div class="admin-user-info">
          <span>欢迎，{{ adminInfo.username || '管理员' }}!</span>
          <button @click="logout" class="logout-button">退出登录</button>
        </div>
      </div>
      
      <div class="admin-content-wrapper">
        <div class="admin-subpage">
          <h2>音乐管理</h2>
          <p>管理平台音乐资源，包括添加、编辑、删除音乐等操作。</p>
          
          <div class="admin-controls">
            <button class="add-btn" @click="showAddForm = true">
              添加音乐
            </button>
          </div>
          
          <!-- 添加音乐模态框 -->
          <Transition name="modal">
            <div v-if="showAddForm" class="edit-modal-overlay" @click="closeAddModal">
              <div class="edit-modal edit-modal-wide" @click.stop ref="addModalRef">
                <div class="modal-header">
                  <h3>添加音乐</h3>
                  <button class="close-btn" @click="closeAddModal">&times;</button>
                </div>
                <div class="modal-content horizontal-layout">
                  <div class="form-column left-column">
                    <div class="form-group">
                      <label>🎵 音乐文件 *</label>
                      <input type="file" @change="handleMusicFileChange" accept=".mp3,.flac,.wav" placeholder="请选择音乐文件（MP3/FLAC/WAV）" />
                      <div v-if="newMusic.fileName" class="file-info">已选择: {{ newMusic.fileName }}</div>
                      <div class="form-hint">支持 MP3、FLAC、WAV 格式。上传 MP3 文件后将自动解析封面、音乐名称、艺术家和专辑信息</div>
                    </div>
                    <div class="form-group">
                      <label>🎵 音乐图标</label>
                      <input type="file" @change="handleCoverFileChange" accept="image/*" placeholder="请选择音乐图标文件（可选）" />
                      <div v-if="newMusic.coverFileName" class="file-info">已选择: {{ newMusic.coverFileName }}</div>
                      <div class="form-hint">如果不选择，将使用MP3文件中的封面图</div>
                    </div>
                    <div class="form-group">
                      <label>⏱️ 时长(秒)</label>
                      <input type="number" v-model="newMusic.duration" placeholder="音乐时长(秒)" readonly />
                      <div class="form-hint">自动从MP3文件中读取</div>
                    </div>
                    <div class="form-group">
                      <label>🌐 语言 *</label>
                      <div class="select-wrapper">
                        <select v-model="newMusic.language" class="styled-select">
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
                    </div>
                  </div>
                  <div class="form-column right-column">
                    <div class="form-group">
                      <label>🎵 音乐名称 *</label>
                      <input type="text" v-model="newMusic.title" placeholder="请输入音乐名称" />
                      <div class="form-hint">自动从MP3文件中读取</div>
                    </div>
                    <div class="form-group">
                      <label>🎤 艺术家 *</label>
                      <input type="text" v-model="newMusic.artist" placeholder="请输入艺术家" />
                      <div class="form-hint">自动从MP3文件中读取</div>
                    </div>
                    <div class="form-group">
                      <label>🏷️ 标签</label>
                      <input type="text" v-model="newMusic.tags" placeholder="请输入标签，多个标签用逗号分隔" />
                    </div>
                    <div class="form-group">
                      <label>💿 专辑</label>
                      <input type="text" v-model="newMusic.album" placeholder="请输入专辑" />
                      <div class="form-hint">自动从MP3文件中读取</div>
                    </div>
                    <div class="form-group">
                      <label>📝 歌词文件 *</label>
                      <input type="file" @change="handleLyricsFileChange" accept=".lrc" placeholder="请选择LRC歌词文件" />
                      <div v-if="newMusic.lyricsFileName" class="file-info">已选择: {{ newMusic.lyricsFileName }}</div>
                      <div class="form-hint">请上传 .lrc 格式的歌词文件</div>
                    </div>
                  </div>
                </div>
                <div class="form-actions modal-actions">
                  <button class="secondary-btn" @click="closeAddModal">取消</button>
                  <button class="primary-btn" @click="addMusic">添加音乐</button>
                </div>
              </div>
            </div>
          </Transition>
          
          <!-- 编辑音乐悬浮窗 -->
          <Transition name="modal">
            <div v-if="editingMusic" class="edit-modal-overlay" @click="closeEditModal">
              <div class="edit-modal edit-modal-wide" @click.stop ref="editModalRef">
                <div class="modal-header">
                  <h3>编辑音乐</h3>
                  <button class="close-btn" @click="cancelEdit">&times;</button>
                </div>
                <div class="modal-content horizontal-layout">
                  <div class="form-column left-column">
                    <div class="form-group">
                      <label>🎵 音乐图标</label>
                      <input type="file" @change="handleEditCoverFileChange" accept="image/*" placeholder="请选择音乐图标文件" />
                      <div v-if="editingMusic.coverFileName" class="file-info">已选择: {{ editingMusic.coverFileName }}</div>
                      <div v-if="editingMusic.coverUrl && !editingMusic.coverFileName && !editingMusic.coverUrl.startsWith('data:image')" class="file-info">当前图标: {{ editingMusic.coverUrl.split('/').pop() }}</div>
                    </div>
                    <div class="form-group">
                      <label>🎵 音乐文件</label>
                      <input type="file" @change="handleEditMusicFileChange" accept=".mp3,.flac,.wav" placeholder="请选择音乐文件（MP3/FLAC/WAV）" />
                      <div v-if="editingMusic.fileName" class="file-info">已选择: {{ editingMusic.fileName }}</div>
                      <div v-if="editingMusic.filePath && !editingMusic.fileName" class="file-info">当前文件: {{ editingMusic.filePath.split('/').pop() }}</div>
                    </div>
                    <div class="form-group">
                      <label>🌐 语言 *</label>
                      <div class="select-wrapper">
                        <select v-model="editingMusic.language" class="styled-select">
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
                    </div>
                  </div>
                  <div class="form-column right-column">
                    <div class="form-group">
                      <label>🎵 音乐名称 *</label>
                      <input type="text" v-model="editingMusic.title" placeholder="请输入音乐名称" />
                    </div>
                    <div class="form-group">
                      <label>🎤 艺术家 *</label>
                      <input type="text" v-model="editingMusic.artist" placeholder="请输入艺术家" />
                    </div>
                    <div class="form-group">
                      <label>🏷️ 标签</label>
                      <input type="text" v-model="editingMusic.tags" placeholder="请输入标签，多个标签用逗号分隔" />
                    </div>
                    <div class="form-group">
                      <label>💿 专辑</label>
                      <input type="text" v-model="editingMusic.album" placeholder="请输入专辑" />
                    </div>
                    <div class="form-group">
                      <label>📝 歌词文件 *</label>
                      <input type="file" @change="handleEditLyricsFileChange" accept=".lrc" placeholder="请选择LRC歌词文件" />
                      <div v-if="editingMusic.lyricsFileName" class="file-info">已选择: {{ editingMusic.lyricsFileName }}</div>
                      <div v-if="editingMusic.lyricsPath && !editingMusic.lyricsFileName" class="file-info">当前文件: {{ editingMusic.lyricsPath.split('\\').pop() }}</div>
                      <div class="form-hint">请上传 .lrc 格式的歌词文件</div>
                    </div>
                  </div>
                </div>
                <div class="form-actions modal-actions">
                  <button class="secondary-btn" @click="cancelEdit">取消</button>
                  <button class="primary-btn" @click="saveEdit">保存更改</button>
                </div>
              </div>
            </div>
          </Transition>
          
          <div class="music-list-section">
            <h3>音乐列表</h3>
            <div class="search-filter">
              <input 
                type="text" 
                v-model="searchQuery" 
                @input="updateSearchResults"
                placeholder="搜索音乐或艺术家..." 
                class="search-input"
              />
            </div>
            
            <div v-if="isLoading" class="loading">
              <p>正在加载音乐列表...</p>
            </div>
            
            <div v-else class="table-container">
              <table class="music-table">
                <thead>
                  <tr>
                    <th>ID</th>
                    <th>封面</th>
                    <th>音乐名称</th>
                    <th>艺术家</th>
                    <th>专辑</th>
                    <th>时长</th>
                    <th>上传时间</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="music in paginatedMusicList()" :key="music.id">
                    <td>{{ music.id }}</td>
                    <td>
                      <div class="cover-cell">
                        <img 
                          :src="getCoverUrl(music.id)" 
                          :alt="music.title"
                          class="music-cover-table"
                          @error="handleImageError"
                        />
                      </div>
                    </td>
                    <td>{{ music.title }}</td>
                    <td>{{ music.artist }}</td>
                    <td>{{ music.album }}</td>
                    <td>{{ formatDuration(music.duration) }}</td>
                    <td>{{ formatDate(music.createdAt) }}</td>
                    <td>
                      <button class="action-btn edit-btn" @click="editMusic(music)">编辑</button>
                      <button class="action-btn delete-btn" @click="deleteMusic(music.id)">删除</button>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            
            <div v-if="!isLoading && filteredMusicList.length === 0" class="no-data">
              <p>暂无音乐数据</p>
            </div>

            <!-- 分页控件 -->
            <div v-if="!isLoading && filteredMusicList.length > 0" class="pagination-container">
              <div class="pagination-info">
                显示第 {{ (currentPage - 1) * pageSize + 1 }} - {{ Math.min(currentPage * pageSize, filteredMusicList.length) }} 条，共 {{ filteredMusicList.length }} 条
              </div>
              <div class="pagination-controls">
                <button
                  class="pagination-btn"
                  @click="prevPage"
                  :disabled="currentPage === 1"
                >
                  上一页
                </button>
                <div class="pagination-pages">
                  <button
                    v-for="page in Math.min(totalPages, 5)"
                    :key="page"
                    class="pagination-page-btn"
                    :class="{ active: currentPage === getDisplayPage(page) }"
                    @click="goToPage(getDisplayPage(page))"
                  >
                    {{ getDisplayPage(page) }}
                  </button>
                </div>
                <button
                  class="pagination-btn"
                  @click="nextPage"
                  :disabled="currentPage === totalPages"
                >
                  下一页
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import AdminSidebar from '@/components/AdminSidebar.vue'
import API_CONFIG from '@/config/apiConfig.js'
import { useToast } from 'vue-toastification'

const toast = useToast()

const router = useRouter()

// 添加文件处理函数
const handleMusicFileChange = async (event) => {
  const file = event.target.files[0]
  if (file) {
    // 检查文件类型是否为支持的格式：MP3、FLAC、WAV
    const fileName = file.name.toLowerCase()
    const fileExtension = fileName.split('.').pop()

    // 主要依赖文件扩展名来识别格式
    const isValidFormat = ['.mp3', '.flac', '.wav'].includes('.' + fileExtension)

    if (!isValidFormat) {
      toast.error('请选择 MP3、FLAC 或 WAV 格式的音乐文件')
      event.target.value = '' // 清空选择
      return
    }

    console.log('检测到文件格式:', fileExtension, '文件类型:', file.type)

    newMusic.value.file = file
    newMusic.value.fileName = file.name

    // 读取音频时长
    const audio = new Audio()
    audio.src = URL.createObjectURL(file)
    audio.onloadedmetadata = async () => {
      newMusic.value.duration = Math.floor(audio.duration)
      console.log('音频时长:', newMusic.value.duration, '秒')

      // 根据文件扩展名解析元数据
      if (fileExtension === 'mp3') {
        console.log('开始解析 MP3 元数据')
        await parseMP3Metadata(file)
      } else if (fileExtension === 'flac') {
        console.log('开始解析 FLAC 元数据')
        await parseFlacMetadata(file)
      } else if (fileExtension === 'wav') {
        console.log('开始解析 WAV 元数据')
        await parseWavMetadata(file)
      }
      URL.revokeObjectURL(audio.src) // 释放对象URL
    }
  }
}

// 解析MP3文件的元数据
const parseMP3Metadata = async (file) => {
  console.log('开始解析MP3文件:', file.name)
  try {
    const arrayBuffer = await file.arrayBuffer()
    const dataView = new DataView(arrayBuffer)
    console.log('文件大小:', arrayBuffer.length, 'bytes')

    // 检查文件头
    const header = dataView.getString(0, 3)
    console.log('文件头标识:', header)

    // 查找ID3v2标签
    if (header === 'ID3') {
      const version = dataView.getUint8(3)
      const revision = dataView.getUint8(4)
      const flags = dataView.getUint8(5)
      const size = dataView.getUint32(6)

      console.log('ID3版本:', version, '.', revision)
      console.log('ID3标志:', flags.toString(2))
      console.log('ID3标签大小:', size, 'bytes')

      const headerSize = 10
      let offset = headerSize

      const metadata = {
        title: '',
        artist: '',
        album: '',
        cover: null
      }

      console.log('开始解析帧...')

      while (offset < headerSize + size) {
        const frameId = dataView.getString(offset, 4)
        const frameSize = dataView.getUint32(offset + 4)
        const frameFlags = dataView.getUint16(offset + 8)

        console.log('帧ID:', frameId, '大小:', frameSize)

        if (frameSize === 0) {
          console.log('帧大小为0，停止解析')
          break
        }

        // 跳过帧头
        const frameDataOffset = offset + 10
        const frameDataSize = frameSize

        if (frameId === 'TIT2') {
          metadata.title = dataView.decodeTextFrame(frameDataOffset, frameDataSize)
          console.log('解析到音乐名称:', metadata.title)
        } else if (frameId === 'TPE1') {
          metadata.artist = dataView.decodeTextFrame(frameDataOffset, frameDataSize)
          console.log('解析到艺术家:', metadata.artist)
        } else if (frameId === 'TALB') {
          metadata.album = dataView.decodeTextFrame(frameDataOffset, frameDataSize)
          console.log('解析到专辑:', metadata.album)
        } else if (frameId === 'APIC') {
          // 解析封面图片
          console.log('开始解析封面图片...')

          const picOffset = frameDataOffset
          const textEncoding = dataView.getUint8(picOffset)
          console.log('封面文本编码:', textEncoding)

          let currentOffset = picOffset + 1

          // 读取MIME类型
          let mimeTypeEnd = currentOffset
          while (dataView.getUint8(mimeTypeEnd) !== 0) {
            mimeTypeEnd++
          }
          const mimeType = dataView.getString(currentOffset, mimeTypeEnd - currentOffset)
          currentOffset = mimeTypeEnd + 1
          console.log('封面MIME类型:', mimeType)

          // 跳过图片类型
          const pictureType = dataView.getUint8(currentOffset)
          currentOffset += 1
          console.log('封面图片类型:', pictureType)

          // 读取描述
          let descEnd = currentOffset
          while (dataView.getUint8(descEnd) !== 0) {
            descEnd++
          }
          const description = dataView.decodeTextString(currentOffset, descEnd - currentOffset, textEncoding)
          currentOffset = descEnd + 1
          console.log('封面描述:', description)

          // 读取图片数据
          const imageSize = frameDataSize - (currentOffset - frameDataOffset)
          console.log('封面图片大小:', imageSize, 'bytes')

          if (imageSize > 0) {
            const imageData = new Uint8Array(arrayBuffer, currentOffset, imageSize)
            metadata.cover = new Blob([imageData], { type: mimeType })
            console.log('封面图片解析成功')
          }
        }

        offset += 10 + frameSize
      }

      console.log('解析完成，开始填充表单...')

      // 自动填充表单
      if (metadata.title) {
        newMusic.value.title = metadata.title
        console.log('已填充音乐名称:', metadata.title)
      }
      if (metadata.artist) {
        newMusic.value.artist = metadata.artist
        console.log('已填充艺术家:', metadata.artist)
      }
      if (metadata.album) {
        newMusic.value.album = metadata.album
        console.log('已填充专辑:', metadata.album)
      }
      if (metadata.cover) {
        newMusic.value.coverFile = new File([metadata.cover], 'cover.jpg', { type: metadata.cover.type })
        newMusic.value.coverFileName = 'cover.jpg'
        console.log('已填充封面图片')
      }

      console.log('MP3文件信息解析成功')
      toast.success('已自动解析MP3文件信息')
    } else {
      console.log('未找到ID3标签，无法解析元数据')
      toast.warning('该MP3文件不包含ID3标签，无法自动解析信息')
    }
  } catch (error) {
    console.error('解析MP3元数据失败:', error)
    console.error('错误详情:', error.stack)
    toast.warning('无法自动解析MP3文件信息，请手动填写')
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

// 解码文本帧（处理不同的编码）
DataView.prototype.decodeTextFrame = function(offset, length) {
  if (length === 0) return ''

  const encoding = this.getUint8(offset)
  const textData = new Uint8Array(this.buffer, this.byteOffset + offset + 1, length - 1)

  switch (encoding) {
    case 0: // ISO-8859-1
      return this.decodeISO88591(textData)
    case 1: // UTF-16 with BOM
      return this.decodeUTF16(textData)
    case 2: // UTF-16BE without BOM
      return this.decodeUTF16BE(textData)
    case 3: // UTF-8
      return this.decodeUTF8(textData)
    default:
      console.warn('未知编码:', encoding, '使用ISO-8859-1')
      return this.decodeISO88591(textData)
  }
}

// 解码ISO-8859-1编码
DataView.prototype.decodeISO88591 = function(data) {
  let result = ''
  for (let i = 0; i < data.length; i++) {
    result += String.fromCharCode(data[i])
  }
  return result
}

// 解码UTF-16 with BOM
DataView.prototype.decodeUTF16 = function(data) {
  if (data.length < 2) return ''

  const bom = (data[0] << 8) | data[1]

  if (bom === 0xFEFF) {
    // Big Endian
    return this.decodeUTF16BE(data)
  } else if (bom === 0xFFFE) {
    // Little Endian
    return this.decodeUTF16LE(data)
  } else {
    // 无BOM，假设Big Endian
    return this.decodeUTF16BE(data)
  }
}

// 解码UTF-16BE
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

// 解码UTF-16LE
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

// 解码UTF-8
DataView.prototype.decodeUTF8 = function(data) {
  let result = ''
  let i = 0

  while (i < data.length) {
    const byte1 = data[i]

    if (byte1 === 0) break

    if (byte1 < 0x80) {
      // 1字节
      result += String.fromCharCode(byte1)
      i++
    } else if ((byte1 & 0xE0) === 0xC0) {
      // 2字节
      if (i + 1 < data.length) {
        const codePoint = ((byte1 & 0x1F) << 6) | (data[i + 1] & 0x3F)
        result += String.fromCharCode(codePoint)
        i += 2
      } else {
        i++
      }
    } else if ((byte1 & 0xF0) === 0xE0) {
      // 3字节
      if (i + 2 < data.length) {
        const codePoint = ((byte1 & 0x0F) << 12) | ((data[i + 1] & 0x3F) << 6) | (data[i + 2] & 0x3F)
        result += String.fromCharCode(codePoint)
        i += 3
      } else {
        i++
      }
    } else if ((byte1 & 0xF8) === 0xF0) {
      // 4字节
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

// 解码文本字符串（带编码）
DataView.prototype.decodeTextString = function(offset, length, encoding) {
  const textData = new Uint8Array(this.buffer, this.byteOffset + offset, length)

  switch (encoding) {
    case 0: // ISO-8859-1
      return this.decodeISO88591(textData)
    case 1: // UTF-16 with BOM
      return this.decodeUTF16(textData)
    case 2: // UTF-16BE without BOM
      return this.decodeUTF16BE(textData)
    case 3: // UTF-8
      return this.decodeUTF8(textData)
    default:
      return this.decodeISO88591(textData)
  }
}

// 解析FLAC文件的元数据
const parseFlacMetadata = async (file, targetMusic = newMusic) => {
  console.log('开始解析FLAC文件:', file.name, '目标对象:', targetMusic === newMusic ? 'newMusic' : 'editingMusic')
  try {
    const arrayBuffer = await file.arrayBuffer()
    const dataView = new DataView(arrayBuffer)
    const textDecoder = new TextDecoder('utf-8')

    // 检查FLAC文件头（fLaC）
    // FLAC文件头: 0x66 0x4C 0x61 0x43 ('fLaC')
    const byte0 = dataView.getUint8(0)
    const byte1 = dataView.getUint8(1)
    const byte2 = dataView.getUint8(2)
    const byte3 = dataView.getUint8(3)

    const header = String.fromCharCode(byte0, byte1, byte2, byte3)
    console.log('FLAC文件头:', header, `[${byte0.toString(16)}, ${byte1.toString(16)}, ${byte2.toString(16)}, ${byte3.toString(16)}]`)

    if (header !== 'fLaC') {
      console.log('不是有效的FLAC文件')
      toast.warning('该FLAC文件不包含元数据标签，无法自动解析信息')
      return
    }

    console.log('找到FLAC文件头')
    console.log('文件总大小:', arrayBuffer.byteLength, 'bytes')

    // FLAC元数据块解析
    let offset = 4 // 跳过"fLaC"标识
    const metadata = {
      title: '',
      artist: '',
      album: '',
      cover: null
    }

    let blockCount = 0
    const maxBlocks = 100 // 防止无限循环

    console.log('开始遍历元数据块，初始 offset:', offset)
    console.log('循环条件检查: offset < arrayBuffer.byteLength?', offset < arrayBuffer.byteLength)
    console.log('循环条件检查: blockCount < maxBlocks?', blockCount < maxBlocks)

    // 遍历元数据块
    while (offset < arrayBuffer.byteLength && blockCount < maxBlocks) {
      blockCount++

      console.log(`当前 offset: ${offset}, 文件总长度: ${arrayBuffer.byteLength}`)

      // 读取块头
      const blockHeader = dataView.getUint8(offset)
      const isLast = (blockHeader & 0x80) !== 0
      const blockType = blockHeader & 0x7F

      // 块大小是3字节，需要正确读取
      const byte1 = dataView.getUint8(offset + 1)
      const byte2 = dataView.getUint8(offset + 2)
      const byte3 = dataView.getUint8(offset + 3)
      const blockSize = (byte1 << 16) | (byte2 << 8) | byte3

      console.log(`元数据块 #${blockCount}: 类型=${blockType}, 大小=${blockSize}, 最后=${isLast}`)
      console.log(`块头字节: [${blockHeader.toString(16)}, ${byte1.toString(16)}, ${byte2.toString(16)}, ${byte3.toString(16)}]`)

      offset += 4 // 跳过块头

      // 检查是否超出文件范围
      if (offset + blockSize > arrayBuffer.byteLength) {
        console.warn('块大小超出文件范围，停止解析')
        break
      }

      // VORBIS_COMMENT块（类型4）包含元数据
      if (blockType === 4) {
        console.log('>>> 找到 VORBIS_COMMENT 块！开始解析...')

        // 读取vendor length和vendor string
        let dataOffset = 0
        const vendorLength = dataView.getUint32(offset + dataOffset, true)
        dataOffset += 4
        console.log(`Vendor 长度: ${vendorLength}`)

        dataOffset += vendorLength // 跳过vendor string

        // 读取comments count
        const commentsCount = dataView.getUint32(offset + dataOffset, true)
        dataOffset += 4

        console.log(`>>> 找到 ${commentsCount} 个注释`)

        // 解析每个comment
        for (let i = 0; i < commentsCount; i++) {
          const commentLength = dataView.getUint32(offset + dataOffset, true)
          dataOffset += 4

          if (dataOffset + commentLength > blockSize) {
            console.warn('注释长度超出块范围，停止解析')
            break
          }

          // 使用 TextDecoder 解码 UTF-8 字符串
          const commentBytes = new Uint8Array(arrayBuffer, offset + dataOffset, commentLength)
          const comment = textDecoder.decode(commentBytes)
          dataOffset += commentLength

          console.log(`>>> 注释 ${i + 1}: ${comment}`)

          // 解析comment格式: FIELD=value
          const equalIndex = comment.indexOf('=')
          if (equalIndex !== -1) {
            const field = comment.substring(0, equalIndex).toUpperCase()
            const value = comment.substring(equalIndex + 1)

            console.log(`>>> 解析字段: ${field} = ${value}`)

            if (field === 'TITLE') {
              metadata.title = value
              console.log('>>> ✓ 解析到音乐名称:', value)
            } else if (field === 'ARTIST') {
              metadata.artist = value
              console.log('>>> ✓ 解析到艺术家:', value)
            } else if (field === 'ALBUM') {
              metadata.album = value
              console.log('>>> ✓ 解析到专辑:', value)
            }
          }
        }
      }
      // PICTURE块（类型6）包含封面图片
      else if (blockType === 6) {
        console.log('>>> 找到 PICTURE 块，开始解析封面...')

        let picOffset = offset
        console.log(`>>> PICTURE 块起始位置: ${picOffset}, 块大小: ${blockSize}`)

        // 读取图片类型（大端序）
        const pictureType = dataView.getUint32(picOffset, false)
        picOffset += 4
        console.log(`>>> 图片类型: ${pictureType}`)

        // 读取MIME类型长度和MIME类型（大端序）
        const mimeLength = dataView.getUint32(picOffset, false)
        picOffset += 4
        console.log(`>>> MIME类型长度: ${mimeLength}, 当前位置: ${picOffset}`)

        if (picOffset + mimeLength > offset + blockSize) {
          console.warn('>>> MIME类型长度超出块范围')
        } else {
          // 使用 TextDecoder 解码 MIME 类型
          const mimeBytes = new Uint8Array(arrayBuffer, picOffset, mimeLength)
          const mimeType = textDecoder.decode(mimeBytes)
          picOffset += mimeLength
          console.log(`>>> MIME类型: ${mimeType}, 解码后位置: ${picOffset}`)

          // 读取描述长度和描述（大端序）
          const descLength = dataView.getUint32(picOffset, false)
          picOffset += 4
          console.log(`>>> 描述长度: ${descLength}, 当前位置: ${picOffset}`)
          picOffset += descLength // 跳过描述
          console.log(`>>> 跳过描述后位置: ${picOffset}`)

          // 读取宽度、高度、颜色深度、颜色数（大端序）
          const width = dataView.getUint32(picOffset, false)
          picOffset += 4
          const height = dataView.getUint32(picOffset, false)
          picOffset += 4
          const colorDepth = dataView.getUint32(picOffset, false)
          picOffset += 4
          const colorCount = dataView.getUint32(picOffset, false)
          picOffset += 4
          console.log(`>>> 图片尺寸: ${width}x${height}, 颜色深度: ${colorDepth}, 颜色数: ${colorCount}`)
          console.log(`>>> 读取图片属性后位置: ${picOffset}`)

          // 读取图片数据长度和图片数据（大端序）
          const pictureLength = dataView.getUint32(picOffset, false)
          picOffset += 4
          console.log(`>>> 图片数据长度: ${pictureLength}, 当前位置: ${picOffset}`)
          console.log(`>>> 块结束位置: ${offset + blockSize}, 剩余空间: ${offset + blockSize - picOffset}`)

          console.log(`>>> 封面MIME类型: ${mimeType}, 大小: ${pictureLength} bytes`)

          if (pictureLength > 0 && picOffset + pictureLength <= offset + blockSize) {
            const imageData = new Uint8Array(arrayBuffer, picOffset, pictureLength)
            metadata.cover = new Blob([imageData], { type: mimeType })
            console.log('>>> ✓ 封面图片解析成功，Blob 大小:', metadata.cover.size)
          } else {
            console.warn(`>>> ❌ 封面图片数据超出范围: ${picOffset + pictureLength} > ${offset + blockSize}`)
          }
        }
      }
      // 其他块类型
      else {
        console.log(`>>> 跳过块类型 ${blockType}`)
      }

      offset += blockSize

      if (isLast) {
        console.log('>>> 到达最后一个元数据块')
        break
      }
    }

    // 自动填充表单
    console.log('准备填充表单，元数据:', metadata)
    console.log('当前目标对象值:', JSON.parse(JSON.stringify(targetMusic.value)))

    if (metadata.title) {
      targetMusic.value.title = metadata.title
      console.log('✓ 已填充音乐名称:', metadata.title)
    } else {
      console.log('✗ 未找到音乐名称')
    }
    if (metadata.artist) {
      targetMusic.value.artist = metadata.artist
      console.log('✓ 已填充艺术家:', metadata.artist)
    } else {
      console.log('✗ 未找到艺术家')
    }
    if (metadata.album) {
      targetMusic.value.album = metadata.album
      console.log('✓ 已填充专辑:', metadata.album)
    } else {
      console.log('✗ 未找到专辑')
    }
    if (metadata.cover) {
      targetMusic.value.coverFile = new File([metadata.cover], 'cover.jpg', { type: metadata.cover.type })
      targetMusic.value.coverFileName = 'cover.jpg'
      console.log('✓ 已填充封面图片')
    } else {
      console.log('✗ 未找到封面图片')
    }

    console.log('填充后的目标对象值:', JSON.parse(JSON.stringify(targetMusic.value)))
    console.log('FLAC文件信息解析成功')
    toast.success('已自动解析FLAC文件信息')
  } catch (error) {
    console.error('解析FLAC元数据失败:', error)
    console.error('错误详情:', error.stack)
    toast.warning('无法自动解析FLAC文件信息，请手动填写')
  }
}

// 解析WAV文件的元数据（WAV格式通常不包含ID3标签，但有一些变种支持）
const parseWavMetadata = async (file) => {
  console.log('开始解析WAV文件:', file.name)
  try {
    const arrayBuffer = await file.arrayBuffer()
    const dataView = new DataView(arrayBuffer)

    // 检查RIFF文件头
    const riffHeader = dataView.getString(0, 4)
    if (riffHeader !== 'RIFF') {
      console.log('不是有效的WAV文件')
      toast.warning('该WAV文件不包含元数据标签，无法自动解析信息')
      return
    }

    // 检查WAVE格式
    const waveHeader = dataView.getString(8, 4)
    if (waveHeader !== 'WAVE') {
      console.log('不是WAVE格式')
      toast.warning('该WAV文件不包含元数据标签，无法自动解析信息')
      return
    }

    console.log('找到WAV文件头')

    // WAV文件通常不包含ID3标签，但有些变种支持INFO LIST或ID3标签
    // 这里我们尝试查找ID3标签（某些WAV文件可能包含）
    let offset = 12 // 跳过RIFF头

    // 查找LIST INFO块
    while (offset < arrayBuffer.length - 8) {
      const chunkId = dataView.getString(offset, 4)
      const chunkSize = dataView.getUint32(offset + 4, true)

      console.log(`块: ${chunkId}, 大小: ${chunkSize}`)

      if (chunkId === 'LIST') {
        const listType = dataView.getString(offset + 8, 4)
        if (listType === 'INFO') {
          console.log('找到INFO LIST块')
          const metadata = {
            title: '',
            artist: '',
            album: ''
          }

          let infoOffset = offset + 12
          const infoEnd = offset + 8 + chunkSize

          while (infoOffset < infoEnd - 8) {
            const infoId = dataView.getString(infoOffset, 4)
            const infoSize = dataView.getUint32(infoOffset + 4, true)
            infoOffset += 8

            if (infoId === 'INAM') {
              // Title
              const titleBytes = new Uint8Array(arrayBuffer, infoOffset, infoSize)
              metadata.title = dataView.decodeUTF8(titleBytes).replace(/\0/g, '')
              console.log('解析到音乐名称:', metadata.title)
            } else if (infoId === 'IART') {
              // Artist
              const artistBytes = new Uint8Array(arrayBuffer, infoOffset, infoSize)
              metadata.artist = dataView.decodeUTF8(artistBytes).replace(/\0/g, '')
              console.log('解析到艺术家:', metadata.artist)
            } else if (infoId === 'IPRD') {
              // Album
              const albumBytes = new Uint8Array(arrayBuffer, infoOffset, infoSize)
              metadata.album = dataView.decodeUTF8(albumBytes).replace(/\0/g, '')
              console.log('解析到专辑:', metadata.album)
            }

            infoOffset += infoSize
            // 对齐到偶数字节
            if (infoSize % 2 !== 0) {
              infoOffset++
            }
          }

          // 自动填充表单
          if (metadata.title) {
            newMusic.value.title = metadata.title
          }
          if (metadata.artist) {
            newMusic.value.artist = metadata.artist
          }
          if (metadata.album) {
            newMusic.value.album = metadata.album
          }

          console.log('WAV文件信息解析成功')
          toast.success('已自动解析WAV文件信息')
          return
        }
      } else if (chunkId === 'ID3 ' || chunkId === 'id3 ') {
        // 某些WAV文件可能包含ID3标签
        console.log('找到ID3标签块')
        // 可以复用parseMP3Metadata的逻辑
        const id3Data = arrayBuffer.slice(offset + 8, offset + 8 + chunkSize)
        // 这里简化处理，WAV的ID3标签较少见
        toast.warning('该WAV文件包含ID3标签，但暂不支持解析')
        return
      }

      offset += 8 + chunkSize
      // 对齐到偶数字节
      if (chunkSize % 2 !== 0) {
        offset++
      }
    }

    console.log('未找到元数据标签')
    toast.warning('该WAV文件不包含元数据标签，无法自动解析信息')
  } catch (error) {
    console.error('解析WAV元数据失败:', error)
    console.error('错误详情:', error.stack)
    toast.warning('无法自动解析WAV文件信息，请手动填写')
  }
}

const handleCoverFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    // 检查文件类型是否为图片
    if (!file.type.startsWith('image/')) {
      toast.error('请选择图片格式的图标文件')
      event.target.value = '' // 清空选择
      return
    }
    
    newMusic.value.coverFile = file
    newMusic.value.coverFileName = file.name
  }
}

const handleEditMusicFileChange = async (event) => {
  const file = event.target.files[0]
  if (file) {
    // 检查文件类型是否为支持的格式：MP3、FLAC、WAV
    const fileName = file.name.toLowerCase()
    const fileExtension = fileName.split('.').pop()

    // 主要依赖文件扩展名来识别格式
    const isValidFormat = ['.mp3', '.flac', '.wav'].includes('.' + fileExtension)

    if (!isValidFormat) {
      toast.error('请选择 MP3、FLAC 或 WAV 格式的音乐文件')
      event.target.value = '' // 清空选择
      return
    }

    console.log('编辑模式 - 检测到文件格式:', fileExtension, '文件类型:', file.type)

    editingMusic.value.file = file
    editingMusic.value.fileName = file.name

    // 读取音频时长
    const audio = new Audio()
    audio.src = URL.createObjectURL(file)
    audio.onloadedmetadata = async () => {
      editingMusic.value.duration = Math.floor(audio.duration)
      console.log('编辑模式 - 音频时长:', editingMusic.value.duration, '秒')

      // 根据文件扩展名解析元数据
      if (fileExtension === 'mp3') {
        console.log('编辑模式 - 开始解析 MP3 元数据')
        await parseMP3Metadata(file, editingMusic)
      } else if (fileExtension === 'flac') {
        console.log('编辑模式 - 开始解析 FLAC 元数据')
        await parseFlacMetadata(file, editingMusic)
      } else if (fileExtension === 'wav') {
        console.log('编辑模式 - 开始解析 WAV 元数据')
        await parseWavMetadata(file, editingMusic)
      }
      URL.revokeObjectURL(audio.src) // 释放对象URL
    }
  }
}

const handleEditCoverFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    // 检查文件类型是否为图片
    if (!file.type.startsWith('image/')) {
      toast.error('请选择图片格式的图标文件')
      event.target.value = '' // 清空选择
      return
    }
    
    editingMusic.value.coverFile = file
    editingMusic.value.coverFileName = file.name
  }
}

const handleLyricsFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    // 检查文件类型是否为LRC
    if (!file.name.toLowerCase().endsWith('.lrc')) {
      toast.error('请选择LRC格式的歌词文件')
      event.target.value = '' // 清空选择
      return
    }
    
    newMusic.value.lyricsFile = file
    newMusic.value.lyricsFileName = file.name
  }
}

const handleEditLyricsFileChange = (event) => {
  const file = event.target.files[0]
  if (file) {
    // 检查文件类型是否为LRC
    if (!file.name.toLowerCase().endsWith('.lrc')) {
      toast.error('请选择LRC格式的歌词文件')
      event.target.value = '' // 清空选择
      return
    }
    
    editingMusic.value.lyricsFile = file
    editingMusic.value.lyricsFileName = file.name
  }
}

// 管理员信息
const adminInfo = ref({})

// 检查管理员登录状态
onMounted(() => {
  const storedToken = localStorage.getItem('adminToken')
  const storedAdminInfo = localStorage.getItem('adminInfo')
  
  if (storedToken && storedAdminInfo) {
    try {
      const parsedInfo = JSON.parse(storedAdminInfo)
      adminInfo.value = parsedInfo
    } catch (e) {
      console.error('解析管理员信息失败:', e)
      router.push('/admin/login')
    }
  } else {
    router.push('/admin/login')
  }
  
  // 获取音乐列表
  fetchMusicList()
})

// 音乐数据
const musicList = ref([])
const filteredMusicList = ref([])

// 分页相关数据
const currentPage = ref(1)
const pageSize = ref(100)
const totalPages = ref(1)

// 表单状态
const showAddForm = ref(false)
const newMusic = ref({
  title: '',
  artist: '',
  language: '',
  album: '',
  duration: 0,
  filePath: '',
  uploadUserId: 0,
  file: null,
  fileName: '',
  coverFile: null,
  coverFileName: '',
  lyricsFile: null,
  lyricsFileName: ''
})
const editingMusic = ref(null)
const searchQuery = ref('')
const isLoading = ref(false)

// 模态框引用
const editModalRef = ref(null)
const addModalRef = ref(null)
const modalPosition = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragOffset = ref({ x: 0, y: 0 })

// 获取音乐列表
const fetchMusicList = async () => {
  isLoading.value = true
  try {
    const timestamp = Date.now()
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/list?t=${timestamp}`, {
      method: 'GET',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
      }
    })

    const data = await response.json()
    if (data.success) {
      // 将API返回的音乐数据转换为前端可用的格式
      musicList.value = data.data.map(music => ({
        ...music,
        uploadTime: new Date(music.createdAt) // 转换日期格式
      }))
      filteredMusicList.value = [...musicList.value]
      // 计算总页数
      totalPages.value = Math.ceil(filteredMusicList.value.length / pageSize.value)
      // 重置到第一页
      currentPage.value = 1
    } else {
      console.error('获取音乐列表失败:', data.message)
      toast.error(data.message || '获取音乐列表失败')
    }
  } catch (error) {
    console.error('获取音乐列表时出错:', error)
    toast.error('获取音乐列表时出错')
  } finally {
    isLoading.value = false
  }
}

// 添加音乐
const addMusic = async () => {
  if (!newMusic.value.title || !newMusic.value.artist || !newMusic.value.language) {
    toast.error('请填写音乐名称、艺术家和语言')
    return
  }
  
  if (!newMusic.value.file) {
    toast.error('请选择音乐文件')
    return
  }
  
  // 验证歌词文件必填
  if (!newMusic.value.lyricsFile) {
    toast.error('请选择歌词文件（必填项）')
    return
  }
  
  // 创建FormData对象，用于上传文件
  const formData = new FormData()
  formData.append('title', newMusic.value.title)
  formData.append('artist', newMusic.value.artist)
  formData.append('language', newMusic.value.language)
  formData.append('tags', newMusic.value.tags || '')
  formData.append('album', newMusic.value.album || '未知专辑')
  formData.append('duration', newMusic.value.duration || 0)
  formData.append('uploadUserId', newMusic.value.uploadUserId || 0)
  formData.append('musicFile', newMusic.value.file)
  formData.append('lyricsFile', newMusic.value.lyricsFile)
  
  if (newMusic.value.coverFile) {
    formData.append('coverFile', newMusic.value.coverFile)
  }
  
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/upload`, {
      method: 'POST',
      headers: {
        // 注意：使用FormData时，不要设置Content-Type，浏览器会自动设置
        'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
      },
      body: formData
    })
    
    const data = await response.json()
    if (data.success) {
      // 添加新音乐到列表顶部
      musicList.value.unshift({
        ...data.data,
        uploadTime: new Date(data.data.createdAt)
      })
      filteredMusicList.value = [...musicList.value]
      resetForm()
      toast.success('添加音乐成功')
    } else {
      console.error('添加音乐失败:', data.message)
      toast.error(data.message || '添加音乐失败')
    }
  } catch (error) {
    console.error('添加音乐时出错:', error)
    toast.error('添加音乐时出错')
  }
}

// 重置表单
const resetForm = () => {
  newMusic.value = {
    title: '',
    artist: '',
    language: '',
    album: '',
    duration: 0,
    filePath: '',
    uploadUserId: 0,
    file: null,
    fileName: '',
    coverFile: null,
    coverFileName: '',
    lyricsFile: null,
    lyricsFileName: ''
  }
  showAddForm.value = false
}

// 关闭添加音乐模态框
const closeAddModal = () => {
  resetForm()
}

// 编辑音乐
const editMusic = async (music) => {
  editingMusic.value = { ...music, lyricsFile: null, lyricsFileName: '', lyricsPath: `\\Music\\lyrics\\${music.id}.lrc` } // 复制音乐对象以避免直接修改原数据
}

// 关闭编辑悬浮窗
const closeEditModal = () => {
  editingMusic.value = null
}

// 开始拖动悬浮窗
const startDrag = (e) => {
  if (e.target.classList.contains('close-btn')) return // 防止在关闭按钮上拖动
  
  isDragging.value = true
  const rect = editModalRef.value.getBoundingClientRect()
  // 计算鼠标相对于悬浮窗的偏移量
  dragOffset.value = {
    x: e.clientX - rect.x,  // 使用rect.x而不是rect.left
    y: e.clientY - rect.y   // 使用rect.y而不是rect.top
  }
  
  // 添加事件监听器
  const handleMouseMove = (e) => {
    if (!isDragging.value) return
    
    // 根据鼠标位置和偏移量计算新位置
    let x = e.clientX - dragOffset.value.x
    let y = e.clientY - dragOffset.value.y
    
    // 确保悬浮窗在屏幕范围内
    const maxX = window.innerWidth - editModalRef.value.offsetWidth
    const maxY = window.innerHeight - editModalRef.value.offsetHeight
    
    // 确保不小于0，不大于最大值
    x = Math.max(0, Math.min(x, maxX))
    y = Math.max(0, Math.min(y, maxY))
    
    modalPosition.value = { x, y }
  }
  
  const handleMouseUp = () => {
    isDragging.value = false
    // 移除事件监听器
    document.removeEventListener('mousemove', handleMouseMove)
    document.removeEventListener('mouseup', handleMouseUp)
  }
  
  // 添加事件监听器
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
  
  // 阻止默认行为（如选择文本）
  e.preventDefault()
}

// 停止拖动
const stopDrag = () => {
  isDragging.value = false
}

// 保存编辑
const saveEdit = async () => {
  if (!editingMusic.value.title || !editingMusic.value.artist || !editingMusic.value.language) {
    toast.error('请填写音乐名称、艺术家和语言')
    return
  }
  
  // 验证歌词文件必填
  if (!editingMusic.value.lyricsFile) {
    toast.error('请选择歌词文件（必填项）')
    return
  }
  
  try {
    // 创建FormData对象，用于上传文件
    const formData = new FormData()
    formData.append('id', editingMusic.value.id)
    formData.append('title', editingMusic.value.title)
    formData.append('artist', editingMusic.value.artist)
    formData.append('language', editingMusic.value.language)
    formData.append('tags', editingMusic.value.tags || '')
    formData.append('album', editingMusic.value.album || '未知专辑')
    formData.append('duration', editingMusic.value.duration || 0)
    formData.append('uploadUserId', editingMusic.value.uploadUserId || 0)
    formData.append('lyricsFile', editingMusic.value.lyricsFile)
    
    if (editingMusic.value.file) {
      formData.append('musicFile', editingMusic.value.file)
    }
    
    if (editingMusic.value.coverFile) {
      formData.append('coverFile', editingMusic.value.coverFile)
    }
    
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/upload`, {
      method: 'PUT',
      headers: {
        // 注意：使用FormData时，不要设置Content-Type，浏览器会自动设置
        'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
      },
      body: formData
    })
    
    const data = await response.json()
    if (data.success) {
      // 更新本地列表
      const index = musicList.value.findIndex(m => m.id === editingMusic.value.id)
      if (index !== -1) {
        musicList.value[index] = {
          ...data.data,
          uploadTime: new Date(data.data.createdAt)
        }
        filteredMusicList.value = [...musicList.value]
      }
      editingMusic.value = null
      toast.success('编辑音乐成功')
    } else {
      console.error('编辑音乐失败:', data.message)
      toast.error(data.message || '编辑音乐失败')
    }
  } catch (error) {
    console.error('编辑音乐时出错:', error)
    toast.error('编辑音乐时出错')
  }
}

// 取消编辑
const cancelEdit = () => {
  editingMusic.value = null
}

// 删除音乐
const deleteMusic = async (id) => {
  if (confirm('确定要删除这首音乐吗？此操作不可撤销。')) {
    try {
      const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/delete/${id}`, {
        method: 'DELETE',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
        }
      })
      
      const data = await response.json()
      if (data.success) {
        musicList.value = musicList.value.filter(music => music.id !== id)
        filteredMusicList.value = [...musicList.value]
        toast.success('删除音乐成功')
      } else {
        console.error('删除音乐失败:', data.message)
        toast.error(data.message || '删除音乐失败')
      }
    } catch (error) {
      console.error('删除音乐时出错:', error)
      toast.error('删除音乐时出错')
    }
  }
}

// 格式化日期
const formatDate = (date) => {
  if (!date) return ''
  return new Date(date).toLocaleDateString('zh-CN')
}

// 格式化音乐时长为 xx分xx秒 格式
const formatDuration = (duration) => {
  if (!duration || duration < 0) return '0分0秒'
  
  const minutes = Math.floor(duration / 60)
  const seconds = duration % 60
  
  return `${minutes}分${seconds}秒`
}

// 使用API搜索音乐
const searchMusicAPI = async (query) => {
  try {
    const response = await fetch(`${API_CONFIG.BASE_URL}/api/music/search`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('adminToken')}`
      },
      body: JSON.stringify({ query: query })
    })
    
    const data = await response.json()
    if (data.success && data.results) {
      // 将API返回的音乐数据转换为前端可用的格式
      return data.results.map(music => ({
        ...music,
        uploadTime: new Date(music.createdAt) // 转换日期格式
      }))
    } else {
      console.error('搜索音乐失败:', data.message)
      return []
    }
  } catch (error) {
    console.error('搜索音乐时出错:', error)
    return []
  }
}

// 监听搜索查询变化
const updateSearchResults = async () => {
  if (!searchQuery.value.trim()) {
    filteredMusicList.value = musicList.value
  } else {
    // 可以选择使用API搜索或本地搜索
    // 这里我们使用本地搜索，因为已经获取了所有音乐数据
    const query = searchQuery.value.toLowerCase()
    filteredMusicList.value = musicList.value.filter(music =>
      music.title.toLowerCase().includes(query) ||
      music.artist.toLowerCase().includes(query)
    )
  }
  // 重新计算总页数并重置到第一页
  totalPages.value = Math.ceil(filteredMusicList.value.length / pageSize.value)
  currentPage.value = 1
}

// 监听搜索查询变化
searchQuery.value = ''
updateSearchResults()

// 计算当前页的音乐列表
const paginatedMusicList = () => {
  const startIndex = (currentPage.value - 1) * pageSize.value
  const endIndex = startIndex + pageSize.value
  return filteredMusicList.value.slice(startIndex, endIndex)
}

// 上一页
const prevPage = () => {
  if (currentPage.value > 1) {
    currentPage.value--
  }
}

// 下一页
const nextPage = () => {
  if (currentPage.value < totalPages.value) {
    currentPage.value++
  }
}

// 跳转到指定页
const goToPage = (page) => {
  if (page >= 1 && page <= totalPages.value) {
    currentPage.value = page
  }
}

// 获取显示的页码（用于处理页码显示逻辑）
const getDisplayPage = (index) => {
  const maxVisible = 5
  if (totalPages.value <= maxVisible) {
    return index
  }

  const half = Math.floor(maxVisible / 2)
  let start = Math.max(1, currentPage.value - half)
  let end = Math.min(totalPages.value, start + maxVisible - 1)

  if (end - start < maxVisible - 1) {
    start = Math.max(1, end - maxVisible + 1)
  }

  return start + index - 1
}

// 获取音乐封面URL
const getCoverUrl = (musicId) => {
  // 返回新的API端点，通过音乐ID获取封面
  return `${API_CONFIG.BASE_URL}/api/music/cover/${musicId}`
}

// 处理封面图片加载错误
const handleImageError = (event) => {
  // 如果图片加载失败，使用后端API的默认图标
  event.target.src = `${API_CONFIG.BASE_URL}/api/music/cover/`;
}

const logout = () => {
  localStorage.removeItem('adminToken')
  localStorage.removeItem('isAdminLoggedIn')
  router.push('/admin/login')
}
</script>

<style scoped>
.admin-layout {
  display: flex;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4edf5 100%);
}

.admin-main-content {
  flex: 1;
  margin-left: 250px; /* 侧边栏宽度 */
  padding: 20px;
  transition: margin-left 0.3s ease;
  min-height: calc(100vh - 40px);
  display: flex;
  flex-direction: column;
}

.admin-header {
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  margin-bottom: 20px;
  flex-shrink: 0; /* 防止头部被压缩 */
}

.admin-user-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.logout-button {
  background: linear-gradient(135deg, rgba(220, 20, 60, 0.8), rgba(255, 99, 71, 0.8));
  color: white;
  border: none;
  border-radius: 20px;
  padding: 8px 16px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  box-shadow: 0 4px 10px rgba(220, 20, 60, 0.3);
}

.logout-button:hover {
  background: linear-gradient(135deg, rgba(190, 10, 50, 0.9), rgba(235, 79, 51, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(220, 20, 60, 0.5);
}

.admin-content-wrapper {
  flex: 1; /* 让内容区域占据剩余空间 */
  padding: 0 20px;
  min-height: calc(100vh - 140px); /* 增加最小高度，考虑头部和边距 */
  height: auto; /* 允许自适应高度 */
  overflow: auto; /* 如果内容过多，允许滚动 */
}

.admin-subpage {
  padding: 20px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 15px;
  box-shadow: 0 8px 32px rgba(31, 38, 135, 0.2);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.admin-subpage h2 {
  color: #6a5acd;
  margin: 0 0 20px 0;
  font-size: 1.5rem;
}

.admin-controls {
  margin-bottom: 20px;
  display: flex;
  justify-content: flex-end;
}

.add-btn {
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(138, 43, 226, 0.8));
  color: white;
  border: none;
  border-radius: 20px;
  padding: 10px 20px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  box-shadow: 0 4px 10px rgba(106, 90, 205, 0.3);
}

.add-btn:hover {
  background: linear-gradient(135deg, rgba(86, 70, 185, 0.9), rgba(118, 23, 206, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(106, 90, 205, 0.5);
}

.add-music-form {
  background: rgba(255, 255, 255, 0.2);
  padding: 20px;
  border-radius: 10px;
  margin-bottom: 30px;
}

.add-music-form h3 {
  color: #6a5acd;
  margin: 0 0 15px 0;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
  gap: 15px;
  margin-bottom: 15px;
}

.form-group {
  display: flex;
  flex-direction: column;
}

.form-group label {
  margin-bottom: 8px;
  color: #6a5acd;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.95rem;
}

.form-group input {
  padding: 12px 15px;
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(106, 90, 205, 0.2);
  color: #333;
  font-size: 1rem;
  transition: all 0.3s ease;
  width: 100%;
  box-sizing: border-box;
}

.form-group input[type="file"] {
  padding: 10px;
  background: rgba(240, 240, 255, 0.4);
  border: 2px dashed rgba(106, 90, 205, 0.3);
  cursor: pointer;
}

.form-group input[type="file"]:hover {
  background: rgba(230, 230, 250, 0.5);
  border: 2px dashed rgba(106, 90, 205, 0.5);
}

.form-group input:focus {
  outline: none;
  border: 1px solid rgba(106, 90, 205, 0.5);
  box-shadow: 0 0 0 3px rgba(106, 90, 205, 0.2);
  background: rgba(255, 255, 255, 0.4);
}

.form-group input[readonly] {
  background: rgba(240, 240, 240, 0.5);
  cursor: not-allowed;
  color: #666;
}

.form-group input[readonly]:focus {
  outline: none;
  border: 1px solid rgba(200, 200, 200, 0.3);
  box-shadow: none;
  background: rgba(240, 240, 240, 0.5);
}

.form-group textarea {
  padding: 12px 15px;
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(106, 90, 205, 0.2);
  color: #333;
  font-size: 1rem;
  transition: all 0.3s ease;
  width: 100%;
  box-sizing: border-box;
  font-family: inherit;
  resize: vertical;
  min-height: 120px;
}

.form-group textarea:focus {
  outline: none;
  border: 1px solid rgba(106, 90, 205, 0.5);
  box-shadow: 0 0 0 3px rgba(106, 90, 205, 0.2);
  background: rgba(255, 255, 255, 0.4);
}

.form-hint {
  font-size: 0.8rem;
  color: #888;
  margin-top: 5px;
  font-style: italic;
}

.form-group input::file-selector-button {
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(138, 43, 226, 0.8));
  color: white;
  padding: 8px 16px;
  border-radius: 6px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s ease;
}

.form-group input::file-selector-button:hover {
  background: linear-gradient(135deg, rgba(92, 75, 123, 0.9), rgba(122, 91, 192, 0.9));
  transform: scale(1.05);
}

.file-info {
  margin-top: 5px;
  font-size: 0.85rem;
  color: #6a5acd;
  padding: 5px;
  background: rgba(106, 90, 205, 0.1);
  border-radius: 5px;
  word-break: break-all;
}



.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.primary-btn {
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(138, 43, 226, 0.8));
  color: white;
  border: none;
  border-radius: 20px;
  padding: 10px 20px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  box-shadow: 0 4px 10px rgba(106, 90, 205, 0.3);
}

.primary-btn:hover {
  background: linear-gradient(135deg, rgba(86, 70, 185, 0.9), rgba(118, 23, 206, 0.9));
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(106, 90, 205, 0.5);
}

.secondary-btn {
  background: rgba(149, 165, 166, 0.2);
  color: #7f8c8d;
  border: none;
  border-radius: 20px;
  padding: 10px 20px;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  box-shadow: 0 4px 10px rgba(149, 165, 166, 0.3);
}

.secondary-btn:hover {
  background: rgba(127, 140, 141, 0.3);
  transform: translateY(-2px);
  box-shadow: 0 6px 15px rgba(149, 165, 166, 0.5);
}

.music-list-section {
  margin-top: 20px;
}

.music-list-section h3 {
  color: #6a5acd;
  margin: 0 0 15px 0;
  font-size: 1.2rem;
}

.search-filter {
  margin-bottom: 20px;
}

.search-input {
  width: 100%;
  max-width: 400px;
  padding: 10px 15px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.25);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
  color: #333;
  font-size: 1rem;
  transition: all 0.3s ease;
}

.search-input:focus {
  outline: none;
  border: 1px solid rgba(106, 90, 205, 0.5);
  box-shadow: 0 0 0 2px rgba(106, 90, 205, 0.2);
  background: rgba(255, 255, 255, 0.35);
}

.table-container {
  overflow-x: auto;
}

.music-table {
  width: 100%;
  border-collapse: collapse;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  overflow: hidden;
}

.music-table th,
.music-table td {
  padding: 12px 15px;
  text-align: left;
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
}

.music-table th:first-child,
.music-table td:first-child {
  text-align: center;
  width: 80px;
}

.music-table th {
  background: rgba(106, 90, 205, 0.3);
  color: #6a5acd;
  font-weight: 600;
}

.music-table tr:last-child td {
  border-bottom: none;
}

.music-table tr:hover {
  background: rgba(106, 90, 205, 0.1);
}

.action-btn {
  padding: 6px 12px;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 0.8rem;
  margin-right: 5px;
  transition: all 0.3s ease;
}

.edit-btn {
  background: rgba(46, 204, 113, 0.2);
  color: #2ecc71;
}

.edit-btn:hover {
  background: rgba(46, 204, 113, 0.3);
}

.delete-btn {
  background: rgba(231, 76, 60, 0.2);
  color: #e74c3c;
}

.delete-btn:hover {
  background: rgba(231, 76, 60, 0.3);
}

.loading {
  text-align: center;
  padding: 20px;
  color: #6a5acd;
  font-size: 1.1rem;
}

.no-data {
  text-align: center;
  padding: 40px;
  color: #7f8c8d;
  font-size: 1.1rem;
}

/* 编辑悬浮窗样式 */
.edit-modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: rgba(0, 0, 0, 0.6);
  display: flex;
  justify-content: center;
  align-items: flex-start;  /* 改为flex-start，并配合padding-top定位 */
  padding-top: 5vh;  /* 继续往上移动，从8vh减少到5vh */
  z-index: 9999;
}

.edit-modal {
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.95), rgba(240, 240, 255, 0.95));
  border-radius: 20px;
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  min-width: 700px;
  min-height: 450px;
  display: flex;
  flex-direction: column;
  position: relative; /* 使用相对定位，让其在overlay中居中 */
  cursor: default;
  z-index: 10000;
  overflow: hidden;
  animation: modalSlideIn 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.edit-modal-wide {
  width: 750px;
  max-width: 90vw;
}

.modal-content.horizontal-layout {
  display: flex;
  flex-direction: row;
  gap: 20px;
  padding: 25px;
}

.form-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.left-column {
  border-right: 1px solid rgba(106, 90, 205, 0.2);
  padding-right: 15px;
}

.right-column {
  padding-left: 15px;
}

@keyframes modalSlideIn {
  from {
    opacity: 0;
    transform: translateY(-40px) scale(0.95);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

/* Vue过渡动画 */
.modal-enter-active, .modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from, .modal-leave-to {
  opacity: 0;
}

.modal-enter-active .edit-modal, .modal-leave-active .edit-modal {
  transition: transform 0.3s ease;
}

.modal-enter-from .edit-modal {
  transform: scale(0.8);
}

.modal-leave-to .edit-modal {
  transform: scale(0.8);
}

.modal-header {
  padding: 20px 25px;
  border-bottom: 1px solid rgba(106, 90, 205, 0.2);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.15), rgba(138, 43, 226, 0.15));
  border-radius: 20px 20px 0 0;
  cursor: default; /* 移除拖动光标 */
}

.modal-header h3 {
  margin: 0;
  color: #6a5acd;
  font-size: 1.4rem;
  font-weight: 600;
  display: flex;
  align-items: center;
  gap: 10px;
}

.modal-header h3::before {
  content: "🎵";
  font-size: 1.2rem;
}

.close-btn {
  background: rgba(255, 255, 255, 0.3);
  border: none;
  color: #6a5acd;
  font-size: 1.6rem;
  cursor: pointer;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: all 0.3s ease;
  backdrop-filter: blur(5px);
}

.close-btn:hover {
  background: rgba(231, 76, 60, 0.2);
  color: #e74c3c;
  transform: rotate(90deg);
}

.modal-content {
  flex: 1;
}

.modal-content .form-group {
  margin-bottom: 0;
}

.modal-actions {
  padding: 20px 25px;
  border-top: 1px solid rgba(106, 90, 205, 0.15);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  background: linear-gradient(135deg, rgba(240, 240, 255, 0.3), rgba(230, 230, 250, 0.3));
  border-radius: 0 0 20px 20px;
}

/* Vue过渡动画 */
.modal-enter-active, .modal-leave-active {
  transition: opacity 0.3s ease;
}

.modal-enter-from, .modal-leave-to {
  opacity: 0;
}

.modal-enter-active .edit-modal, .modal-leave-active .edit-modal {
  transition: transform 0.3s ease;
}

.modal-enter-from .edit-modal {
  transform: scale(0.8);
}

.modal-leave-to .edit-modal {
  transform: scale(0.8);
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-main-content {
    margin-left: 0;
    padding: 10px;
  }
  
  .admin-layout {
    flex-direction: column;
  }
  
  .edit-modal {
    min-width: 300px;
    margin: 10px;
    max-width: calc(100% - 20px);
  }
}

/* 美化选择框样式 */
.select-wrapper {
  position: relative;
  width: 100%;
}

.styled-select {
  width: 100%;
  padding: 12px 15px;
  padding-right: 40px; /* 为自定义下拉箭头留出空间 */
  border: none;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.3);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(106, 90, 205, 0.2);
  color: #333;
  font-size: 1rem;
  transition: all 0.3s ease;
  appearance: none; /* 隐藏默认下拉箭头 */
  cursor: pointer;
  box-sizing: border-box;
}

.styled-select:hover {
  background: rgba(255, 255, 255, 0.4);
  border-color: rgba(106, 90, 205, 0.5);
}

.styled-select:focus {
  outline: none;
  border: 1px solid rgba(106, 90, 205, 0.5);
  box-shadow: 0 0 0 3px rgba(106, 90, 205, 0.2);
  background: rgba(255, 255, 255, 0.4);
}

/* 自定义下拉箭头 */
.select-wrapper::after {
  content: "▼";
  position: absolute;
  top: 50%;
  right: 15px;
  transform: translateY(-50%);
  pointer-events: none; /* 确保箭头不影响点击事件 */
  color: #6a5acd;
  font-size: 0.7rem;
  transition: transform 0.3s ease;
}

/* 当选择框获得焦点时旋转箭头 */
.styled-select:focus + .select-wrapper::after {
  transform: translateY(-50%) rotate(180deg);
}

/* 封面单元格样式 */
.cover-cell {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 50px;
}

.music-cover-table {
  width: 40px;
  height: 40px;
  object-fit: cover;
  border-radius: 4px;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.no-cover-table {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f0;
  border-radius: 4px;
  color: #999;
  font-size: 1.2rem;
}

/* 分页控件样式 */
.pagination-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 20px;
  padding: 15px 20px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 10px;
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.18);
}

.pagination-info {
  color: #6a5acd;
  font-size: 0.9rem;
  font-weight: 500;
}

.pagination-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pagination-btn {
  padding: 8px 16px;
  border: none;
  border-radius: 8px;
  background: rgba(106, 90, 205, 0.2);
  color: #6a5acd;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  font-weight: 500;
}

.pagination-btn:hover:not(:disabled) {
  background: rgba(106, 90, 205, 0.4);
  transform: translateY(-2px);
}

.pagination-btn:disabled {
  background: rgba(200, 200, 200, 0.2);
  color: #999;
  cursor: not-allowed;
}

.pagination-pages {
  display: flex;
  gap: 5px;
}

.pagination-page-btn {
  min-width: 36px;
  height: 36px;
  padding: 0 12px;
  border: none;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.3);
  color: #6a5acd;
  cursor: pointer;
  font-size: 0.9rem;
  transition: all 0.3s ease;
  font-weight: 500;
}

.pagination-page-btn:hover {
  background: rgba(106, 90, 205, 0.2);
  transform: translateY(-2px);
}

.pagination-page-btn.active {
  background: linear-gradient(135deg, rgba(106, 90, 205, 0.8), rgba(138, 43, 226, 0.8));
  color: white;
  box-shadow: 0 4px 10px rgba(106, 90, 205, 0.3);
}

.pagination-page-btn.active:hover {
  background: linear-gradient(135deg, rgba(86, 70, 185, 0.9), rgba(118, 23, 206, 0.9));
  transform: translateY(-2px);
}

/* 响应式分页 */
@media (max-width: 768px) {
  .pagination-container {
    flex-direction: column;
    gap: 15px;
  }

  .pagination-controls {
    flex-wrap: wrap;
    justify-content: center;
  }

  .pagination-info {
    text-align: center;
  }
}
</style>