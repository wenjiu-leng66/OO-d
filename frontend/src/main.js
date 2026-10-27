import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import 'element-plus/dist/index.css'
import './styles/main.css'
import App from './App.vue'
import router from './router'

// 路由守卫由 router 初始化时注册，页面挂载前即具备身份恢复和访问判断入口。
createApp(App).use(router).use(ElementPlus, { locale: zhCn }).mount('#app')
