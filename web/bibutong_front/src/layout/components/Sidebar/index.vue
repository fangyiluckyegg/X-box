<template>
    <div>
        <el-scrollbar  wrap-class="scrollbar-wrapper">
            <el-menu
                router
                :default-active="defaultActivePath"
                background-color= #304156
                text-color= #bfcbd9
                active-text-color= #fed14c
                :unique-opened="true"
            >
                <el-menu-item index='/index' >
                    <template slot="title">
                       <i class="el-icon-location"></i>
                       <span>回到首页</span>
                    </template>
                </el-menu-item>

                <el-submenu index="/info_manager">
                    <template slot="title">
                       <i class="el-icon-location"></i>
                       <span>比对工具</span>
                     </template>

                    <el-menu-item-group>
                        <el-menu-item index="/employee">单位名称比对</el-menu-item>
                        <el-menu-item index="/employee_kpi">XXXXXXXX</el-menu-item>
                    </el-menu-item-group>
                </el-submenu>
XXXXXXXX
            </el-menu>

        </el-scrollbar>
    </div>
</template>

<script>
export default {
  data() {
    return {
      defaultActivePath: '/' // 默认激活的路径
    };
  }
}
</script>