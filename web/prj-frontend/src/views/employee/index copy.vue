<template>
  <div class="app-container">
    <el-form :model="queryParams" ref="queryForm" :inline="true" v-show="showSearch" label-width="70px">
      <el-form-item label="原始数据" prop="dept">
        <el-input
          v-model="queryParams.dept"
          placeholder="请选择原始数据"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item label="比对数据" prop="name">
        <el-input
          v-model="queryParams.name"
          placeholder="请输入新比对数据"
          @keyup.enter.native="handleQuery"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="el-icon-search" @click="handleQuery">启动比对</el-button>
        <el-button icon="el-icon-refresh" @click="resetQuery">结果下载</el-button>
      </el-form-item>
    </el-form>

  </div>
</template>

<script>
import { listEmployee, getEmployee, delEmployee, addEmployee, updateEmployee } from "@/api/employee";

export default {
  name: "Employee",
  data() {
    return {
    // 遮罩层
    loading: true,
    // 选中数组
    ids: [],
    // 非单个禁用
    single: true,
    // 非多个禁用
    multiple: true,
    // 显示搜索条件
    showSearch: true,
    // 总条数
    total: 0,
      // 是否显示弹出层
      open: false,
      // 查询参数
      queryParams: {
        pageNum: 1,
        pageSize: 10,
        dept: null,
        name: null,
        position: null,
        salary: null
      },
      // 表单参数
      form: {},
      // 表单校验
      rules: {
      }
    };
  },
  methods: {
    /** 搜索按钮操作 */
    handleQuery() {
      this.queryParams.pageNum = 1;
      this.getList();
    },
    /** 重置按钮操作 */
    resetQuery() {
      this.resetForm("queryForm");
      this.handleQuery();
    },
  }
};
</script>
