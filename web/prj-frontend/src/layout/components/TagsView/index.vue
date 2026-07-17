<template>
  <div id="myTagsView" class="myTagsView">

    <scroll-pane ref="scrollPane" class="myTagsViewWrapper" @scroll="handleScroll">
      <router-link
        v-for="tag in visitedViews"
        ref="tag"
        :key="tag.path"
        :to="{ path: tag.path, query: tag.query }"
        custom
        v-slot="{ navigate }"
      >
        <span
          :class="isActive(tag)?'active':''"
          class="tagsMenu"
          :style="activeStyle(tag)"
          @click="navigate"
          @click.middle="!isFixed(tag)?closeSelected(tag):''"
          @contextmenu.prevent="openMenu(tag,$event)"
        >
          {{ tag.title }}

          <span v-if="!isFixed(tag)" class="el-icon-close" @click.prevent.stop="closeSelected(tag)" />
        </span>
      </router-link>
    </scroll-pane>
    <ul v-show="visible" :style="{left:leftVal+'px',top:topVal+'px'}" class="contextmenu">
      <li v-if="!isFixed(selectedTag)" @click="closeSelected(selectedTag)"><i class="el-icon-close"></i> 关闭当前</li>
      <li @click="closeOthers"><i class="el-icon-circle-close"></i> 关闭其它</li>
      <li v-if="!isFirstView()" @click="closeLeft"><i class="el-icon-back"></i> 关闭左侧</li>
      <li v-if="!isLastView()" @click="closeRight"><i class="el-icon-right"></i> 关闭右侧</li>
      <li @click="closeAll(selectedTag)"><i class="el-icon-circle-close"></i> 全部关闭</li>
    </ul>
  </div>
</template>

