<template>
  <div class="compare-page">
    <div style="background:#ffefef;padding:8px;border:1px solid #ffd0d0;color:#a00;margin-bottom:12px;">
      调试：组件已渲染（若看不到此条，请查看控制台错误）
    </div>
    <!-- 操作栏：双Excel上传 + 功能按钮 -->
    <div class="operate-row">
      <!-- 原始数据上传 -->
      <div class="upload-item">
        <span class="label">原始数据</span>
        <el-upload
          ref="originUploadRef"
          class="upload-box"
          action=""
          :http-request="handleOriginUpload"
          :before-upload="checkFile"
          accept=".xlsx,.xls"
          :limit="1"
          list-type="text"
        >
          <template #default>
            <div class="upload-placeholder">请选择原始数据</div>
          </template>
        </el-upload>
        <span class="file-name" v-if="originFile">
          已选择：{{ originFile.name }}
          <el-button type="text" size="small" @click="clearOrigin">清除</el-button>
        </span>
      </div>

      <!-- 比对数据上传 -->
      <div class="upload-item">
        <span class="label">比对数据</span>
        <el-upload
          ref="newUploadRef"
          class="upload-box"
          action=""
          :http-request="handleNewUpload"
          :before-upload="checkFile"
          accept=".xlsx,.xls"
          :limit="1"
          list-type="text"
        >
          <template #default>
            <div class="upload-placeholder">请输入新比对数据</div>
          </template>
        </el-upload>
        <span class="file-name" v-if="newFile">
          已选择：{{ newFile.name }}
          <el-button type="text" size="small" @click="clearNew">清除</el-button>
        </span>
      </div>

      <!-- 功能按钮 -->
      <div class="actions">
        <el-button
          type="primary"
          size="large"
          icon="el-icon-search"
          :disabled="!originFile || !newFile || loading"
          @click="uploadFiles"
        >
          文件上传
        </el-button>

        <el-button
          type="primary"
          size="large"
          icon="el-icon-search"
          :disabled="!originFile || !newFile || loading"
          @click="startCompare"
        >
          启动比对
        </el-button>

        <el-button
          size="large"
          icon="el-icon-download"
          :disabled="!hasCompareResult || loading"
          @click="downloadResult"
        >
          结果下载
        </el-button>
      </div>
    </div>

    <!-- 比对结果表格（可选） -->
    <div v-if="compareResult.length" class="result-table">
      <h3>比对差异结果</h3>
      <el-table border :data="compareResult" style="width:100%">
        <el-table-column label="关键字段" prop="key" />
        <el-table-column label="原始值" prop="originVal" />
        <el-table-column label="新值" prop="newVal" />
        <el-table-column label="差异类型" prop="diffType" />
      </el-table>
    </div>
  </div>
</template>

<script>
import * as XLSX from 'xlsx'
import { Message, Loading } from 'element-ui'
import { compareExcelApi, downloadCompareResultApi } from '@/api/compare'

export default {
  name: 'EmployeeCompare',
  data() {
    return {
      originFile: null,
      newFile: null,
      loading: false,
      hasCompareResult: false,
      compareResult: []
    }
  },
  methods: {
    checkFile(file) {
      const suffix = file.name.split('.').pop()
      const allow = ['xlsx', 'xls']
      if (!allow.includes(suffix)) {
        Message.error('仅支持 .xlsx / .xls 文件')
        return false
      }
      if (file.size / 1024 / 1024 > 20) {
        Message.error('文件最大20MB')
        return false
      }
      return true
    },
    handleOriginUpload(params) {
      const file = params.file.raw || params.file
      this.originFile = file
      Message.success('原始数据文件已选择')
      params.onSuccess({})
    },
    handleNewUpload(params) {
      const file = params.file.raw || params.file
      this.newFile = file
      Message.success('比对数据文件已选择')
      params.onSuccess({})
    },
    uploadFiles() {
      if (!this.originFile || !this.newFile) {
        Message.warning('请先选择原始数据和比对数据文件')
        return
      }
      this.startCompare()
    },
    async startCompare() {
      if (!this.originFile || !this.newFile) {
        Message.warning('请先选择原始数据和比对数据文件')
        return
      }
      this.loading = true
      const loadingInstance = Loading.service({ text: '正在比对Excel数据，请稍候...' })
      const formData = new FormData()
      formData.append('originExcel', this.originFile)
      formData.append('newExcel', this.newFile)
      try {
        const res = await compareExcelApi(formData)
        this.compareResult = res.data.list
        this.hasCompareResult = true
        Message.success('数据比对完成')
      } catch (err) {
        Message.error('比对失败：' + (err.msg || '服务异常'))
        this.hasCompareResult = false
      } finally {
        this.loading = false
        loadingInstance.close()
      }
    },
    async downloadResult() {
      try {
        const blob = await downloadCompareResultApi()
        const a = document.createElement('a')
        a.href = URL.createObjectURL(blob)
        a.download = `数据比对结果_${new Date().getTime()}.xlsx`
        a.click()
        URL.revokeObjectURL(a.href)
      } catch (err) {
        Message.error('文件下载失败')
      }
    },
    clearOrigin() {
      this.originFile = null
      if (this.$refs.originUploadRef && this.$refs.originUploadRef.clearFiles) {
        this.$refs.originUploadRef.clearFiles()
      }
    },
    clearNew() {
      this.newFile = null
      if (this.$refs.newUploadRef && this.$refs.newUploadRef.clearFiles) {
        this.$refs.newUploadRef.clearFiles()
      }
    }
  }
}
</script>

<style scoped>
.compare-page {
  padding: 20px;
}
.operate-row {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  flex-wrap: wrap;
}
.upload-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 300px;
}
.label {
  font-size: 18px;
  font-weight: 500;
  white-space: nowrap;
}
.upload-box {
  max-width: 480px;
  width: 100%;
}
.upload-placeholder {
  padding: 12px 16px;
  color: #c0c4cc;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
}
.file-name {
  margin-left: 12px;
  color: #606266;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
.actions {
  margin-left: auto;
  display: flex;
  gap: 12px;
  align-items: center;
}

@media (max-width: 900px) {
  .operate-row {
    gap: 12px;
  }
  .label { font-size: 16px }
  .upload-box { max-width: 360px }
  .actions { width: 100%; justify-content: flex-start; margin-left: 0 }
}
.result-table {
  margin-top: 30px;
}
</style>