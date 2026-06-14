import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authAPI } from '../services/api';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const response = await authAPI.login({ email, password });
      login(response.data.data);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || 'Invalid email or password');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <div className="auth-card">
        {/* Brand */}
        <div className="auth-brand">
          <div className="auth-brand__icon-wrapper">
            <span className="material-symbols-outlined auth-brand__icon">account_balance_wallet</span>
          </div>
          <h1 className="auth-brand__name">SplitSmart</h1>
          <p className="auth-brand__tagline">Simplifying shared expenses for everyone.</p>
        </div>

        {/* Welcome Text */}
        <h2 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '4px' }}>Welcome back</h2>
        <p className="text-body-sm text-muted" style={{ marginBottom: '24px' }}>
          Please enter your details to sign in.
        </p>

        {/* Error Message */}
        {error && (
          <div style={{
            background: 'var(--error-container)',
            color: 'var(--on-error-container)',
            padding: '12px 16px',
            borderRadius: 'var(--radius-lg)',
            marginBottom: '16px',
            fontSize: '14px',
            fontWeight: 500,
          }}>
            {error}
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className="form-group" style={{ marginBottom: '16px' }}>
            <label className="form-label" htmlFor="login-email">Email Address</label>
            <div className="form-input-wrapper">
              <span className="material-symbols-outlined form-input-icon">mail</span>
              <input
                id="login-email"
                type="email"
                className="form-input form-input--icon"
                placeholder="name@example.com"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                required
                autoComplete="email"
              />
            </div>
          </div>

          <div className="form-group" style={{ marginBottom: '24px' }}>
            <div className="flex justify-between items-center">
              <label className="form-label" htmlFor="login-password">Password</label>
            </div>
            <div className="form-input-wrapper">
              <span className="material-symbols-outlined form-input-icon">lock</span>
              <input
                id="login-password"
                type={showPassword ? 'text' : 'password'}
                className="form-input form-input--icon"
                placeholder="••••••••"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
              />
              <button
                type="button"
                className="form-input-suffix"
                onClick={() => setShowPassword(!showPassword)}
                tabIndex={-1}
              >
                <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>
                  {showPassword ? 'visibility_off' : 'visibility'}
                </span>
              </button>
            </div>
          </div>

          <button
            type="submit"
            className="btn btn--primary btn--full"
            disabled={loading}
            id="login-submit"
          >
            {loading ? (
              <div className="spinner spinner--sm" style={{ borderTopColor: 'white', borderColor: 'rgba(255,255,255,0.3)' }} />
            ) : (
              'Sign In'
            )}
          </button>
        </form>

        {/* Footer */}
        <div className="auth-footer">
          Don't have an account? <Link to="/register">Create one</Link>
        </div>
      </div>

      {/* Status bar */}
      <div style={{
        marginTop: '24px',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        fontSize: '12px',
        color: 'var(--on-surface-variant)',
      }}>
        <span className="material-symbols-outlined" style={{ fontSize: '14px', color: 'var(--tertiary)' }}>check_circle</span>
        <span>Systems Operational</span>
        <span style={{ margin: '0 4px' }}>•</span>
        <span className="material-symbols-outlined" style={{ fontSize: '14px' }}>lock</span>
        <span>256-bit Secure</span>
      </div>
    </div>
  );
}
