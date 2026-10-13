function requireAuth() {
  if (!localStorage.getItem('token')) {
    window.location.href = 'login.html';
    return false;
  }
  return true;
}

function getUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null');
  } catch (e) {
    return null;
  }
}

function initNav() {
  if (!requireAuth()) return;
  const user = getUser();
  if (user) {
    const el = document.getElementById('nav-user-name');
    if (el) el.textContent = user.name || user.email;
  }
}

function logout() {
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  window.location.href = 'login.html';
}

document.addEventListener('DOMContentLoaded', initNav);
