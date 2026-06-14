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
      <section className="auth-shell">
        <aside className="auth-showcase" aria-label="SplitSmart highlights">
          <div className="auth-showcase__brand">
            <span className="material-symbols-outlined">account_balance_wallet</span>
            <span>SplitSmart</span>
          </div>
          <div>
            <p className="auth-eyebrow">Shared expense tracking</p>
            <h1 className="auth-showcase__title">Settle group expenses with clarity.</h1>
            <p className="auth-showcase__text">
              Track rent, trips, meals, and recurring bills with clean balances and fast settlements.
            </p>
          </div>
          <div className="auth-metrics">
            <div>
              <strong>4</strong>
              <span>split modes</span>
            </div>
            <div>
              <strong>INR</strong>
              <span>ready</span>
            </div>
            <div>
              <strong>JWT</strong>
              <span>secured</span>
            </div>
          </div>
        </aside>

        <div className="auth-card">
          <div className="auth-brand">
            <div className="auth-brand__icon-wrapper">
              <span className="material-symbols-outlined auth-brand__icon">account_balance_wallet</span>
            </div>
            <div>
              <h1 className="auth-brand__name">Welcome back</h1>
              <p className="auth-brand__tagline">Sign in to review balances and settle up.</p>
            </div>
          </div>

          {error && (
            <div className="alert alert--error" role="alert">
              <span className="material-symbols-outlined">error</span>
              <span>{error}</span>
            </div>
          )}

          <form className="auth-form" onSubmit={handleSubmit}>
            <div className="form-group">
              <label className="form-label" htmlFor="login-email">Email address</label>
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

            <div className="form-group">
              <label className="form-label" htmlFor="login-password">Password</label>
              <div className="form-input-wrapper">
                <span className="material-symbols-outlined form-input-icon">lock</span>
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  className="form-input form-input--icon"
                  placeholder="Enter your password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="current-password"
                />
                <button
                  type="button"
                  className="form-input-suffix"
                  onClick={() => setShowPassword(!showPassword)}
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                >
                  <span className="material-symbols-outlined">
                    {showPassword ? 'visibility_off' : 'visibility'}
                  </span>
                </button>
              </div>
            </div>

            <button
              type="submit"
              className="btn btn--primary btn--full btn--lg"
              disabled={loading}
              id="login-submit"
            >
              {loading ? (
                <div className="spinner spinner--sm spinner--light" />
              ) : (
                <>
                  Sign in
                  <span className="material-symbols-outlined">arrow_forward</span>
                </>
              )}
            </button>
          </form>

          <div className="auth-footer">
            New to SplitSmart? <Link to="/register">Create an account</Link>
          </div>

          <div className="auth-trust-row" aria-label="Security status">
            <span><span className="material-symbols-outlined">verified_user</span> Secure login</span>
            <span><span className="material-symbols-outlined">monitor_heart</span> API ready</span>
          </div>
        </div>
      </section>
    </div>
  );
}
