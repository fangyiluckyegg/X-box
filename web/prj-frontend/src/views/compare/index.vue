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
        :status="progressInfo.stage === 'done' ? 'success' : (progressInfo.stage === 'failed' ? 'exception' : undefined)"
      ></el-progress>
      <div style="margin-top:8px;text-align:right;">
        {{ progressInfo.current }} / {{ progressInfo.total }} 条
      </div>
      <div v-if="progressInfo.stage === 'failed'" style="margin:12px 0;color:#f56c6c;font-weight:bold;">
        比对失败：{{ progressInfo.message || '未知错误' }}
      </div>
      <template slot="footer">
        <el-button
          v-if="progressInfo.stage === 'done'"
          type="success"
          @click="progressDialogVisible = false"
        >
          关闭弹窗
        </el-button>
        <el-button
          v-if="progressInfo.stage === 'failed'"
          type="danger"
          @click="retryCompare"
        >
          重试
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
      <el-table border :data="pagedResult" style="width:100%">
        <el-table-column label="原始数据值" prop="originVal" />
        <el-table-column label="新比对数据值" prop="newVal" />
        <el-table-column label="匹配结果" prop="diffType" />
      </el-table>
      <el-pagination
        v-if="compareResult.length"
        style="margin-top:12px;text-align:right;"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
        :current-page="currentPage"
        :page-sizes="[50, 100, 200, 500]"
        :page-size="pageSize"
        layout="total, sizes, prev, pager, next, jumper"
        :total="compareResult.length"
      >
      </el-pagination>
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
      currentPage: 1,
      pageSize: 100,
      progressDialogVisible: false,
      progressInfo: {
        total: 0,
        current: 0,
        percent: 0,
        currentText: '',
        stage: ''
      },
      progressTimer: null,
      stageTextMap: {
        uploaded: "文件已上传",
        vector_calc: "向量计算中",
        match_compare: "相似度匹配比对",
        done: "比对任务全部完成",
        failed: "比对失败"
      }
    }
  },
  computed: {
    // 内存分页：十万行结果全量来自 fetchResult，前端 slice 分页渲染
    pagedResult() {
      const start = (this.currentPage - 1) * this.pageSize
      return this.compareResult.slice(start, start + this.pageSize)
    }
  },
  beforeDestroy() {
    this.stopPoll()
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
    // 提交比对（fire-and-forget）：POST 仅触发，结果以轮询 done → fetchResult 为准；不消费 POST body
    async startCompare() {
      if (!this.originFile || !this.newFile) {
        Message.warning('请先选择原始数据和比对数据文件')
        return
      }
      this.progressDialogVisible = true
      this.loading = true
      this.compareResult = []
      this.hasCompareResult = false
      this.currentPage = 1
      this.resetProgress()
      this.startPollProgress()
      const formData = new FormData()
      formData.append('originExcel', this.originFile)
      formData.append('newExcel', this.newFile)
      compareExcelApi(formData).then(res => {
        // 202 仅确认任务已提交，结果以轮询 fetchResult 获取
        console.log('[compare] 任务已提交:', res && res.msg)
      }).catch(err => {
        console.warn('[compare] 提交失败:', err.msg || err.message)
        this.stopPoll()
        this.progressDialogVisible = false
        this.loading = false
      })
    },
    // 失败后重试：重新提交（后端 cancel 覆盖旧任务）
    retryCompare() {
      this.progressDialogVisible = true
      this.loading = true
      this.compareResult = []
      this.hasCompareResult = false
      this.currentPage = 1
      this.resetProgress()
      this.startPollProgress()
      const formData = new FormData()
      formData.append('originExcel', this.originFile)
      formData.append('newExcel', this.newFile)
      compareExcelApi(formData).then(res => {
        console.log('[compare] 重试任务已提交:', res && res.msg)
      }).catch(err => {
        console.warn('[compare] 重试提交失败:', err.msg || err.message)
        this.stopPoll()
        this.progressDialogVisible = false
        this.loading = false
      })
    },
    resetProgress() {
      this.progressInfo = {
        total: 0,
        current: 0,
        percent: 0,
        currentText: '',
        stage: ''
      }
    },
    startPollProgress() {
      if (this.progressTimer) clearInterval(this.progressTimer)
      this.progressTimer = setInterval(async () => {
        try {
          const res = await getExcelCompareProgressApi()
          if (!res.data) {
            this.stopPoll()
            this.progressDialogVisible = false
            return
          }
          Object.assign(this.progressInfo, res.data)
          if (res.data.stage === 'done') {
            this.stopPoll()
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
          } else if (res.data.stage === 'failed') {
            // 失败：停止轮询，展示红色错误 + 重试按钮（不关闭弹窗）
            this.stopPoll()
            this.loading = false
            Message.error('比对失败：' + (res.data.message || '未知错误'))
          }
        } catch (e) {
          // 轮询单次失败不中断，继续下一次
          console.warn('[compare] progress poll error:', e)
        }
      }, 1500)
    },
    stopPoll() {
      if (this.progressTimer) {
        clearInterval(this.progressTimer)
        this.progressTimer = null
      }
    },
    handleSizeChange(val) {
      this.pageSize = val
      this.currentPage = 1
    },
    handleCurrentChange(val) {
      this.currentPage = val
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
