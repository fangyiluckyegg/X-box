<template>
  <div>
    <div class="text-center">
      <h2>比对工具</h2>
      <p>支持Execl表格之间的比对，文件最大200MB</p>
    </div>

    <div>
      <!-- 基础数据表 -->
      <div>
        <div class="card-body">
          <h3>一、单位名称比对工具</h3>
          <h4>基础数据表：文件名设置成names_bak.xlsx</h4>
          <h4>比对数据表：文件名设置成names_new.xlsx</h4>
          <h4>一个一个上传后，在点击启动比对按钮，会生成比对结果文件names_budong.xlsx</h4>          
          <input type="file" accept=".xls,.xlsx,.csv" @change="handleFileSelect('doc', $event)"/>
          <div v-if="files.doc" >已选择: {{ files.doc.name }}
            <div>{{ formatFileSize(files.doc.size) }}</div>
          </div>
          
          <!-- 上传进度显示 -->
          <div v-if="uploadProgress.doc.active" class="space-y-2">
            <div class="flex justify-between text-sm">
              <span>{{ uploadProgress.doc.percentage }}%</span>
              <span>{{ formatFileSize(uploadProgress.doc.loaded) }} / {{ formatFileSize(uploadProgress.doc.total) }}</span>
            </div>
            <progress 
              class="progress progress-primary w-full" 
              :value="uploadProgress.doc.percentage" 
              max="100"
            ></progress>
            <div class="text-xs text-gray-500">
              {{ uploadProgress.doc.speed }} KB/s - {{ uploadProgress.doc.timeRemaining }}
            </div>
          </div>
          
          <button 
            @click="uploadFile('doc')"
            :disabled="!files.doc || loading.doc"
            :class="{ 'loading': loading.doc }"
          >
            {{ loading.doc ? '上传中...' : '上传' }}
          </button>
          <div v-if="results.doc" class="text-xs text-success">
            ✓ {{ results.doc }}
          </div>
        </div>
      </div>

      <p></p>
      <!-- 启动比对 -->
      <div>
          <button @click="startRemoteProcess">启动比对</button>
          <p v-if="statusMessage">{{ statusMessage }}</p>
      </div>

      <!--  结果下载 -->   
      <div>
        <button size="small" type="primary" @click="downloadF">结果下载</button>
      </div>



      <p></p>
      <!-- PDF文件上传工具 -->
      <div class="card bg-base-200">
        <div class="card-body">
          <h3 class="card-title text-lg">二、PDF文件上传工具</h3>
          <input 
            type="file" 
            class="file-input file-input-bordered w-full" 
            accept=".pdf"
            @change="handleFileSelect('soc', $event)"
          />
          <div v-if="files.soc" class="text-sm text-gray-600">
            已选择: {{ files.soc.name }}
            <div class="text-xs text-gray-500">{{ formatFileSize(files.soc.size) }}</div>
          </div>
          
          <!-- 上传进度显示 -->
          <div v-if="uploadProgress.soc.active" class="space-y-2">
            <div class="flex justify-between text-sm">
              <span>{{ uploadProgress.soc.percentage }}%</span>
              <span>{{ formatFileSize(uploadProgress.soc.loaded) }} / {{ formatFileSize(uploadProgress.soc.total) }}</span>
            </div>
            <progress 
              class="progress progress-primary w-full" 
              :value="uploadProgress.soc.percentage" 
              max="100"
            ></progress>
            <div class="text-xs text-gray-500">
              {{ uploadProgress.soc.speed }} KB/s - {{ uploadProgress.soc.timeRemaining }}
            </div>
          </div>
          
          <button 
            class="btn btn-primary btn-sm" 
            @click="uploadFile('soc')"
            :disabled="!files.soc || loading.soc"
            :class="{ 'loading': loading.soc }"
          >
            {{ loading.soc ? '上传中...' : '上传' }}
          </button>
          <div v-if="results.soc" class="text-xs text-success">
            ✓ {{ results.soc }}
          </div>
        </div>
      </div>
    </div>



    <div v-if="error" class="alert alert-error">
      <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ error }}</span>
    </div>

    <div v-if="success" class="alert alert-success">
      <svg xmlns="http://www.w3.org/2000/svg" class="stroke-current shrink-0 h-6 w-6" fill="none" viewBox="0 0 24 24">
        <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span>{{ success }}</span>
    </div>
  </div>
</template>

<script>
import axios from 'axios'

