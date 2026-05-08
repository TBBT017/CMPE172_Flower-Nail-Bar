// Flower Nail Bar – Main JS

// Auto-hide flash messages after 5 seconds
document.addEventListener('DOMContentLoaded', function () {
    const alerts = document.querySelectorAll('.alert');
    alerts.forEach(alert => {
        setTimeout(() => {
            alert.style.transition = 'opacity 0.5s';
            alert.style.opacity = '0';
            setTimeout(() => alert.remove(), 500);
        }, 5000);
    });

    // Mobile hamburger toggle
    const hamburger = document.querySelector('.hamburger');
    const navLinks = document.querySelector('.navbar-links');
    if (hamburger && navLinks) {
        hamburger.addEventListener('click', () => {
            navLinks.style.display = navLinks.style.display === 'flex' ? 'none' : 'flex';
            navLinks.style.flexDirection = 'column';
            navLinks.style.position = 'absolute';
            navLinks.style.top = '70px';
            navLinks.style.right = '1rem';
            navLinks.style.background = 'var(--bg-main)';
            navLinks.style.padding = '1rem';
            navLinks.style.borderRadius = '8px';
            navLinks.style.boxShadow = '0 4px 20px rgba(0,0,0,0.15)';
        });
    }

    // Navbar user dropdown toggle
    const userMenu = document.getElementById('userMenu');
    const userChip = document.getElementById('userChipBtn');
    if (userMenu && userChip) {
        userChip.addEventListener('click', function (e) {
            e.stopPropagation();
            const isOpen = userMenu.classList.toggle('open');
            userChip.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        });
        // Click outside to close
        document.addEventListener('click', function (e) {
            if (!userMenu.contains(e.target)) {
                userMenu.classList.remove('open');
                userChip.setAttribute('aria-expanded', 'false');
            }
        });
        // Escape to close
        document.addEventListener('keydown', function (e) {
            if (e.key === 'Escape') {
                userMenu.classList.remove('open');
                userChip.setAttribute('aria-expanded', 'false');
            }
        });
    }
});
