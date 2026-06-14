import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { groupAPI, expenseAPI, balanceAPI, paymentAPI, userAPI } from '../services/api';

const COLORS = ['primary', 'secondary', 'tertiary'];
const SPLIT_TYPES = ['EQUAL', 'EXACT', 'PERCENTAGE', 'SHARES'];
const CATEGORY_ICONS = {
  Food: 'restaurant', Transport: 'directions_car', Shopping: 'shopping_bag',
  Entertainment: 'movie', Bills: 'receipt_long', Other: 'more_horiz',
  Rent: 'home', Groceries: 'local_grocery_store', Default: 'payments',
};

function getInitials(n) { return n ? n.split(' ').map(w => w[0]).join('').toUpperCase().slice(0, 2) : '??'; }
function fmt(amount) {
  const n = parseFloat(amount) || 0;
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', minimumFractionDigits: 0, maximumFractionDigits: 2 }).format(Math.abs(n));
}

export default function GroupDetailPage() {
  const { groupId } = useParams();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [group, setGroup] = useState(null);
  const [expenses, setExpenses] = useState([]);
  const [balanceData, setBalanceData] = useState(null);
  const [payments, setPayments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState('expenses');
  const [showAddExpense, setShowAddExpense] = useState(false);
  const [showSettle, setShowSettle] = useState(false);
  const [showAddMember, setShowAddMember] = useState(false);

  const loadAll = useCallback(async () => {
    try {
      const [gRes, eRes, bRes, pRes] = await Promise.all([
        groupAPI.getById(groupId),
        expenseAPI.getByGroup(groupId),
        balanceAPI.getByGroup(groupId),
        paymentAPI.getByGroup(groupId),
      ]);
      setGroup(gRes.data.data);
      setExpenses(eRes.data.data || []);
      setBalanceData(bRes.data.data);
      setPayments(pRes.data.data || []);
    } catch (e) { console.error(e); }
    finally { setLoading(false); }
  }, [groupId]);

  useEffect(() => { loadAll(); }, [loadAll]);

  if (loading) return (
    <div className="flex flex-col items-center justify-center" style={{ minHeight: '60vh' }}>
      <div className="spinner spinner--lg" /><p className="text-body-sm text-muted mt-md">Loading group...</p>
    </div>
  );

  if (!group) return (
    <div className="empty-state">
      <span className="material-symbols-outlined empty-state__icon">error</span>
      <h3 className="empty-state__title">Group not found</h3>
      <button className="btn btn--outline mt-md" onClick={() => navigate('/groups')}>Back to Groups</button>
    </div>
  );

  const members = group.members || [];
  const activeMembers = members.filter(m => !m.leftAt);
  const pastMembers = members.filter(m => m.leftAt);
  const totalSpent = expenses.reduce((sum, e) => sum + parseFloat(e.amountInInr || 0), 0);
  const myBal = balanceData?.balances?.find(b => b.user?.id === user?.id);
  const myNet = parseFloat(myBal?.netBalance || 0);

  return (
    <div>
      {/* Back + Header */}
      <button className="btn btn--ghost btn--sm" style={{ marginBottom: '8px', paddingLeft: 0 }}
        onClick={() => navigate('/groups')}>
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>arrow_back</span> Group Details
      </button>

      {/* Group Info Card */}
      <div className="card card--primary card--stat" style={{ marginBottom: '20px', padding: '20px' }}>
        <div style={{ position: 'relative', zIndex: 1 }}>
          <div className="flex items-center gap-md" style={{ marginBottom: '12px' }}>
            <div className="avatar avatar--lg" style={{ background: 'rgba(255,255,255,0.15)', color: 'white', borderRadius: 'var(--radius-xl)' }}>
              <span className="material-symbols-outlined" style={{ fontSize: '24px' }}>group</span>
            </div>
            <div>
              <h1 className="text-headline-md" style={{ color: 'white' }}>{group.name}</h1>
              {group.description && <p className="text-label-sm" style={{ color: 'rgba(255,255,255,0.7)' }}>{group.description}</p>}
            </div>
          </div>

          <div className="text-label-sm" style={{ color: 'rgba(255,255,255,0.7)', marginBottom: '12px' }}>
            Total Group Spending
          </div>
          <div style={{ fontSize: '28px', fontWeight: 800, color: 'white', marginBottom: '16px' }}>
            {fmt(totalSpent)}
          </div>

          <div style={{ display: 'flex', gap: '12px' }}>
            <div style={{ flex: 1, background: 'rgba(255,255,255,0.1)', padding: '12px', borderRadius: 'var(--radius-xl)' }}>
              <div style={{ fontSize: '10px', fontWeight: 700, textTransform: 'uppercase', opacity: 0.6, marginBottom: '4px', color: 'white' }}>You {myNet >= 0 ? 'are owed' : 'owe'}</div>
              <div className="text-label-md" style={{ color: 'white' }}>{fmt(myNet)}</div>
            </div>
            <div style={{ flex: 1, background: 'rgba(255,255,255,0.1)', padding: '12px', borderRadius: 'var(--radius-xl)' }}>
              <div style={{ fontSize: '10px', fontWeight: 700, textTransform: 'uppercase', opacity: 0.6, marginBottom: '4px', color: 'white' }}>Members</div>
              <div className="text-label-md" style={{ color: 'white' }}>{activeMembers.length} active</div>
            </div>
          </div>
        </div>
      </div>

      {/* Settle Up Button */}
      {myNet !== 0 && (
        <button className="btn btn--secondary btn--full" style={{ marginBottom: '16px' }}
          onClick={() => setShowSettle(true)}>
          <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>handshake</span>
          Settle Up
        </button>
      )}

      {/* Tab Bar */}
      <div className="tab-bar">
        {[
          { key: 'expenses', icon: 'receipt_long', label: 'Expenses' },
          { key: 'balances', icon: 'account_balance', label: 'Balances' },
          { key: 'members', icon: 'people', label: 'Members' },
          { key: 'activity', icon: 'history', label: 'Activity' },
        ].map(tab => (
          <button key={tab.key}
            className={`tab-bar__item ${activeTab === tab.key ? 'tab-bar__item--active' : ''}`}
            onClick={() => setActiveTab(tab.key)}>
            <span className="material-symbols-outlined" style={{ fontSize: '20px' }}>{tab.icon}</span>
            {tab.label}
          </button>
        ))}
      </div>

      {/* Tab Content */}
      {activeTab === 'expenses' && <ExpensesTab expenses={expenses} user={user} onAdd={() => setShowAddExpense(true)} />}
      {activeTab === 'balances' && <BalancesTab balanceData={balanceData} settlements={balanceData?.settlements} />}
      {activeTab === 'members' && <MembersTab activeMembers={activeMembers} pastMembers={pastMembers} onAddMember={() => setShowAddMember(true)} />}
      {activeTab === 'activity' && <ActivityTab expenses={expenses} payments={payments} />}

      {/* Add Expense FAB */}
      <button className="fab" onClick={() => setShowAddExpense(true)} title="Add expense">
        <span className="material-symbols-outlined" style={{ fontSize: '28px' }}>add</span>
      </button>

      {/* Add Expense Modal */}
      {showAddExpense && <AddExpenseModal groupId={groupId} members={activeMembers} user={user}
        onClose={() => setShowAddExpense(false)} onSaved={() => { setShowAddExpense(false); loadAll(); }} />}

      {/* Settle Up Modal */}
      {showSettle && <SettleModal groupId={groupId} settlements={balanceData?.settlements} user={user}
        onClose={() => setShowSettle(false)} onSaved={() => { setShowSettle(false); loadAll(); }} />}

      {/* Add Member Modal */}
      {showAddMember && <AddMemberModal groupId={groupId}
        onClose={() => setShowAddMember(false)} onSaved={() => { setShowAddMember(false); loadAll(); }} />}
    </div>
  );
}

/* ================================================================ */
/* EXPENSES TAB                                                      */
/* ================================================================ */
function ExpensesTab({ expenses, user, onAdd }) {
  if (expenses.length === 0) return (
    <div className="empty-state">
      <span className="material-symbols-outlined empty-state__icon">receipt_long</span>
      <h3 className="empty-state__title">No expenses yet</h3>
      <p className="empty-state__text">Add your first expense to start tracking.</p>
      <button className="btn btn--primary mt-md" onClick={onAdd}>
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>add</span> Add Expense
      </button>
    </div>
  );

  return (
    <div className="card" style={{ padding: '0 16px' }}>
      {expenses.map((exp) => {
        const icon = CATEGORY_ICONS[exp.category] || CATEGORY_ICONS.Default;
        const isPayer = exp.paidBy?.id === user?.id;
        return (
          <div key={exp.id} className="expense-item">
            <div className="expense-item__icon">
              <span className="material-symbols-outlined">{icon}</span>
            </div>
            <div className="expense-item__content">
              <div className="expense-item__title">{exp.description}</div>
              <div className="expense-item__subtitle">
                {isPayer ? 'You' : exp.paidBy?.fullName} paid · {exp.splitType}
              </div>
            </div>
            <div className="expense-item__amount">
              <div className={`expense-item__amount-value ${isPayer ? 'text-positive' : 'text-negative'}`}>
                {isPayer ? '+' : '-'}{fmt(exp.amountInInr)}
              </div>
              <div className="expense-item__amount-label">
                {isPayer ? 'you paid' : 'your share'}
              </div>
            </div>
          </div>
        );
      })}
    </div>
  );
}

/* ================================================================ */
/* BALANCES TAB                                                      */
/* ================================================================ */
function BalancesTab({ balanceData, settlements }) {
  if (!balanceData) return <div className="spinner spinner--md" style={{ margin: '40px auto' }} />;
  const balances = balanceData.balances || [];

  return (
    <div>
      {/* Settlement Suggestions (Aisha's view) */}
      {settlements && settlements.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <h3 className="text-label-md" style={{ marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--outline)' }}>
            Suggested Settlements
          </h3>
          {settlements.map((s, i) => (
            <div key={i} className="settlement-card" style={{ marginBottom: '8px' }}>
              <div className="settlement-card__text">
                {s.fromUser?.fullName} owes {s.toUser?.fullName}
              </div>
              <div className="settlement-card__amount">{fmt(s.amount)}</div>
            </div>
          ))}
          {settlements.length === 0 && (
            <p className="text-body-sm text-muted">No outstanding settlements needed. 🎉</p>
          )}
        </div>
      )}

      {/* Individual Balances */}
      <h3 className="text-label-md" style={{ marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--outline)' }}>
        Individual Balances
      </h3>
      <div className="card" style={{ padding: '0 16px' }}>
        {balances.map((bal, i) => {
          const net = parseFloat(bal.netBalance || 0);
          return (
            <div key={i} className="balance-bar">
              <div className="balance-bar__avatar">
                <div className={`avatar avatar--md avatar--${COLORS[i % 3]}`}>
                  {getInitials(bal.user?.fullName)}
                </div>
              </div>
              <div className="balance-bar__name">{bal.user?.fullName}</div>
              <div className="balance-bar__status">
                {net > 0 ? 'is owed' : net < 0 ? 'owes' : 'settled'}
              </div>
              <div className={`balance-bar__amount ${net > 0 ? 'text-positive' : net < 0 ? 'text-negative' : ''}`}>
                {net === 0 ? '✓' : (net > 0 ? '+' : '-') + fmt(net)}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

/* ================================================================ */
/* MEMBERS TAB                                                       */
/* ================================================================ */
function MembersTab({ activeMembers, pastMembers, onAddMember }) {
  return (
    <div>
      <button className="btn btn--outline btn--full" style={{ marginBottom: '16px' }} onClick={onAddMember}>
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>person_add</span>
        Add Member
      </button>

      {/* Active */}
      <h3 className="text-label-md" style={{ marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--outline)' }}>
        Active Members
      </h3>
      <div className="card" style={{ padding: '0 16px', marginBottom: '20px' }}>
        {activeMembers.map((m, i) => (
          <div key={m.membershipId || i} className="member-item">
            <div className={`avatar avatar--md avatar--${COLORS[i % 3]}`}>
              {getInitials(m.user?.fullName)}
            </div>
            <div className="member-item__info">
              <div className="member-item__name">{m.user?.fullName}
                {m.role === 'ADMIN' && <span className="badge badge--info" style={{ marginLeft: '8px', fontSize: '10px' }}>Admin</span>}
              </div>
              <div className="member-item__date">Joined {new Date(m.joinedAt).toLocaleDateString()}</div>
            </div>
            <span className="material-symbols-outlined text-muted">more_vert</span>
          </div>
        ))}
      </div>

      {/* Past */}
      {pastMembers.length > 0 && (
        <>
          <h3 className="text-label-md" style={{ marginBottom: '12px', textTransform: 'uppercase', letterSpacing: '0.04em', color: 'var(--outline)' }}>
            Past Members
          </h3>
          <div className="card" style={{ padding: '0 16px' }}>
            {pastMembers.map((m, i) => (
              <div key={m.membershipId || i} className="member-item" style={{ opacity: 0.6 }}>
                <div className="avatar avatar--md avatar--surface">{getInitials(m.user?.fullName)}</div>
                <div className="member-item__info">
                  <div className="member-item__name">{m.user?.fullName}</div>
                  <div className="member-item__date">Left {new Date(m.leftAt).toLocaleDateString()}</div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  );
}

/* ================================================================ */
/* ACTIVITY TAB                                                      */
/* ================================================================ */
function ActivityTab({ expenses, payments }) {
  const activities = [
    ...expenses.map(e => ({
      type: 'expense', date: e.createdAt || e.expenseDate,
      icon: 'receipt_long', color: 'var(--primary)',
      title: `${e.paidBy?.fullName} added "${e.description}"`,
      sub: `${fmt(e.amountInInr)} · Split ${e.splitType?.toLowerCase()}`,
    })),
    ...payments.map(p => ({
      type: 'payment', date: p.createdAt || p.paymentDate,
      icon: 'handshake', color: 'var(--tertiary)',
      title: `${p.paidBy?.fullName} settled a debt with ${p.paidTo?.fullName}`,
      sub: fmt(p.amount),
    })),
  ].sort((a, b) => new Date(b.date) - new Date(a.date));

  if (activities.length === 0) return (
    <div className="empty-state">
      <span className="material-symbols-outlined empty-state__icon">history</span>
      <h3 className="empty-state__title">No activity yet</h3>
      <p className="empty-state__text">Expenses and payments will appear here.</p>
    </div>
  );

  return (
    <div className="card" style={{ padding: '0 16px' }}>
      {activities.slice(0, 20).map((a, i) => (
        <div key={i} className="expense-item">
          <div className="expense-item__icon">
            <span className="material-symbols-outlined" style={{ color: a.color }}>{a.icon}</span>
          </div>
          <div className="expense-item__content">
            <div className="expense-item__title">{a.title}</div>
            <div className="expense-item__subtitle">
              {a.sub} · {new Date(a.date).toLocaleDateString()}
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

/* ================================================================ */
/* ADD EXPENSE MODAL                                                 */
/* ================================================================ */
function AddExpenseModal({ groupId, members, user, onClose, onSaved }) {
  const [step, setStep] = useState(1);
  const [desc, setDesc] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('INR');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [paidBy, setPaidBy] = useState(user?.id || '');
  const [splitType, setSplitType] = useState('EQUAL');
  const [splits, setSplits] = useState({});
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  // Initialize splits when members change
  useEffect(() => {
    const initial = {};
    members.forEach(m => {
      initial[m.user?.id] = { selected: true, value: '' };
    });
    setSplits(initial);
  }, [members]);

  const selectedMembers = Object.entries(splits).filter(([_, v]) => v.selected);

  async function handleSave() {
    setError('');
    const parsedAmount = parseFloat(amount);
    if (!desc.trim()) { setError('Description is required'); return; }
    if (!parsedAmount || parsedAmount <= 0) { setError('Amount must be positive'); return; }

    setSaving(true);
    try {
      const splitDetails = {};
      if (splitType === 'EXACT') {
        selectedMembers.forEach(([id, v]) => { splitDetails[id] = parseFloat(v.value) || 0; });
      } else if (splitType === 'PERCENTAGE') {
        selectedMembers.forEach(([id, v]) => { splitDetails[id] = parseFloat(v.value) || 0; });
      } else if (splitType === 'SHARES') {
        selectedMembers.forEach(([id, v]) => { splitDetails[id] = parseInt(v.value) || 1; });
      }

      await expenseAPI.create(groupId, {
        description: desc,
        amount: parsedAmount,
        currency,
        expenseDate: date,
        paidByUserId: parseInt(paidBy),
        splitType,
        ...(splitType === 'EXACT' && { exactSplits: splitDetails }),
        ...(splitType === 'PERCENTAGE' && { percentageSplits: splitDetails }),
        ...(splitType === 'SHARES' && { sharesSplits: splitDetails }),
      });
      onSaved();
    } catch (e) {
      setError(e.response?.data?.message || 'Failed to save expense');
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()} style={{ maxHeight: '90vh', overflowY: 'auto' }}>
        <div className="modal-header">
          <h2 className="modal-header__title" style={{ color: 'var(--primary)' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '20px', marginRight: '8px', verticalAlign: 'middle' }}>add</span>
            Add Expense
          </h2>
          <div className="text-label-sm text-muted">Step {step} of 2</div>
        </div>

        {error && (
          <div style={{ background: 'var(--error-container)', color: 'var(--on-error-container)', padding: '12px', borderRadius: 'var(--radius-lg)', marginBottom: '16px', fontSize: '13px' }}>
            {error}
          </div>
        )}

        {step === 1 && (
          <>
            {/* Amount Display */}
            <div style={{ textAlign: 'center', marginBottom: '24px', padding: '16px', background: 'var(--primary-fixed)', borderRadius: 'var(--radius-xl)' }}>
              <div className="text-label-sm text-muted" style={{ marginBottom: '4px' }}>Total Expense Amount</div>
              <div style={{ fontSize: '32px', fontWeight: 800, color: 'var(--on-primary-fixed)' }}>
                {currency === 'INR' ? '₹' : '$'}{amount || '0.00'}
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: '16px' }}>
              <label className="form-label">Description</label>
              <div className="form-input-wrapper">
                <span className="material-symbols-outlined form-input-icon">edit</span>
                <input type="text" className="form-input form-input--icon" placeholder="e.g., Dinner at Zomato"
                  value={desc} onChange={e => setDesc(e.target.value)} autoFocus />
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr auto', gap: '12px', marginBottom: '16px' }}>
              <div className="form-group">
                <label className="form-label">Amount</label>
                <div className="form-input-wrapper">
                  <span className="form-input-icon" style={{ fontWeight: 700, fontSize: '16px', left: '14px' }}>{currency === 'INR' ? '₹' : '$'}</span>
                  <input type="number" className="form-input" style={{ paddingLeft: '36px' }} placeholder="0.00"
                    value={amount} onChange={e => setAmount(e.target.value)} step="0.01" />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">Currency</label>
                <select className="form-input" value={currency} onChange={e => setCurrency(e.target.value)}
                  style={{ padding: '0 12px', appearance: 'auto' }}>
                  <option value="INR">INR</option>
                  <option value="USD">USD</option>
                </select>
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: '16px' }}>
              <label className="form-label">Date</label>
              <div className="form-input-wrapper">
                <span className="material-symbols-outlined form-input-icon">calendar_today</span>
                <input type="date" className="form-input form-input--icon" value={date}
                  onChange={e => setDate(e.target.value)} />
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: '24px' }}>
              <label className="form-label">Paid By</label>
              <div className="form-input-wrapper">
                <span className="material-symbols-outlined form-input-icon">person</span>
                <select className="form-input form-input--icon" value={paidBy}
                  onChange={e => setPaidBy(e.target.value)} style={{ appearance: 'auto' }}>
                  {members.map(m => (
                    <option key={m.user?.id} value={m.user?.id}>
                      {m.user?.id === user?.id ? 'Me (You)' : m.user?.fullName}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            <button className="btn btn--primary btn--full" onClick={() => setStep(2)}>
              Next: Split Details <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>arrow_forward</span>
            </button>
          </>
        )}

        {step === 2 && (
          <>
            {/* Split Type Selector */}
            <div style={{ marginBottom: '16px' }}>
              <label className="form-label" style={{ marginBottom: '8px', display: 'block' }}>Split Type</label>
              <div className="split-selector">
                {SPLIT_TYPES.map(t => (
                  <button key={t} className={`split-selector__btn ${splitType === t ? 'split-selector__btn--active' : ''}`}
                    onClick={() => setSplitType(t)}>{t.charAt(0) + t.slice(1).toLowerCase()}</button>
                ))}
              </div>
            </div>

            {/* Amount Summary */}
            <div style={{ textAlign: 'center', marginBottom: '16px', padding: '12px', background: 'var(--surface-container-low)', borderRadius: 'var(--radius-lg)' }}>
              <span className="text-label-sm">Total Amount: </span>
              <span className="text-label-md" style={{ color: 'var(--primary)' }}>{currency === 'INR' ? '₹' : '$'}{amount}</span>
              {splitType === 'EQUAL' && selectedMembers.length > 0 && (
                <span className="text-label-sm text-muted"> · Each: {fmt(parseFloat(amount || 0) / selectedMembers.length)}</span>
              )}
            </div>

            {/* Select Members */}
            <label className="form-label" style={{ marginBottom: '8px', display: 'block' }}>
              Select Members
              <span className="text-label-sm text-muted" style={{ marginLeft: '8px' }}>
                ({selectedMembers.length} selected)
              </span>
            </label>

            <div className="card" style={{ padding: '0 16px', marginBottom: '16px' }}>
              {members.map((m, i) => {
                const uid = m.user?.id;
                const s = splits[uid] || { selected: true, value: '' };
                return (
                  <div key={uid} className="member-item" style={{ padding: '10px 0' }}>
                    <div className={`avatar avatar--sm avatar--${COLORS[i % 3]}`}>
                      {getInitials(m.user?.fullName)}
                    </div>
                    <div style={{ flex: 1 }}>
                      <div className="text-label-md">{m.user?.fullName}</div>
                      <div className="text-label-sm text-muted">
                        {s.selected ? 'Included in split' : 'Excluded'}
                      </div>
                    </div>

                    {splitType !== 'EQUAL' && s.selected && (
                      <input type="number" className="form-input" style={{ width: '80px', height: '36px', fontSize: '13px', textAlign: 'right' }}
                        placeholder={splitType === 'PERCENTAGE' ? '%' : splitType === 'SHARES' ? 'shares' : '₹'}
                        value={s.value} onChange={e => setSplits(prev => ({
                          ...prev, [uid]: { ...prev[uid], value: e.target.value }
                        }))} />
                    )}

                    <label style={{ cursor: 'pointer' }}>
                      <input type="checkbox" checked={s.selected}
                        onChange={e => setSplits(prev => ({
                          ...prev, [uid]: { ...prev[uid], selected: e.target.checked }
                        }))}
                        style={{ width: '18px', height: '18px', accentColor: 'var(--primary)' }} />
                    </label>
                  </div>
                );
              })}
            </div>

            {splitType === 'EQUAL' && (
              <div style={{ padding: '12px', background: 'var(--tertiary-fixed)', borderRadius: 'var(--radius-lg)', marginBottom: '16px', display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span className="material-symbols-outlined" style={{ color: 'var(--tertiary)', fontSize: '18px' }}>check_circle</span>
                <span className="text-body-sm" style={{ color: 'var(--on-tertiary-fixed-variant)' }}>
                  Everyone selected will be charged <strong>{fmt(parseFloat(amount || 0) / (selectedMembers.length || 1))}</strong>.
                </span>
              </div>
            )}

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
              <button className="btn btn--outline" onClick={() => setStep(1)}>
                <span className="material-symbols-outlined" style={{ fontSize: '16px' }}>arrow_back</span> Back
              </button>
              <button className="btn btn--primary" onClick={handleSave} disabled={saving}>
                {saving ? <div className="spinner spinner--sm" style={{ borderTopColor: 'white', borderColor: 'rgba(255,255,255,0.3)' }} /> :
                  <><span className="material-symbols-outlined" style={{ fontSize: '16px' }}>check</span> Save Expense</>}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

/* ================================================================ */
/* SETTLE MODAL                                                      */
/* ================================================================ */
function SettleModal({ groupId, settlements, user, onClose, onSaved }) {
  const [selectedIdx, setSelectedIdx] = useState(0);
  const [amount, setAmount] = useState('');
  const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
  const [saving, setSaving] = useState(false);

  const s = settlements?.[selectedIdx];

  useEffect(() => {
    if (s) setAmount(s.amount?.toString() || '');
  }, [s]);

  async function handleSettle() {
    if (!s) return;
    setSaving(true);
    try {
      await paymentAPI.create(groupId, {
        paidByUserId: s.fromUser?.id,
        paidToUserId: s.toUser?.id,
        amount: parseFloat(amount),
        paymentDate: date,
        notes: `Settlement: ${s.description}`,
      });
      onSaved();
    } catch (e) {
      alert(e.response?.data?.message || 'Failed to record payment');
    } finally { setSaving(false); }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-header__title">
            <span className="material-symbols-outlined" style={{ fontSize: '20px', marginRight: '8px', verticalAlign: 'middle', color: 'var(--secondary)' }}>handshake</span>
            Settle Up
          </h2>
          <button className="modal-close" onClick={onClose}>
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>

        {!settlements || settlements.length === 0 ? (
          <div className="empty-state" style={{ padding: '24px' }}>
            <span className="material-symbols-outlined" style={{ fontSize: '48px', color: 'var(--tertiary)' }}>check_circle</span>
            <h3 className="empty-state__title mt-md">All settled! 🎉</h3>
            <p className="empty-state__text">No outstanding balances in this group.</p>
          </div>
        ) : (
          <>
            {settlements.map((st, i) => (
              <div key={i} className="settlement-card" style={{ marginBottom: '8px', cursor: 'pointer', border: selectedIdx === i ? '2px solid var(--primary)' : undefined }}
                onClick={() => setSelectedIdx(i)}>
                <div className="settlement-card__text">{st.fromUser?.fullName} → {st.toUser?.fullName}</div>
                <div className="settlement-card__amount">{fmt(st.amount)}</div>
              </div>
            ))}

            <div className="divider" />

            <div className="form-group" style={{ marginBottom: '16px' }}>
              <label className="form-label">Payment Amount</label>
              <div className="form-input-wrapper">
                <span className="form-input-icon" style={{ fontWeight: 700, fontSize: '16px', left: '14px' }}>₹</span>
                <input type="number" className="form-input" style={{ paddingLeft: '36px' }}
                  value={amount} onChange={e => setAmount(e.target.value)} />
              </div>
            </div>

            <div className="form-group" style={{ marginBottom: '24px' }}>
              <label className="form-label">Payment Date</label>
              <input type="date" className="form-input" value={date} onChange={e => setDate(e.target.value)} />
            </div>

            <button className="btn btn--secondary btn--full" onClick={handleSettle} disabled={saving}>
              {saving ? <div className="spinner spinner--sm" style={{ borderTopColor: 'white', borderColor: 'rgba(255,255,255,0.3)' }} /> :
                <><span className="material-symbols-outlined" style={{ fontSize: '18px' }}>check_circle</span> Mark Paid</>}
            </button>
          </>
        )}
      </div>
    </div>
  );
}

/* ================================================================ */
/* ADD MEMBER MODAL                                                  */
/* ================================================================ */
function AddMemberModal({ groupId, onClose, onSaved }) {
  const [userId, setUserId] = useState('');
  const [joinedAt, setJoinedAt] = useState(new Date().toISOString().split('T')[0]);
  const [users, setUsers] = useState([]);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    userAPI.getAll()
      .then((res) => setUsers(res.data.data || []))
      .catch(() => setUsers([]));
  }, []);

  async function handleAdd(e) {
    e.preventDefault();
    if (!userId) return;
    setSaving(true);
    try {
      await groupAPI.addMember(groupId, { userId: parseInt(userId), joinedAt });
      onSaved();
    } catch (e) {
      alert(e.response?.data?.message || 'Failed to add member');
    } finally { setSaving(false); }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content" onClick={e => e.stopPropagation()}>
        <div className="modal-header">
          <h2 className="modal-header__title">Add Member</h2>
          <button className="modal-close" onClick={onClose}>
            <span className="material-symbols-outlined">close</span>
          </button>
        </div>

        <form onSubmit={handleAdd}>
          <div className="form-group" style={{ marginBottom: '16px' }}>
            <label className="form-label">User</label>
            {users.length > 0 ? (
              <select className="form-input" value={userId}
                onChange={e => setUserId(e.target.value)} required autoFocus>
                <option value="">Select a user</option>
                {users.map((u) => (
                  <option key={u.id} value={u.id}>{u.fullName} ({u.email})</option>
                ))}
              </select>
            ) : (
              <input type="number" className="form-input" placeholder="Enter user ID"
                value={userId} onChange={e => setUserId(e.target.value)} required autoFocus />
            )}
          </div>

          <div className="form-group" style={{ marginBottom: '24px' }}>
            <label className="form-label">Joined Date</label>
            <input type="date" className="form-input" value={joinedAt}
              onChange={e => setJoinedAt(e.target.value)} />
          </div>

          <button type="submit" className="btn btn--primary btn--full" disabled={saving}>
            {saving ? <div className="spinner spinner--sm" style={{ borderTopColor: 'white', borderColor: 'rgba(255,255,255,0.3)' }} /> :
              <><span className="material-symbols-outlined" style={{ fontSize: '18px' }}>person_add</span> Add Member</>}
          </button>
        </form>
      </div>
    </div>
  );
}
