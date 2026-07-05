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
                       <span>X-box工具箱</span>
                     </template>

                    <el-menu-item-group>
                        <el-menu-item index="/compare">比对工具</el-menu-item>
                        <el-menu-item index="/employee_kpi">XX工具</el-menu-item>
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