<template>
  <div class="loginStyle">
    <!-- 中部居中大标题 -->
    <h1 class="loginMainTitle">X-box百宝箱</h1>

    <!-- 顶部右侧一行式登录栏 -->
    <div class="loginBar">
      <el-form
        ref="loginForm"
        :model="loginForm"
        :rules="loginRules"
        class="loginFormInline"
        inline
      >
        <el-form-item prop="username" class="loginItem">
          <el-input
            v-model="loginForm.username"
            type="text"
            auto-complete="off"
            placeholder="账号"
          >
          </el-input>
        </el-form-item>

        <el-form-item class="loginItem loginRemember">
          <el-checkbox v-model="loginForm.rememberMe">记住用户名</el-checkbox>
        </el-form-item>

        <el-form-item prop="password" class="loginItem">
          <el-input
            v-model="loginForm.password"
            type="password"
            auto-complete="off"
            placeholder="密码"
            @keyup.enter.native="handleLogin"
          >
          </el-input>
        </el-form-item>

        <el-form-item prop="code" class="loginItem loginCodeItem">
          <div class="loginCodeWrap">
            <el-input
              v-model="loginForm.code"
              auto-complete="off"
              placeholder="验证码"
              class="loginCodeInput"
              @keyup.enter.native="handleLogin"
            >
            </el-input>
            <img :src="codeUrl" @click="getCode" class="login-code-img" />
          </div>
        </el-form-item>

        <el-form-item class="loginItem">
          <el-button
            :loading="loading"
            type="primary"
            @click.native.prevent="handleLogin"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登 录 中...</span>
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 左下方班级同学录链接（两个上下排列） -->
    <div class="loginFooterLink">
      <a href="http://127.0.0.1:1181/607/" target="_blank" class="classmatesBtn">607班级同学录</a>
      <a href="http://127.0.0.1:1181/902/" target="_blank" class="classmatesBtn">902班级同学录</a>
    </div>
  </div>
</template>

<script>
import { getCodeImg } from "@/api/login";
import Cookies from "js-cookie";

export default {
  name: "Login",
  data() {
    return {
      codeUrl: "",
      loginForm: {
        username: "",
        password: "",
        rememberMe: false,
        code: "",
        uuid: ""
      },
      loginRules: {
        username: [
          { required: true, trigger: "blur", message: "请输入用户名" }
        ],
        password: [
          { required: true, trigger: "blur", message: "请输入密码" }
        ],
        code: [{ required: true, trigger: "change", message: "请输入验证码" }]
      },
      loading: false
    };
  },

  created() {
    this.getCode();
    this.getCookie();
  },
  methods: {
    getCode() {
      getCodeImg().then(res => {
        this.codeUrl = "data:image/gif;base64," + res.img;
        this.loginForm.uuid = res.uuid;

      });
    },
    getCookie() {
      const username = Cookies.get("username");
      const rememberMe = Cookies.get('rememberMe')
      this.loginForm = {
        username: username === undefined ? this.loginForm.username : username,
        password: this.loginForm.password,
        rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
      };
    },
    handleLogin() {
      this.$refs.loginForm.validate(valid => {
        if (valid) {
          this.loading = true;
          if (this.loginForm.rememberMe) {
            // [P0-FIX] 记住密码功能改为仅记住用户名，不再存储密码到Cookie
            Cookies.set("username", this.loginForm.username, { expires: 10 });
            Cookies.set('rememberMe', this.loginForm.rememberMe, { expires: 10 });
          } else {
            Cookies.remove("username");
            Cookies.remove('rememberMe');
          }
          this.$store.dispatch("Login", this.loginForm).then(() => {
            this.$router.push({ path: this.redirect || "/index" }).catch(()=>{});
          }).catch(() => {
            this.loading = false;
            this.getCode();
          });
        }
      });
    }
  }
};
</script>

<style>
.loginStyle {
  position: relative;
  height: 100%;
  width: 100%;
  background: #f5f7fa;
  overflow: hidden;
}

/* 中部居中大标题 */
.loginMainTitle {
  position: absolute;
  top: 45%;
  left: 50%;
  transform: translate(-50%, -50%);
  margin: 0;
  font-size: 52px;
  font-weight: 600;
  letter-spacing: 4px;
  color: #303133;
  text-align: center;
  user-select: none;
}

/* 顶部右侧登录栏 */
.loginBar {
  position: absolute;
  top: 24px;
  right: 32px;
  z-index: 2;
}

.loginFormInline {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
}

.loginFormInline .loginItem {
  margin-bottom: 0;
  margin-right: 14px;
}

.loginFormInline .loginItem:last-child {
  margin-right: 0;
}

.loginFormInline .el-input {
  width: 130px;
}

.loginFormInline .loginRemember {
  margin-right: 8px;
}

/* 验证码输入框 + 图片并排 */
.loginCodeWrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.loginFormInline .loginCodeInput {
  width: 100px;
}

.login-code-img {
  height: 38px;
  width: 100px;
  cursor: pointer;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  vertical-align: middle;
}

/* 左下方班级同学录链接 */
.loginFooterLink {
  position: absolute;
  bottom: 36px;
  left: 36px;
  z-index: 2;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.classmatesBtn {
  display: inline-block;
  padding: 10px 18px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  color: #606266;
  background: #ffffff;
  font-size: 14px;
  text-decoration: none;
  transition: all 0.2s ease;
}

.classmatesBtn:hover {
  border-color: #409eff;
  color: #409eff;
}

/* 响应式：窄屏登录栏折行并保持可读 */
@media screen and (max-width: 768px) {
  .loginBar {
    top: 16px;
    right: 16px;
    left: 16px;
  }

  .loginFormInline {
    justify-content: flex-start;
  }

  .loginMainTitle {
    font-size: 34px;
    width: 90%;
  }

  .loginFooterLink {
    bottom: 20px;
    left: 20px;
  }
}
</style>