<script>
  import ScrollPane from './ScrollPane'
  import path from 'path'

  export default {
    components: { ScrollPane },
    data() {
      return {
        visible: false,
        topVal: 0,
        leftVal: 0,
        selectedTag: {},
        isFixedTags: []
      }
    },
    // 计算属性
    computed: {
      visitedViews() {
        return this.$store.state.tagsView.visitedViews
      },
      routes() {
        // 返回路由配置数组，兼容 Vue Router 配置
        return (this.$router && this.$router.options && this.$router.options.routes) || []
      },
      theme() {
        return (this.$store && this.$store.state && this.$store.state.settings && this.$store.state.settings.theme) || '#43c985'
      },
    },
    // 监听
    watch: {
      $route() {
        this.addTags()
        this.moveToCurrentTag()
      },
      visible(value) {
        if (value) {
          document.body.addEventListener('click', this.closeMenu)
        } else {
          document.body.removeEventListener('click', this.closeMenu)
        }
      }
    },

    mounted() {
      this.initTags()
      this.addTags()
    },
    methods: {
      isActive(route) {
        return route.path === this.$route.path
      },
      activeStyle(tag) {
        if (!this.isActive(tag)) return {};
        return {
          "background-color": this.theme,
          "border-color": this.theme
        };
      },
      isFixed(tag) {
        return tag.meta && tag.meta.isFixed
      },
      isFirstView() {
        try {
          if (!this.selectedTag || !this.selectedTag.path) return false
          if (!this.visitedViews || this.visitedViews.length < 2) return false
          if (this.selectedTag.path === this.visitedViews[1].path || this.selectedTag.path === '/index') {
            return true
          }
          return false
        } catch (err) {
          return false
        }
      },
      isLastView() {
        try {
          if (!this.selectedTag || !this.selectedTag.path) return false
          if (!this.visitedViews || this.visitedViews.length === 0) return false
          return this.selectedTag.path === this.visitedViews[this.visitedViews.length - 1].path
        } catch (err) {
          return false
        }
      },
      filterisFixedTags(routes, basePath = '/') {
        let tags = []
        routes.forEach(route => {
          if (route.meta && route.meta.isFixed) {
            const tagPath = path.resolve(basePath, route.path)
            tags.push({
              fullPath: tagPath,
              path: tagPath,
              name: route.name,
              meta: { ...route.meta }
            })
          }
        })
        return tags
      },
      initTags() {
        const isFixedTags = this.isFixedTags = this.filterisFixedTags(this.routes)
        for (const tag of isFixedTags) {
          this.$store.dispatch('tagsView/addVisitedView', tag)
        }
      },

      addTags() {
        const { name } = this.$route
        if (name) {
          this.$store.dispatch('tagsView/addView', this.$route)
        }
        return false
      },

      moveToCurrentTag() {
        const tags = this.$refs.tag
        this.$nextTick(() => {
          if (!tags) return
          for (const tag of tags) {
            const to = tag && tag.to
            if (!to) continue
            if (to.path === this.$route.path) {
              this.$refs.scrollPane && this.$refs.scrollPane.moveToTarget && this.$refs.scrollPane.moveToTarget(tag)

              if (to.fullPath && to.fullPath !== this.$route.fullPath) {
                this.$store.dispatch('tagsView/updateVisitedView', this.$route)
              }
              break
            }
          }
        })
      },
      closeSelected(view) {
        this.$store.dispatch('tagsView/delView', view).then(({ visitedViews }) => {
          if (this.isActive(view)) {
            this.toLastView(visitedViews, view)
          }
        })
      },
      closeRight() {
        this.$store.dispatch('tagsView/delRightTags', this.selectedTag).then(visitedViews => {
          if (!visitedViews.find(i => i.fullPath === this.$route.fullPath)) {
            this.toLastView(visitedViews)
          }
        })
      },
      closeLeft() {
        this.$store.dispatch('tagsView/delLeftTags', this.selectedTag).then(visitedViews => {
          if (!visitedViews.find(i => i.fullPath === this.$route.fullPath)) {
            this.toLastView(visitedViews)
          }
        })
      },
      closeOthers() {
        this.$router.push(this.selectedTag)
        this.$store.dispatch('tagsView/delOthersViews', this.selectedTag).then(() => {
          this.moveToCurrentTag()
        })
      },
      closeAll(view) {
        this.$store.dispatch('tagsView/delAllViews').then(({ visitedViews }) => {
          if (this.isFixedTags.some(tag => tag.path === this.$route.path)) {
            return
          }
          this.toLastView(visitedViews, view)
        })
      },
      toLastView(visitedViews, view) {
        const latestView = visitedViews.slice(-1)[0]
        if (latestView) {
          this.$router.push(latestView.fullPath || latestView.path)
        } else {
          this.$router.push('/')
        }
      },
      openMenu(tag, e) {

        const maxLeft = this.$el.offsetWidth - 100
        const leftVal = e.clientX - this.$el.getBoundingClientRect().left + 10

        if (leftVal > maxLeft) {
          this.leftVal = maxLeft
        } else {
          this.leftVal = leftVal
        }

        this.topVal = e.clientY
        this.visible = true
        this.selectedTag = tag
      },
      closeMenu() {
        this.visible = false
      },
      handleScroll() {
        this.closeMenu()
      }
    }
  }
</script>

<style lang="scss" scoped>
  .myTagsView {
    height: 30px;
    background: #fff;
    border-bottom: 1px solid #d8dce5;
    .myTagsViewWrapper {
      .tagsMenu {
        display: inline-block;
        position: relative;
        cursor: pointer;
        line-height: 30px;
        border: 1px solid #d8dce5;
        color: #505060;
        height: 30px;
        font-size: 14px;
        padding: 0 10px;
        &:first-of-type {
          margin-left: 15px;
        }
        &:last-of-type {
          margin-right: 15px;
        }
        &.active {
          background-color: #43c985;
          border-color: #43c985;
        }
      }
    }
    .contextmenu {
      background: white;
      z-index: 3000;
      position: absolute;
      padding: 5px 0;
      border-radius: 2px;
      font-size: 14px;
      box-shadow: 1px 1px 1px 1px ;
      li {
        margin: 2;
        padding: 10px 20px;
        &:hover {
          background: #ddd;
        }
      }
    }
  }
</style>

<style lang="scss">
  .tags-view-wrapper {
    .tagsMenu {
      .el-icon-close {
        vertical-align: 2px;
        border-radius: 50%;
        text-align: center;
      }
    }
  }
</style>
