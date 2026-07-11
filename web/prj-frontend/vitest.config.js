// ============================================================
// 最小可行前端单元测试配置（可选 / 非强制启用）
// 对应任务：web/prj-frontend 当前无 vitest/jest，给出最小可行测试配置建议。
//
// 安装（Mac，需先 npm install 拉取既有依赖）：
//   npm install -D vitest jsdom @vue/test-utils
//
// 运行：
//   npx vitest run            # 单次运行（CI/验收）
//   npx vitest                # watch 模式（本地开发）
//
// 说明：本配置以 jsdom 环境运行纯 JS 模块测试（如 src/utils/auth.js）。
// 若要测试 .vue 单文件组件，需额外装 @vitejs/plugin-vue2 并在下面 plugins 中注册，
// 并用 @vue/test-utils 的 mount() 渲染组件。
// ============================================================
import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'jsdom',
    include: ['tests/**/*.{test,spec}.js'],
    globals: true,
  },
})
