const state = {
  visitedViews: []
}

const mutations = {
  // 添加标签
  ADD_VISITED_VIEW: (state, view) => {
    // 如果标签跳转的路由存在就不添加，否则就添加进标签组
    if (state.visitedViews.some(v => v.path === view.path)) return
    state.visitedViews.push(
      Object.assign({}, view, {
        title: view.meta.title || '标签'
      })
    )
  },
  // 删除选择的标签
  DEL_VISITED_VIEW: (state, view) => {
    for (const [i, v] of state.visitedViews.entries()) {
      if (v.path === view.path) {
        state.visitedViews.splice(i, 1)
        break
      }
    }
  },
  // 删除其它标签
  DEL_OTHERS_VISITED_VIEWS: (state, view) => {
    state.visitedViews = state.visitedViews.filter(v => {
      return v.meta.isFixed || v.path === view.path
    })
  },
  // 删除所有标签
  DEL_ALL_VISITED_VIEWS: state => {
    // 过滤出固定的标签，只保留固定标签
    const isFixedTags = state.visitedViews.filter(tag => tag.meta.isFixed)
    state.visitedViews = isFixedTags
  },

  UPDATE_VISITED_VIEW: (state, view) => {
    for (let v of state.visitedViews) {
      if (v.path === view.path) {
        v = Object.assign(v, view)
        break
      }
    }
  },
  // 删除右侧标签
  DEL_RIGHT_VIEWS: (state, view) => {
    const index = state.visitedViews.findIndex(v => v.path === view.path)
    if (index === -1) {
      return
    }
    state.visitedViews = state.visitedViews.filter((item, idx) => {
      if (idx <= index || (item.meta && item.meta.isFixed)) {
        return true
      }
      return false
    })
  },
  // 删除左侧标签
  DEL_LEFT_VIEWS: (state, view) => {
    const index = state.visitedViews.findIndex(v => v.path === view.path)
    if (index === -1) {
      return
    }
    state.visitedViews = state.visitedViews.filter((item, idx) => {
      if (idx >= index || (item.meta && item.meta.isFixed)) {
        return true
      }
      return false
    })
  }
}

const actions = {
  // 新增当前路由标签
  addView({ dispatch }, view) {
    dispatch('addVisitedView', view)
  },
  // 新增当前路由标签
  addVisitedView({ commit }, view) {
    commit('ADD_VISITED_VIEW', view)
  },
  // 删除当前路由标签
  delView({ dispatch, state }, view) {
    return new Promise(resolve => {
      dispatch('delVisitedView', view)
      resolve({
        visitedViews: [...state.visitedViews],
      })
    })
  },
  // 删除当前路由标签
  delVisitedView({ commit, state }, view) {
    return new Promise(resolve => {
      commit('DEL_VISITED_VIEW', view)
      resolve([...state.visitedViews])
    })
  },
  // 删除其他路由标签
  delOthersViews({ dispatch, state }, view) {
    return new Promise(resolve => {
      dispatch('delOthersVisitedViews', view)
      resolve({
        visitedViews: [...state.visitedViews]
      })
    })
  },
  // 删除其他路由标签
  delOthersVisitedViews({ commit, state }, view) {
    return new Promise(resolve => {
      commit('DEL_OTHERS_VISITED_VIEWS', view)
      resolve([...state.visitedViews])
    })
  },
  // 删除所有路由标签
  delAllViews({ dispatch, state }, view) {
    return new Promise(resolve => {
      dispatch('delAllVisitedViews', view)
      resolve({
        visitedViews: [...state.visitedViews]
      })
    })
  },
  // 删除所有路由标签
  delAllVisitedViews({ commit, state }) {
    return new Promise(resolve => {
      commit('DEL_ALL_VISITED_VIEWS')
      resolve([...state.visitedViews])
    })
  },

  updateVisitedView({ commit }, view) {
    commit('UPDATE_VISITED_VIEW', view)
  },
  // 删除右侧路
  delRightTags({ commit }, view) {
    return new Promise(resolve => {
      commit('DEL_RIGHT_VIEWS', view)
      resolve([...state.visitedViews])
    })
  },
  // 删除左侧路由标签
  delLeftTags({ commit }, view) {
    return new Promise(resolve => {
      commit('DEL_LEFT_VIEWS', view)
      resolve([...state.visitedViews])
    })
  },
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
