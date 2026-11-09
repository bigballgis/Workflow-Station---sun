import { createApp } from "vue";
import { createPinia } from "pinia";
import ElementPlus from "element-plus";
import "element-plus/dist/index.css";
import "@fontsource-variable/inter";
import * as ElementPlusIconsVue from "@element-plus/icons-vue";
import App from "./App.vue";
import router from "./router";
import i18n from "./i18n";
import "./styles/index.scss";
import { refreshToken, saveUser, setOnLogout } from "@/api/auth";
import { redirectToUnifiedLogin } from "@/utils/sso";
import { setSessionRefresher } from "@platform-shared/auth/sessionRefresh";

// Before app.use(router): the first navigation's /me check may already need a refresh.
// A refresh recomputes adminAccess from the user's current roles; store it so menus and buttons follow.
setSessionRefresher(async () => {
  const response = await refreshToken();
  if (response?.user) saveUser(response.user);
  return response;
});

const app = createApp(App);

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component);
}

app.use(createPinia());
app.use(router);
app.use(i18n);
app.use(ElementPlus);

// Cross-tab logout: redirect to unified login when other tab triggers logout
setOnLogout(() => redirectToUnifiedLogin("admin"));

app.mount("#app");
