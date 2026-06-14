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
      <div className="auth-card">
        {/* Brand */}
        <div className="auth-brand">
          <div className="auth-brand__icon-wrapper">
            <span className="material-symbols-outlined auth-brand__icon">account_balance_wallet</span>
          </div>
          <h1 className="auth-brand__name">SplitSmart</h1>
          <p className="auth-brand__tagline">Join the community and start splitting.</p>
        </div>

        {/* Error */}
        {error && (
          <div style={{
            background: 'var(--error-container)', color: 'var(--on-error-container)',
            padding: '12px 16px', borderRadius: 'var(--radius-lg)',
            marginBottom: '16px', fontSize: '14px', fontWeight: 500,
          }}>
            {error}
          </div>
        )}

        {/* Form */}
        <form onSubmit={handleSubmit}>
          <div className="form-group" style={{ marginBottom: '16px' }}>
            <label className="form-label" htmlFor="reg-name">Full Name</label>
            <div className="form-input-wrapper">
              <span className="material-symbols-outlined form-input-icon">person</span>
              <input id="reg-name" type="text" className="form-input form-input--icon"
                placeholder="Jane Doe" value={fullName}
                onChange={(e) => setFullName(e.target.value)} required />
            </div>
          </div>

          <div className="form-group" style={{ marginBottom: '16px' }}>
            <label className="form-label" htmlFor="reg-email">Email Address</label>
            <div className="form-input-wrapper">
              <span className="material-symbols-outlined form-input-icon">mail</span>
              <input id="reg-email" type="email" className="form-input form-input--icon"
                placeholder="name@example.com" value={email}
                onChange={(e) => setEmail(e.target.value)} required />
            </div>
          </div>

          <div className="form-group" style={{ marginBottom: '16px' }}>
            <label className="form-label" htmlFor="reg-password">Password</label>
            <div className="form-input-wrapper">
              <span className="material-symbols-outlined form-input-icon">lock</span>
              <input id="reg-password" type={showPassword ? 'text' : 'password'}
                className="form-input form-input--icon" placeholder="••••••••"
                value={password} onChange={(e) => setPassword(e.target.value)} required />
              <button type="button" className="form-input-suffix"
                onClick={() => setShowPassword(!showPassword)} tabIndex={-1}>
                <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>
                  {showPassword ? 'visibility_off' : 'visibility'}
                </span>
              </button>
            </div>
            <span className="form-helper">Min. 6 characters</span>
          </div>

          <div className="form-group" style={{ marginBottom: '24px' }}>
            <label className="form-label" htmlFor="reg-confirm">Confirm Password</label>
            <div className="form-input-wrapper">
              <span className="material-symbols-outlined form-input-icon">lock</span>
              <input id="reg-confirm" type={showPassword ? 'text' : 'password'}
                className={`form-input form-input--icon ${confirmPassword && password !== confirmPassword ? 'form-input--error' : ''}`}
                placeholder="••••••••" value={confirmPassword}
                onChange={(e) => setConfirmPassword(e.target.value)} required />
            </div>
            {confirmPassword && password !== confirmPassword && (
              <span className="form-error">Passwords do not match</span>
            )}
          </div>

          <button type="submit" className="btn btn--primary btn--full" disabled={loading} id="register-submit">
            {loading ? (
              <div className="spinner spinner--sm" style={{ borderTopColor: 'white', borderColor: 'rgba(255,255,255,0.3)' }} />
            ) : (
              <>Create Account <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>arrow_forward</span></>
            )}
          </button>
        </form>

        <div className="auth-footer">
          Already have an account? <Link to="/login">Login here</Link>
        </div>
      </div>
    </div>
  );
}
