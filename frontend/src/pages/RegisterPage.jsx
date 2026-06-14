import React, { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { authAPI } from '../services/api';

export default function RegisterPage() {
  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const { login } = useAuth();
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }
    if (password.length < 6) {
      setError('Password must be at least 6 characters');
      return;
    }

    setLoading(true);
    try {
      const response = await authAPI.register({ fullName, email, password });
      login(response.data.data);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message || 'Registration failed. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="auth-page">
      <section className="auth-shell auth-shell--reverse">
        <aside className="auth-showcase" aria-label="Account benefits">
          <div className="auth-showcase__brand">
            <span className="material-symbols-outlined">account_balance_wallet</span>
            <span>SplitSmart</span>
          </div>
          <div>
            <p className="auth-eyebrow">Create your workspace</p>
            <h1 className="auth-showcase__title">Bring every shared bill into one clean view.</h1>
            <p className="auth-showcase__text">
              Add groups, invite members, split by exact amounts or percentages, and keep settlements simple.
            </p>
          </div>
          <div className="auth-feature-list">
            <span><span className="material-symbols-outlined">groups</span> Group-based tracking</span>
            <span><span className="material-symbols-outlined">receipt_long</span> Expense history</span>
            <span><span className="material-symbols-outlined">handshake</span> Settlement suggestions</span>
          </div>
        </aside>

        <div className="auth-card">
          <div className="auth-brand">
            <div className="auth-brand__icon-wrapper">
              <span className="material-symbols-outlined auth-brand__icon">person_add</span>
            </div>
            <div>
              <h1 className="auth-brand__name">Create account</h1>
              <p className="auth-brand__tagline">Start tracking shared expenses in minutes.</p>
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
              <label className="form-label" htmlFor="reg-name">Full name</label>
              <div className="form-input-wrapper">
                <span className="material-symbols-outlined form-input-icon">person</span>
                <input
                  id="reg-name"
                  type="text"
                  className="form-input form-input--icon"
                  placeholder="Jane Doe"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  required
                  autoComplete="name"
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="reg-email">Email address</label>
              <div className="form-input-wrapper">
                <span className="material-symbols-outlined form-input-icon">mail</span>
                <input
                  id="reg-email"
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
              <label className="form-label" htmlFor="reg-password">Password</label>
              <div className="form-input-wrapper">
                <span className="material-symbols-outlined form-input-icon">lock</span>
                <input
                  id="reg-password"
                  type={showPassword ? 'text' : 'password'}
                  className="form-input form-input--icon"
                  placeholder="Minimum 6 characters"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  required
                  autoComplete="new-password"
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

            <div className="form-group">
              <label className="form-label" htmlFor="reg-confirm">Confirm password</label>
              <div className="form-input-wrapper">
                <span className="material-symbols-outlined form-input-icon">lock_reset</span>
                <input
                  id="reg-confirm"
                  type={showPassword ? 'text' : 'password'}
                  className={`form-input form-input--icon ${confirmPassword && password !== confirmPassword ? 'form-input--error' : ''}`}
                  placeholder="Re-enter password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  required
                  autoComplete="new-password"
                />
              </div>
              {confirmPassword && password !== confirmPassword && (
                <span className="form-error">Passwords do not match</span>
              )}
            </div>

            <button type="submit" className="btn btn--primary btn--full btn--lg" disabled={loading} id="register-submit">
              {loading ? (
                <div className="spinner spinner--sm spinner--light" />
              ) : (
                <>
                  Create account
                  <span className="material-symbols-outlined">arrow_forward</span>
                </>
              )}
            </button>
          </form>

          <div className="auth-footer">
            Already have an account? <Link to="/login">Sign in</Link>
          </div>
        </div>
      </section>
    </div>
  );
}
