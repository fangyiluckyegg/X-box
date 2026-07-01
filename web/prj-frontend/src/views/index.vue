<template>
  <div class="app-container home">
    <el-row>
      <el-col>
        <blockquote style="font-size: 14px">
          X工具箱
          <br/>
          <el-link
            href="http://www.tup.tsinghua.edu.cn/index.html"
            type="primary"
            target="_blank"
            >http://www.tup.tsinghua.edu.cn/index.html</el-link>
        </blockquote>
      </el-col>
    </el-row>
    <el-row>
      <el-col :lg="12" style="padding-left: 20px">
        <h2>项目说明</h2>
        <p>
          该项目XXXXXXXXXXXXXXXX
          <br/>
          <br/>
          第一XXXXXXXXXXXXXXXXXXX
          <br/>
          第二XXXXXXXXXXXXXXXXXXX
          <br/>
          第三XXXXXXXXXXXXXXXXXXX
          <br/>
          第四XXXXXXXXXXXXXXXXXXX
        </p>
      </el-col>

      <el-col :lg="12" style="padding-left: 50px">
        <el-row>
          <el-col :span="10">
            <h2>本项目所包含的技术</h2>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="10">
            <h4>后端主要技术</h4>
            <ul>
              <li>SpringBoot</li>
              <li>IOC和AOP</li>
              <li>Mybatis</li>
              <li>Redis</li>
              <li>MyBatis</li>
              <li>JPA</li>
              <li>Swagger</li>
              <li>logback</li>
            </ul>
          </el-col>
          <el-col :span="10">
            <h4>前端主要技术</h4>
            <ul>
              <li>Vue</li>
              <li>Vuex</li>
              <li>Element-ui</li>
              <li>Axios</li>
              <li>Vue Route</li>
              <li>前端重要组件使用要点</li>
              <li>监听器</li>
              <li>基于Json的数据交互</li>
            </ul>
          </el-col>
        </el-row>
      </el-col>
    </el-row>
  </div>
</template>

<script>
export default {
  name: "Index",

  methods: {
    goTarget(href) {
      window.open(href, "_blank");
    },
  },
};
</script>

<style scoped lang="scss">
.home {
  blockquote {
    padding: 10px 20px;
    margin: 0 0 20px;
    font-size: 17.5px;
    border-left: 5px solid #eee;
  }
  hr {
    margin-top: 20px;
    margin-bottom: 20px;
    border: 0;
    border-top: 1px solid #eee;
  }
  .col-item {
    margin-bottom: 20px;
  }

  ul {
    padding: 0;
    margin: 0;
  }

  color: #675c6b;


}
</style>

