/**
 * NovaTrans - 高级交互效果
 * 包含滚动动画、粒子效果、深色模式等功能
 */

(function() {
  'use strict';

  // ==============================
  // 滚动动画观察器
  // ==============================
  function initScrollAnimations() {
    const observerOptions = {
      root: null,
      rootMargin: '-50px',
      threshold: 0.1
    };

    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('visible');
          // 可选：动画只触发一次
          // observer.unobserve(entry.target);
        }
      });
    }, observerOptions);

    // 观察所有需要动画的元素
    document.querySelectorAll('.fade-in-section, .slide-in-left, .slide-in-right, .zoom-fade-in, .blur-fade-in').forEach(el => {
      observer.observe(el);
    });
  }

  // ==============================
  // 粒子背景效果
  // ==============================
  function initParticles() {
    const container = document.querySelector('.particles-container');
    if (!container) return;

    // 创建额外的小粒子
    for (let i = 0; i < 20; i++) {
      const particle = document.createElement('div');
      particle.className = 'star-particle';
      particle.style.left = Math.random() * 100 + '%';
      particle.style.top = Math.random() * 100 + '%';
      particle.style.animationDelay = Math.random() * 3 + 's';
      particle.style.width = (Math.random() * 4 + 2) + 'px';
      particle.style.height = particle.style.width;
      container.appendChild(particle);
    }
  }

  // ==============================
  // 按钮波纹效果
  // ==============================
  function initRippleEffect() {
    document.addEventListener('click', function(e) {
      const btn = e.target.closest('.ripple-btn, .btn-primary, .btn-secondary, .btn-outline');
      if (!btn) return;

      const rect = btn.getBoundingClientRect();
      const ripple = document.createElement('span');
      ripple.className = 'ripple';

      const size = Math.max(rect.width, rect.height);
      ripple.style.width = ripple.style.height = size + 'px';
      ripple.style.left = (e.clientX - rect.left - size / 2) + 'px';
      ripple.style.top = (e.clientY - rect.top - size / 2) + 'px';

      btn.appendChild(ripple);

      setTimeout(() => ripple.remove(), 600);
    });
  }

  // ==============================
  // 滚动进度条
  // ==============================
  function initScrollProgress() {
    const progress = document.createElement('div');
    progress.className = 'scroll-progress';
    document.body.appendChild(progress);

    function updateProgress() {
      const scrollTop = window.scrollY || document.documentElement.scrollTop;
      const height = document.documentElement.scrollHeight - document.documentElement.clientHeight;
      const percentage = (scrollTop / height) * 100;
      progress.style.width = percentage + '%';
    }

    window.addEventListener('scroll', updateProgress, { passive: true });
    updateProgress();
  }

  // ==============================
  // 深色模式切换
  // ==============================
  function initThemeToggle() {
    // 创建切换按钮
    const toggle = document.createElement('button');
    toggle.className = 'theme-toggle ripple-btn';
    toggle.setAttribute('aria-label', '切换深色模式');
    toggle.innerHTML = `
      <svg class="moon-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
      </svg>
      <svg class="sun-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <circle cx="12" cy="12" r="5"/>
        <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
      </svg>
    `;
    document.body.appendChild(toggle);

    // 从 localStorage 读取主题
    const savedTheme = localStorage.getItem('theme') || 'light';
    if (savedTheme === 'dark') {
      document.documentElement.setAttribute('data-theme', 'dark');
    }

    toggle.addEventListener('click', () => {
      const isDark = document.documentElement.getAttribute('data-theme') === 'dark';
      const newTheme = isDark ? 'light' : 'dark';

      document.documentElement.setAttribute('data-theme', newTheme);
      localStorage.setItem('theme', newTheme);

      // 添加切换动画
      document.body.style.transition = 'background-color 0.3s, color 0.3s';

      // 通知其他标签页
      window.dispatchEvent(new StorageEvent('storage', {
        key: 'theme',
        newValue: newTheme
      }));
    });

    // 监听其他标签页的变化
    window.addEventListener('storage', (e) => {
      if (e.key === 'theme') {
        document.documentElement.setAttribute('data-theme', e.newValue);
      }
    });

    // 监听系统主题变化
    window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', (e) => {
      if (!localStorage.getItem('theme')) {
        document.documentElement.setAttribute('data-theme', e.matches ? 'dark' : 'light');
      }
    });
  }

  // ==============================
  // 平滑滚动
  // ==============================
  function initSmoothScroll() {
    document.querySelectorAll('a[href^="#"]').forEach(anchor => {
      anchor.addEventListener('click', function(e) {
        const href = this.getAttribute('href');
        if (href === '#') return;

        e.preventDefault();
        const target = document.querySelector(href);
        if (target) {
          target.scrollIntoView({
            behavior: 'smooth',
            block: 'start'
          });
        }
      });
    });
  }

  // ==============================
  // 数字计数动画
  // ==============================
  function initCountUp() {
    const observerOptions = {
      root: null,
      rootMargin: '0px',
      threshold: 0.5
    };

    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          const target = entry.target;
          const end = parseInt(target.getAttribute('data-end'), 10);
          const duration = 2000;
          const step = end / (duration / 16);
          let current = 0;

          const timer = setInterval(() => {
            current += step;
            if (current >= end) {
              target.textContent = end.toLocaleString();
              clearInterval(timer);
            } else {
              target.textContent = Math.floor(current).toLocaleString();
            }
          }, 16);

          observer.unobserve(target);
        }
      });
    }, observerOptions);

    document.querySelectorAll('[data-animate-number]').forEach(el => {
      observer.observe(el);
    });
  }

  // ==============================
  // 视差滚动效果
  // ==============================
  function initParallax() {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;

    const parallaxElements = document.querySelectorAll('[data-parallax]');
    if (parallaxElements.length === 0) return;

    function updateParallax() {
      const scrollTop = window.scrollY;

      parallaxElements.forEach(el => {
        const speed = parseFloat(el.getAttribute('data-parallax')) || 0.5;
        const yPos = scrollTop * speed;
        el.style.transform = 'translateY(' + yPos + 'px)';
      });
    }

    window.addEventListener('scroll', updateParallax, { passive: true });
  }

  // ==============================
  // 文字揭示动画
  // ==============================
  function initTextReveal() {
    document.querySelectorAll('[data-text-reveal]').forEach(el => {
      const text = el.textContent;
      el.textContent = '';

      [...text].forEach((char, i) => {
        const span = document.createElement('span');
        span.textContent = char === ' ' ? '\u00A0' : char;
        span.style.display = 'inline-block';
        span.style.opacity = '0';
        span.style.transform = 'translateY(20px)';
        span.style.transition = `opacity 0.3s, transform 0.3s`;
        span.style.transitionDelay = (i * 0.03) + 's';
        el.appendChild(span);
      });

      const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
          if (entry.isIntersecting) {
            setTimeout(() => {
              entry.target.querySelectorAll('span').forEach(span => {
                span.style.opacity = '1';
                span.style.transform = 'translateY(0)';
              });
            }, 200);
            observer.unobserve(entry.target);
          }
        });
      }, { threshold: 0.5 });

      observer.observe(el);
    });
  }

  // ==============================
  // 磁吸按钮效果
  // ==============================
  function initMagneticButtons() {
    if (window.matchMedia('(max-width: 768px)').matches) return;

    document.querySelectorAll('.btn-magnetic').forEach(btn => {
      btn.addEventListener('mousemove', (e) => {
        const rect = btn.getBoundingClientRect();
        const x = e.clientX - rect.left - rect.width / 2;
        const y = e.clientY - rect.top - rect.height / 2;

        btn.style.transform = `translate(${x * 0.3}px, ${y * 0.3}px)`;
      });

      btn.addEventListener('mouseleave', () => {
        btn.style.transform = 'translate(0, 0)';
      });
    });
  }

  // ==============================
  // 初始化所有效果
  // ==============================
  function init() {
    if (document.readyState === 'loading') {
      document.addEventListener('DOMContentLoaded', runInit);
    } else {
      runInit();
    }

    function runInit() {
      initScrollAnimations();
      initParticles();
      initRippleEffect();
      initScrollProgress();
      initThemeToggle();
      initSmoothScroll();
      initCountUp();
      initParallax();
      initTextReveal();
      initMagneticButtons();

      // 添加页面加载动画
      document.body.classList.add('page-transition');
      setTimeout(() => {
        document.body.classList.remove('page-transition');
      }, 500);
    }
  }

  // 启动
  init();

  // 暴露 API 供外部使用
  window.NovaTransEffects = {
    refresh: () => {
      initScrollAnimations();
    }
  };

})();