export default {
  name: 'MultiUpload',
  data() {
    return {
      files: {
        doc: null,
        soc: null
      },
      loading: {
        doc: false,
        soc: false
      },
      uploadProgress: {
        doc: {
          active: false,
          percentage: 0,
          loaded: 0,
          total: 0,
          speed: 0,
          timeRemaining: ''
        },
        soc: {
          active: false,
          percentage: 0,
          loaded: 0,
          total: 0,
          speed: 0,
          timeRemaining: ''
        }
      },
      results: {
        doc: '',
        soc: ''
      },
      position: '',
      positionResponsibility: '',
      submitting: false,
      error: null,
      success: null,
      
      statusMessage: '' // 用于显示状态信息
    }
  },
  computed: {
    canSubmit() {
      return this.position && this.positionResponsibility.trim()
    }
  },



  methods: {  
    formatFileSize(bytes) {
      if (bytes === 0) return '0 B'
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
    },

    handleFileSelect(type, event) {
      const file = event.target.files[0]
      if (file) {
        // 验证文件类型
        const allowedTypes = {
          doc: ['.xls', '.xlsx', '.csv'],
          soc: ['.pdf']
        }
        
        const fileExtension = '.' + file.name.split('.').pop().toLowerCase()
        const isAllowed = allowedTypes[type].some(ext => fileExtension === ext)
        
        if (!isAllowed) {
          this.error = `请选择${type === 'video' ? 'MP4/AVI/MOV' : 'PDF' }格式的文件`
          return
        }
        
        // 验证文件大小
        if (file.size > 200 * 1024 * 1024) {
          this.error = '文件大小不能超过200MB'
          return
        }
        
        this.files[type] = file
        this.error = null
        this.success = null
        
        // 重置该类型的上传进度
        this.resetUploadProgress(type)
      }
    },

    resetUploadProgress(type) {
      this.uploadProgress[type] = {
        active: false,
        percentage: 0,
        loaded: 0,
        total: 0,
        speed: 0,
        timeRemaining: ''
      }
    },

    async uploadFile(type) {
      if (!this.files[type]) return

      this.loading[type] = true
      this.error = null
      this.success = null
      
      // 初始化上传进度
      this.uploadProgress[type] = {
        active: true,
        percentage: 0,
        loaded: 0,
        total: this.files[type].size,
        speed: 0,
        timeRemaining: '计算中...'
      }

      const formData = new FormData()
      formData.append('file', this.files[type])

      const startTime = Date.now()
      let lastLoaded = 0

      try {
        let endpoint = '/api/positionLearning/docUpload/uploadDoc'

        const response = await axios.post(endpoint, formData, {
          headers: {
            'Content-Type': 'multipart/form-data'
          },
          onUploadProgress: (progressEvent) => {
            const { loaded, total } = progressEvent
            const currentTime = Date.now()
            const timeElapsed = (currentTime - startTime) / 1000 // 秒
            
            // 计算百分比
            const percentage = Math.round((loaded / total) * 100)
            
            // 计算速度 (KB/s)
            const speed = Math.round((loaded - lastLoaded) / (currentTime - startTime) * 1000 / 1024)
            lastLoaded = loaded
            
            // 计算剩余时间
            const remainingBytes = total - loaded
            const timeRemaining = speed > 0 
              ? this.formatTimeRemaining(remainingBytes / (speed * 1024))
              : '计算中...'

            // 更新进度
            this.uploadProgress[type] = {
              active: true,
              percentage,
              loaded,
              total,
              speed: speed || 0,
              timeRemaining
            }
          }
        })

        if (response.data.code === 200) {
          this.results[type] = this.files[type].name + ' / ' + response.data.data
          this.success = `${type === 'doc' ? '基础数据表' : type === 'soc' ? '比对数据表' : '教学视频'}上传成功！`
          
          // 上传完成后延迟隐藏进度条
          setTimeout(() => {
            this.uploadProgress[type].active = false
          }, 1000)
        } else {
          this.error = response.data.message || '上传失败'
          this.uploadProgress[type].active = false
        }
      } catch (error) {
        this.error = error.response?.data?.message || '上传失败，请重试'
        this.uploadProgress[type].active = false
      } finally {
        this.loading[type] = false
      }
    },

    formatTimeRemaining(seconds) {
      if (seconds < 60) {
        return `${Math.ceil(seconds)}秒`
      } else if (seconds < 3600) {
        return `${Math.ceil(seconds / 60)}分钟`
      } else {
        return `${Math.ceil(seconds / 3600)}小时${Math.ceil((seconds % 3600) / 60)}分钟`
      }
    },

    resetForm() {
      this.files = { doc: null, soc: null, video: null }
      this.results = { doc: '', soc: '', video: '' }
      this.position = ''
      this.positionResponsibility = ''
      
      // 清空文件输入
      const inputs = document.querySelectorAll('input[type="file"]')
      inputs.forEach(input => input.value = '')
    },

    startRemoteProcess() {
      this.statusMessage = '正在启动...'; // 显示加载状态
      axios.post('/api/bibutong') // 假设你的后端API端点为/api/start-process
      //axios.post('/api/positionLearning/docUpload/uploadDoc') // 假设你的后端API端点为/api/start-process
        .then(response => {
          // 请求成功处理
          this.statusMessage = '启动成功！';
          console.log('Process started successfully', response.data);
        })
        .catch(error => {
          // 请求失败处理
          this.statusMessage = '启动失败，请重试。';
          console.error('Error starting process', error);
        });
    },

    downloadF() {
      axios({
        url: '/api/positionLearning/download',
        method: 'get',
        responseType: 'blob'
      }).then(response => {
        // 创建blob对象
        const blob = new Blob([response.data])
    
        // 创建下载链接
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
    
        // 设置文件名
        link.download = 'names_budong.xlsx'
    
        // 触发下载
        link.click()
    
        // 清理资源
        window.URL.revokeObjectURL(url)
    
      }).catch(error => {
      console.error('下载失败:', error)
      alert('文件下载失败')
    })
  }
  }
}
</script>