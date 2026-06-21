<template>
  <div id="app">
    <router-view/>   //引入router路由组件
  </div>
</template>

<script>
export default  {
  name:  'App',
    metaInfo() {
        return {
            title: X工具箱,
            titleTemplate: X工具箱
        }
    }
}
</script>
