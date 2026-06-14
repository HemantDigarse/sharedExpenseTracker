import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { groupAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const COLORS = ['primary', 'secondary', 'tertiary'];
function getInitials(name) {
  if (!name) return '??';
  return name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
}

export default function GroupsPage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [newName, setNewName] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [creating, setCreating] = useState(false);
  const [search, setSearch] = useState('');

  useEffect(() => { loadGroups(); }, []);

  async function loadGroups() {
    try {
      const res = await groupAPI.getMyGroups();
      setGroups(res.data.data || []);
    } catch (e) {
      console.error('Failed to load groups', e);
    } finally {
      setLoading(false);
    }
  }

  async function handleCreate(e) {
    e.preventDefault();
    if (!newName.trim()) return;
    setCreating(true);
    try {
      await groupAPI.create({ name: newName, description: newDesc });
      setNewName(''); setNewDesc(''); setShowCreate(false);
      loadGroups();
    } catch (e) {
      alert(e.response?.data?.message || 'Failed to create group');
    } finally {
      setCreating(false);
    }
  }

  const filtered = groups.filter(g =>
    g.name?.toLowerCase().includes(search.toLowerCase())
  );

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center" style={{ minHeight: '60vh' }}>
        <div className="spinner spinner--lg" />
      </div>
    );
  }

  return (
    <div>
      {/* Header */}
      <div style={{ marginBottom: '20px' }}>
        <h1 className="text-headline-lg-mobile" style={{ marginBottom: '4px' }}>My Groups</h1>
        <p className="text-body-sm text-muted">Manage shared expenses with friends and family.</p>
      </div>

      {/* New Group Button */}
      <button className="btn btn--primary btn--full" style={{ marginBottom: '20px' }}
        onClick={() => setShowCreate(true)}>
        <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>add</span>
        New Group
      </button>

      {/* Search */}
      <div className="form-input-wrapper" style={{ marginBottom: '20px' }}>
        <span className="material-symbols-outlined form-input-icon">search</span>
        <input type="text" className="form-input form-input--icon" placeholder="Search groups..."
          value={search} onChange={(e) => setSearch(e.target.value)} />
      </div>

      {/* Group List */}
      {filtered.length === 0 ? (
        <div className="empty-state">
          <span className="material-symbols-outlined empty-state__icon">group_add</span>
          <h3 className="empty-state__title">
            {search ? 'No groups found' : 'No groups yet'}
          </h3>
          <p className="empty-state__text">
            {search ? 'Try a different search term.' : 'Create your first group to start splitting expenses.'}
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {filtered.map((group, idx) => {
            const members = group.members || [];
            return (
              <div key={group.id} className="card card--clickable" style={{ padding: '16px' }}
                onClick={() => navigate(`/groups/${group.id}`)}>
                <div className="flex items-center gap-md" style={{ marginBottom: '12px' }}>
                  <div className={`avatar avatar--lg avatar--${COLORS[idx % 3]}`}
                    style={{ borderRadius: 'var(--radius-xl)' }}>
                    <span className="material-symbols-outlined" style={{ fontSize: '24px' }}>
                      {idx % 3 === 0 ? 'home' : idx % 3 === 1 ? 'flight' : 'restaurant'}
                    </span>
                  </div>
                  <div style={{ flex: 1 }}>
                    <h3 className="text-label-md">{group.name}</h3>
                    <p className="text-label-sm text-muted">
                      {group.description || `Created ${new Date(group.createdAt).toLocaleDateString()}`}
                      {members.length > 0 ? ` · ${members.length} members` : ''}
                    </p>
                  </div>
                  <span className="material-symbols-outlined text-muted">chevron_right</span>
                </div>

                <div className="flex items-center justify-between">
                  <div className="avatar-stack">
                    {members.slice(0, 4).map((m, i) => (
                      <div key={m.membershipId || i} className={`avatar avatar--sm avatar--${COLORS[i % 3]}`}>
                        {getInitials(m.user?.fullName)}
                      </div>
                    ))}
                  </div>
                  <span className="badge badge--info" style={{ fontSize: '11px' }}>View →</span>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Start a new group card */}
      <div className="card" style={{ marginTop: '16px', padding: '20px', textAlign: 'center', cursor: 'pointer', borderStyle: 'dashed' }}
        onClick={() => setShowCreate(true)}>
        <span className="material-symbols-outlined" style={{ fontSize: '32px', color: 'var(--outline)', marginBottom: '8px', display: 'block' }}>add_circle</span>
        <h3 className="text-label-md" style={{ marginBottom: '4px' }}>Start a new group</h3>
        <p className="text-label-sm text-muted">Split rent, bills, or travel expenses easily.</p>
      </div>

      {/* Create Group Modal */}
      {showCreate && (
        <div className="modal-overlay" onClick={() => setShowCreate(false)}>
          <div className="modal-content" onClick={e => e.stopPropagation()}>
            <div className="modal-header">
              <h2 className="modal-header__title">Create New Group</h2>
              <button className="modal-close" onClick={() => setShowCreate(false)}>
                <span className="material-symbols-outlined">close</span>
              </button>
            </div>

            <form onSubmit={handleCreate}>
              <div className="form-group" style={{ marginBottom: '16px' }}>
                <label className="form-label">Group Name</label>
                <input type="text" className="form-input" placeholder="e.g., Summer Trip 2024"
                  value={newName} onChange={e => setNewName(e.target.value)} required autoFocus />
              </div>

              <div className="form-group" style={{ marginBottom: '24px' }}>
                <label className="form-label">Description (Optional)</label>
                <input type="text" className="form-input" placeholder="What's this group for?"
                  value={newDesc} onChange={e => setNewDesc(e.target.value)} />
              </div>

              <button type="submit" className="btn btn--primary btn--full" disabled={creating}>
                {creating ? (
                  <div className="spinner spinner--sm" style={{ borderTopColor: 'white', borderColor: 'rgba(255,255,255,0.3)' }} />
                ) : (
                  <>
                    <span className="material-symbols-outlined" style={{ fontSize: '18px' }}>group_add</span>
                    Create Group
                  </>
                )}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}
