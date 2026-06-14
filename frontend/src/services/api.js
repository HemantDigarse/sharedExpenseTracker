import axios from 'axios';

const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
});

// Attach JWT token to every request
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Handle 401 responses (expired/invalid token)
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// Auth
export const authAPI = {
  register: (data) => api.post('/auth/register', data),
  login: (data) => api.post('/auth/login', data),
};

// Users
export const userAPI = {
  getAll: () => api.get('/users'),
};

// Groups
export const groupAPI = {
  getAll: () => api.get('/groups'),
  getById: (id) => api.get(`/groups/${id}`),
  create: (data) => api.post('/groups', data),
  getMyGroups: () => api.get('/groups/my-groups'),
  addMember: (groupId, data) => api.post(`/groups/${groupId}/members`, data),
  removeMember: (groupId, userId, data) =>
    api.put(`/groups/${groupId}/members/${userId}/leave`, data),
};

// Expenses
export const expenseAPI = {
  getByGroup: (groupId) => api.get(`/groups/${groupId}/expenses`),
  getById: (id) => api.get(`/expenses/${id}`),
  create: (groupId, data) => api.post(`/groups/${groupId}/expenses`, data),
};

// Balances
export const balanceAPI = {
  getByGroup: (groupId) => api.get(`/groups/${groupId}/balances`),
};

// Payments
export const paymentAPI = {
  getByGroup: (groupId) => api.get(`/groups/${groupId}/payments`),
  create: (groupId, data) => api.post(`/groups/${groupId}/payments`, data),
};

// CSV Import
export const importAPI = {
  upload: (groupId, file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/groups/${groupId}/import/upload`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  submitDecisions: (sessionId, decisions) =>
    api.post(`/import/${sessionId}/decisions`, decisions),
  confirm: (groupId, sessionId) =>
    api.post(`/groups/${groupId}/import/${sessionId}/confirm`),
  getReport: (sessionId) => api.get(`/import/${sessionId}/report`),
};

export default api;
