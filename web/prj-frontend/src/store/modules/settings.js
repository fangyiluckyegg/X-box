const state = {
  title: '',
  needTagsView: true
}

const mutations = {
  SET_TITLE(state, title) {
    state.title = title
  },
  SET_NEED_TAGS_VIEW(state, val) {
    state.needTagsView = val
  }
}

const actions = {
  setTitle({ commit }, title) {
    commit('SET_TITLE', title)
  },
  setNeedTagsView({ commit }, val) {
    commit('SET_NEED_TAGS_VIEW', val)
  }
}

export default {
  namespaced: true,
  state,
  mutations,
  actions
}
