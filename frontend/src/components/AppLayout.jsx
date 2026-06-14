import React, { useState } from 'react';
import { Outlet, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

export default function AppLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();

  const getInitials = (name) => {
    if (!name) return '??';
    return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
  };

  const navItems = [
    { path: '/', icon: 'home', label: 'Home', exact: true },
    { path: '/groups', icon: 'group', label: 'Groups' },
    { path: '/activity', icon: 'history', label: 'Activity' },
  ];

  const isActive = (item) => {
    if (item.exact) return location.pathname === item.path;
    return location.pathname.startsWith(item.path);
  };

  return (
    <div className="app-layout">
      {/* Top App Bar */}
      <header className="top-app-bar">
        <NavLink to="/" className="top-app-bar__brand">
          <span className="material-symbols-outlined top-app-bar__brand-icon">account_balance_wallet</span>
          <span className="top-app-bar__brand-name">SplitSmart</span>
        </NavLink>

        <div className="flex items-center gap-md">
          <div className="top-app-bar__avatar" onClick={logout} title="Sign out">
            {getInitials(user?.fullName)}
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="app-main">
        <Outlet />
      </main>

      {/* Bottom Navigation (mobile) */}
      <nav className="bottom-nav">
        {navItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={`bottom-nav__item ${isActive(item) ? 'bottom-nav__item--active' : ''}`}
            end={item.exact}
          >
            <span className={`material-symbols-outlined ${isActive(item) ? 'filled' : ''}`}>
              {item.icon}
            </span>
            <span className="bottom-nav__item-label">{item.label}</span>
          </NavLink>
        ))}
      </nav>
    </div>
  );
}
