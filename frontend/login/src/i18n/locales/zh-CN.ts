export default {
  login: {
    htmlTitle: '登录',
    title: 'Workflow Platform',
    subtitle: '统一登录',
    username: '用户名',
    password: '密码',
    submit: '登录',
    submitting: '登录中...',
    error: {
      missingParams: '缺少 client_id 或 redirect_uri 参数。',
      network: '网络错误。',
      invalidResponse: '无效的登录响应。',
      serverError: '服务器错误 ({status})。Kong/admin-center 是否正常运行？',
      invalidCredentials: '用户名或密码错误，或 SSO redirect_uri 被拒绝。',
    },
  },
}
