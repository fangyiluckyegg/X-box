<template>
  <div style="padding: 0 25px;" @click="handleClick">
    <svg
      :class="{'is-active':isActive}"
      class="hamburger"
      viewBox="0 0 15 15">
      <path d="M7.5,0.5c3.9,0,7,3.1,7,7c0,3.9-3.1,7-7,7c-3.9,0-7-3.1-7-7l0,0C0.5,3.6,3.6,0.5,7.5,0.5 C7.5,0.5,7.5,0.5,7.5,0.5L7.5,0.5L7.5,0.5z M6.1,4.7v5.6l4.2-2.8L6.1,4.7z"/>
    </svg>
  </div>
</template>

<script>
export default {
  name: 'Hamburger',
  props: {
    isActive: {
      type: Boolean,
      default: false
    }
  },
  methods: {
    handleClick() {
      this.$emit('toggleClick')
    }
  }
}
</script>

<style scoped>
.hamburger {
  width: 25px;
  height: 25px;
  vertical-align: middle;
}

.hamburger.is-active {
  transform: rotate(180deg);
}
</style>
