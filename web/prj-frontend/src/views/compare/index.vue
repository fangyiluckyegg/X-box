<template>
  <div class="compare-page">
    <el-dialog
      :visible.sync="progressDialogVisible"
      title="Excel比对实时进度"
      width="620px"
      :close-on-click-modal="false"
    >
      <div style="margin-bottom:10px;">
        <span style="font-weight:bold;">当前阶段：</span>
        {{ stageTextMap[progressInfo.stage] || "等待初始化" }}
      </div>
      <div style="margin-bottom:14px;color:#666;">
        正在处理文本：{{ progressInfo.currentText || "-" }}
      </div>
      <el-progress
        :percentage="progressInfo.percent"
        :status="progressInfo.stage === 'done' ? 'success' : undefined"
      ></el-progress>
      <div style="margin-top:8px;text-align:right;">
        {{ progressInfo.done }} / {{ progressInfo.total }} 条
      </div>
      <template slot="footer">
        <el-button
          v-if="progressInfo.stage === 'done'"
          type="success"
          @click="progressDialogVisible = false"
        >
          关闭弹窗
        </el-button>
      </template>
    </el-dialog>

    <div class="operate-row">
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

      <div class="actions">
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

    <div v-if="compareResult.length" class="result-table">
      <h3>比对差异结果</h3>
      <el-table border :data="compareResult" style="width:100%">
        <el-table-column label="原始数据值" prop="originVal" />
        <el-table-column label="新比对数据值" prop="newVal" />
        <el-table-column label="匹配结果" prop="diffType" />
      </el-table>
    </div>
  </div>
</template>

<script>
import { Message } from 'element-ui'
import { compareExcelApi, downloadCompareResultApi, getExcelCompareProgressApi, fetchCompareResultApi } from '@/api/compare'

export default {
  name: 'EmployeeCompare',
  data() {
    return {
      originFile: null,
      newFile: null,
      loading: false,
      hasCompareResult: false,
      compareResult: [],
      progressDialogVisible: false,
      progressInfo: {
        total: 0,
        done: 0,
        percent: 0,
        currentText: '',
        stage: ''
      },
      progressTimer: null,
      stageTextMap: {
        vector_calc: "向量计算中",
        match_compare: "相似度匹配比对",
        done: "比对任务全部完成"
      }
    }
  },
  beforeDestroy() {
    if (this.progressTimer) {
      clearInterval(this.progressTimer)
      this.progressTimer = null
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
    async startCompare() {
      if (!this.originFile || !this.newFile) {
        Message.warning('请先选择原始数据和比对数据文件')
        return
      }
      this.progressDialogVisible = true
      this.loading = true
      this.compareResult = []
      this.hasCompareResult = false
      this.startPollProgress()
      const formData = new FormData()
      formData.append('originExcel', this.originFile)
      formData.append('newExcel', this.newFile)
      // 触发后端比对（fire-and-forget，不 await 结果；结果通过轮询 done → fetchResult 获取）
      compareExcelApi(formData).then(res => {
        if (res.data && res.data.list && res.data.list.length) {
          this.compareResult = res.data.list
          this.hasCompareResult = true
        }
      }).catch(err => {
        console.warn('[compare] 触发请求完成（可能超时），结果以轮询为准:', err.msg || err.message)
      })
    },
    startPollProgress() {
      if (this.progressTimer) clearInterval(this.progressTimer)
      this.progressTimer = setInterval(async () => {
        try {
          const res = await getExcelCompareProgressApi()
          if (!res.data) {
            clearInterval(this.progressTimer)
            this.progressTimer = null
            this.progressDialogVisible = false
            return
          }
          Object.assign(this.progressInfo, res.data)
          if (res.data.stage === 'done') {
            clearInterval(this.progressTimer)
            this.progressTimer = null
            this.loading = false
            // 轮询到完成 → 立即取结果
            try {
              const resultRes = await fetchCompareResultApi()
              if (resultRes.data && resultRes.data.list) {
                this.compareResult = resultRes.data.list
                this.hasCompareResult = this.compareResult.length > 0
              }
            } catch (e) {
              console.error('[compare] fetchResult failed:', e)
            }
            Message.success('全部比对完成，下方可查看表格或下载结果')
          }
        } catch (e) {
          // 轮询单次失败不中断，继续下一次
          console.warn('[compare] progress poll error:', e)
        }
      }, 1500)
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
  .label {
    font-size: 16px;
  }
  .upload-box {
    max-width: 360px;
  }
  .actions {
    width: 100%;
    justify-content: flex-start;
    margin-left: 0;
  }
}
.result-table {
  margin-top: 30px;
}
::v-deep .el-dialog {
  z-index: 9999 !important;
}
</style>