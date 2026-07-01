<template>
  <el-breadcrumb class="my-breadcrumb" separator="/">
    <el-breadcrumb-item v-for="(item,index) in menuList" :key="item.path">
      <span class="menuTitle">{{ item.meta.title }}</span>
    </el-breadcrumb-item>
  </el-breadcrumb>
</template>

<script>
export default {
  data() {
    return {
      menuList: '首页'
    }
  },
  watch: {
    $route(route) {
      this.getBreadcrumb()
    }
  },
  created() {
    this.getBreadcrumb()
  },
  methods: {
    getBreadcrumb() {
      let matched = this.$route.matched.filter(item => item.meta)
      const first = matched[0]
      this.menuList = matched.filter(item => item.meta.title )
    }
  }
}
</script>

<style lang="scss" scoped>
.my-breadcrumb.el-breadcrumb {
  display: inline-block;
  font-size: 15px;
  line-height: 50px;

  .menuTitle {
    color: #75b7c9;
    cursor: text;
  }
}
</style>
