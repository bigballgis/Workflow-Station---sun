export default {
  login: {
    htmlTitle: 'Sign in',
    title: 'Workflow Platform',
    subtitle: 'Unified Sign-In',
    username: 'Username',
    password: 'Password',
    usernamePlaceholder: 'Enter Your Username',
    passwordPlaceholder: 'Enter Your Password',
    submit: 'Log in',
    submitting: 'Signing in...',
    or: 'or',
    dsp: 'Passwordless Sign-In (DSP)',
    dspSubmitting: 'Signing in via DSP...',
    error: {
      missingParams: 'Missing client_id or redirect_uri parameter.',
      network: 'Network error.',
      invalidResponse: 'Invalid login response.',
      serverError: 'Server error ({status}). Is Kong/admin-center healthy?',
      invalidCredentials:
        'Invalid username or password, or SSO redirect_uri was rejected.',
      dspDisabled: 'Passwordless sign-in is not available.',
      dspNoToken: 'No DSP token found. Please sign in with your password.',
      dspFailed: 'Passwordless sign-in failed ({detail}).',
    },
  },
}
