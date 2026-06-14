import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { groupAPI, balanceAPI } from '../services/api';

const COLORS = ['primary', 'secondary', 'tertiary'];

function getInitials(name) {
  if (!name) return '??';
  return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
}

function formatCurrency(amount) {
  const num = parseFloat(amount) || 0;
  return new Intl.NumberFormat('en-IN', {
    style: 'currency', currency: 'INR',
    minimumFractionDigits: 0, maximumFractionDigits: 0,
  }).format(Math.abs(num));
}

export default function DashboardPage() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [groups, setGroups] = useState([]);
  const [balances, setBalances] = useState({});
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({ totalExpenses: 0, youOwe: 0, youAreOwed: 0, activeGroups: 0 });

  useEffect(() => {
    loadData();
  }, []);

  async function loadData() {
    try {
      const groupRes = await groupAPI.getMyGroups();
      const groupList = groupRes.data.data || [];
      setGroups(groupList);

      let totalExpenses = 0, youOwe = 0, youAreOwed = 0;

      const balanceMap = {};
      for (const group of groupList) {
        try {
          const balRes = await balanceAPI.getByGroup(group.id);
          const balData = balRes.data.data;
          balanceMap[group.id] = balData;

          const myBalance = balData?.balances?.find(b => b.user?.id === user?.id);
          if (myBalance) {
            totalExpenses += parseFloat(myBalance.totalPaid || 0);
            const net = parseFloat(myBalance.netBalance || 0);
            if (net > 0) youAreOwed += net;
            else if (net < 0) youOwe += Math.abs(net);
          }
        } catch (e) { /* skip groups with no data */ }
      }

      setBalances(balanceMap);
      setStats({ totalExpenses, youOwe, youAreOwed, activeGroups: groupList.length });
    } catch (e) {
      console.error('Failed to load dashboard data', e);
    } finally {
      setLoading(false);
    }
  }

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center" style={{ minHeight: '60vh' }}>
        <div className="spinner spinner--lg" />
        <p className="text-body-sm text-muted" style={{ marginTop: '16px' }}>Loading your dashboard...</p>
      </div>
    );
  }

  return (
    <div>
      {/* Welcome Header */}
      <div style={{ marginBottom: '24px' }}>
        <h1 className="text-headline-lg-mobile" style={{ marginBottom: '4px' }}>Dashboard</h1>
        <p className="text-body-sm text-muted">
          Welcome back! Here's what's happening with your expenses.
        </p>
      </div>

      {/* Stats Grid */}
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px', marginBottom: '24px' }}>
        <div className="stat-card" style={{ background: 'rgba(70,72,212,0.08)' }}>
          <div className="stat-card__icon" style={{ background: 'rgba(70,72,212,0.15)' }}>
            <span className="material-symbols-outlined" style={{ color: 'var(--primary)', fontSize: '20px' }}>receipt_long</span>
          </div>
          <div>
            <div className="stat-card__label">Total Expenses</div>
            <div className="stat-card__value">{formatCurrency(stats.totalExpenses)}</div>
          </div>
        </div>

        <div className="stat-card" style={{ background: 'rgba(186,26,26,0.06)' }}>
          <div className="stat-card__icon" style={{ background: 'rgba(186,26,26,0.12)' }}>
            <span className="material-symbols-outlined" style={{ color: 'var(--error)', fontSize: '20px' }}>trending_up</span>
          </div>
          <div>
            <div className="stat-card__label">You Owe</div>
            <div className="stat-card__value text-negative">{formatCurrency(stats.youOwe)}</div>
          </div>
        </div>

        <div className="stat-card" style={{ background: 'rgba(0,108,73,0.06)' }}>
          <div className="stat-card__icon" style={{ background: 'rgba(0,108,73,0.12)' }}>
            <span className="material-symbols-outlined" style={{ color: 'var(--tertiary)', fontSize: '20px' }}>savings</span>
          </div>
          <div>
            <div className="stat-card__label">You're Owed</div>
            <div className="stat-card__value text-positive">{formatCurrency(stats.youAreOwed)}</div>
          </div>
        </div>

        <div className="stat-card" style={{ background: 'rgba(107,56,212,0.06)' }}>
          <div className="stat-card__icon" style={{ background: 'rgba(107,56,212,0.12)' }}>
            <span className="material-symbols-outlined" style={{ color: 'var(--secondary)', fontSize: '20px' }}>groups</span>
          </div>
          <div>
            <div className="stat-card__label">Active Groups</div>
            <div className="stat-card__value">{stats.activeGroups}</div>
          </div>
        </div>
      </div>

      {/* Your Groups */}
      <div className="section-header">
        <h2 className="section-header__title">Your Groups</h2>
        <button className="section-header__action" onClick={() => navigate('/groups')}>View All</button>
      </div>

      {groups.length === 0 ? (
        <div className="empty-state">
          <span className="material-symbols-outlined empty-state__icon">group_add</span>
          <h3 className="empty-state__title">No groups yet</h3>
          <p className="empty-state__text">Create your first group to start tracking shared expenses.</p>
          <button className="btn btn--primary" style={{ marginTop: '16px' }} onClick={() => navigate('/groups')}>
            <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>add</span>
            Create Group
          </button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: '12px', marginBottom: '24px' }}>
          {groups.slice(0, 4).map((group, idx) => {
            const groupBal = balances[group.id];
            const myBal = groupBal?.balances?.find(b => b.user?.id === user?.id);
            const net = parseFloat(myBal?.netBalance || 0);
            const members = group.members || [];

            return (
              <div key={group.id} className="card card--clickable"
                style={{ padding: '16px' }}
                onClick={() => navigate(`/groups/${group.id}`)}>
                <h3 className="text-label-md" style={{ marginBottom: '8px' }}>{group.name}</h3>
                <div className="avatar-stack" style={{ marginBottom: '12px' }}>
                  {members.slice(0, 3).map((m, i) => (
                    <div key={m.membershipId || i} className={`avatar avatar--sm avatar--${COLORS[i % 3]}`}>
                      {getInitials(m.user?.fullName)}
                    </div>
                  ))}
                  {members.length > 3 && (
                    <div className="avatar avatar--sm avatar--surface">+{members.length - 3}</div>
                  )}
                </div>
                <div style={{ fontSize: '12px', color: 'var(--on-surface-variant)' }}>
                  Your Balance: {' '}
                  <span className={net > 0 ? 'text-positive' : net < 0 ? 'text-negative' : ''} style={{ fontWeight: 700 }}>
                    {net === 0 ? 'Settled' : net > 0 ? `You're owed ${formatCurrency(net)}` : `You owe ${formatCurrency(net)}`}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Recent Activity */}
      <div className="section-header">
        <h2 className="section-header__title">Recent Activity</h2>
      </div>

      {groups.length === 0 ? (
        <p className="text-body-sm text-muted text-center" style={{ padding: '24px' }}>
          No activity yet. Create a group and add expenses to see activity here.
        </p>
      ) : (
        <div className="card" style={{ padding: '0 16px' }}>
          {[
            { icon: 'restaurant', title: 'Dinner at Zomato', sub: 'Aisha paid ₹1,200', amount: '₹300', amountLabel: 'your share', type: 'negative' },
            { icon: 'bolt', title: 'Electricity Bill', sub: 'Rohan paid ₹2,400', amount: '₹600', amountLabel: 'your share', type: 'negative' },
            { icon: 'home', title: 'Rent', sub: 'You paid ₹20,000', amount: '+₹15,000', amountLabel: 'owed to you', type: 'positive' },
          ].map((item, i) => (
            <div key={i} className="expense-item">
              <div className="expense-item__icon">
                <span className="material-symbols-outlined">{item.icon}</span>
              </div>
              <div className="expense-item__content">
                <div className="expense-item__title">{item.title}</div>
                <div className="expense-item__subtitle">{item.sub}</div>
              </div>
              <div className="expense-item__amount">
                <div className={`expense-item__amount-value ${item.type === 'positive' ? 'text-positive' : 'text-negative'}`}>
                  {item.amount}
                </div>
                <div className="expense-item__amount-label">{item.amountLabel}</div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* FAB */}
      <button className="fab" onClick={() => navigate('/groups')} title="Add new expense">
        <span className="material-symbols-outlined" style={{ fontSize: '28px' }}>add</span>
      </button>
    </div>
  );
}
