const menuButton = document.querySelector('.menu-toggle');
const siteNavigation = document.querySelector('#site-navigation');
const siteHeader = document.querySelector('.site-header');
const installAppButton = document.querySelector('#installAppButton');
const installAppStatus = document.querySelector('#installAppStatus');
let pendingInstallPrompt = null;
const socialPlatforms = {
  facebook: ['https://www.facebook.com/', 'Facebook'],
  instagram: ['https://www.instagram.com/', 'Instagram'],
  linkedin: ['https://www.linkedin.com/', 'LinkedIn'],
  youtube: ['https://www.youtube.com/', 'YouTube'],
  whatsapp: ['https://wa.me/256769234213', 'WhatsApp'],
  x: ['https://x.com/', 'X'],
  tiktok: ['https://www.tiktok.com/', 'TikTok'],
};

document.querySelectorAll('.social-list li').forEach(item => {
  const platformName = item.getAttribute('aria-label')?.split(' ')[0].toLowerCase();
  const platform = socialPlatforms[platformName];
  const icon = item.firstElementChild;
  if (!platform || !icon) return;

  item.removeAttribute('aria-label');
  item.closest('.social-list').setAttribute('aria-label', 'Social media platform links');
  const link = document.createElement('a');
  link.className = 'social-link';
  link.href = platform[0];
  link.target = '_blank';
  link.rel = 'noopener noreferrer';
  link.setAttribute('aria-label', `Open ${platform[1]} (MIT profile link to be updated)`);
  link.title = `${platform[1]} — update to the official MIT profile when available`;
  link.append(icon);
  item.replaceChildren(link);
});

document.querySelectorAll('.footer-social').forEach(section => {
  const description = section.querySelector('h2 + p');
  if (description) {
    description.textContent = 'These links open the platforms directly. Replace them with the official MIT profile URLs when available.';
  }
  const note = section.querySelector('.social-note');
  if (note) {
    note.replaceChildren(
      document.createTextNode('Email '),
      Object.assign(document.createElement('a'), {
        href: 'mailto:ciphernelson072@gmail.com',
        textContent: 'ciphernelson072@gmail.com',
      }),
      document.createTextNode(' or WhatsApp '),
      Object.assign(document.createElement('a'), {
        href: 'https://wa.me/256769234213',
        target: '_blank',
        rel: 'noopener noreferrer',
        textContent: '+256 769 234 213',
      }),
      document.createTextNode('.'),
    );
  }
});

function updateHeaderBackground() {
  siteHeader?.classList.toggle('is-scrolled', window.scrollY > 32);
}

updateHeaderBackground();
window.addEventListener('scroll', updateHeaderBackground, { passive: true });

if (menuButton && siteNavigation) {
  menuButton.addEventListener('click', () => {
    const expanded = menuButton.getAttribute('aria-expanded') === 'true';
    menuButton.setAttribute('aria-expanded', String(!expanded));
    menuButton.setAttribute('aria-label', expanded ? 'Open navigation' : 'Close navigation');
    siteNavigation.classList.toggle('is-open', !expanded);
  });

  siteNavigation.addEventListener('click', event => {
    if (event.target instanceof HTMLAnchorElement && window.matchMedia('(max-width: 1024px)').matches) {
      menuButton.setAttribute('aria-expanded', 'false');
      menuButton.setAttribute('aria-label', 'Open navigation');
      siteNavigation.classList.remove('is-open');
    }
  });
}

if (installAppButton && installAppStatus) {
  const isStandalone = window.matchMedia('(display-mode: standalone)').matches
    || window.navigator.standalone === true;

  if (isStandalone) {
    installAppButton.hidden = true;
    installAppStatus.textContent = 'The portal is already installed on this device.';
  }

  window.addEventListener('beforeinstallprompt', event => {
    event.preventDefault();
    pendingInstallPrompt = event;
  });

  window.addEventListener('appinstalled', () => {
    pendingInstallPrompt = null;
    installAppButton.hidden = true;
    installAppStatus.textContent = 'The portal has been installed.';
  });

  installAppButton.addEventListener('click', async () => {
    if (isStandalone) return;
    if (!pendingInstallPrompt) {
      installAppStatus.textContent = /iphone|ipad|ipod/i.test(window.navigator.userAgent)
        ? 'To install on iPhone or iPad, open the Share menu and choose Add to Home Screen.'
        : 'To install, open your browser menu and choose Install app or Add to Home screen.';
      return;
    }

    pendingInstallPrompt.prompt();
    const choice = await pendingInstallPrompt.userChoice;
    pendingInstallPrompt = null;
    if (choice.outcome === 'accepted') installAppButton.hidden = true;
    installAppStatus.textContent = choice.outcome === 'accepted'
      ? 'The portal has been installed.'
      : 'Installation was cancelled. You can install it later from your browser menu.';
  });
}

const photoCarousel = document.querySelector('[data-photo-carousel]');
if (photoCarousel) {
  const slides = [
    {
      src: '/public/images/campus-community.jpg',
      alt: 'A group of people, some wearing graduation gowns, outside a glass-fronted building.',
      caption: 'A group outside a glass-fronted building',
    },
    {
      src: '/public/images/campus-gathering.jpg',
      alt: 'A group gathered indoors beside national and institutional flags.',
      caption: 'A gathering at the institute',
    },
    {
      src: '/public/images/campus-activity.jpg',
      alt: 'A group of people gathered in a hall while one person speaks at the front.',
      caption: 'A group gathered for an institute activity',
    },
    {
      src: '/public/images/academic-community.jpg',
      alt: 'A line of people wearing colorful academic gowns and caps indoors.',
      caption: 'Academic community',
    },
  ];
  const firstImage = photoCarousel.querySelector('[data-carousel-image]');
  const secondImage = photoCarousel.querySelector('[data-carousel-image-secondary]');
  const caption = photoCarousel.querySelector('[data-carousel-caption]');
  const status = photoCarousel.querySelector('[data-carousel-status]');
  const previousButton = photoCarousel.querySelector('[data-carousel-previous]');
  const nextButton = photoCarousel.querySelector('[data-carousel-next]');
  const toggleButton = photoCarousel.querySelector('[data-carousel-toggle]');
  const motionPreference = window.matchMedia('(prefers-reduced-motion: reduce)');
  const mobileViewport = window.matchMedia('(max-width: 700px)');
  let activeImage = firstImage;
  let currentSlide = 0;
  let isPlaying = !motionPreference.matches && !mobileViewport.matches;
  let timer;
  let renderVersion = 0;
  let pointerInput = false;

  function updateRotationControl() {
    toggleButton.hidden = motionPreference.matches || mobileViewport.matches;
    toggleButton.textContent = isPlaying ? 'Pause' : 'Play';
    toggleButton.setAttribute('aria-label', isPlaying ? 'Pause photo rotation' : 'Start photo rotation');
    status.setAttribute('aria-live', isPlaying ? 'off' : 'polite');
  }

  function setPlaying(playing) {
    isPlaying = playing;
    window.clearInterval(timer);
    if (isPlaying) {
      timer = window.setInterval(() => showSlide((currentSlide + 1) % slides.length), 7000);
    }
    updateRotationControl();
  }

  async function showSlide(index) {
    if (index === currentSlide) return;
    const version = ++renderVersion;
    const incomingImage = activeImage === firstImage ? secondImage : firstImage;
    const slide = slides[index];
    incomingImage.classList.remove('is-active');
    incomingImage.alt = '';
    incomingImage.setAttribute('aria-hidden', 'true');
    incomingImage.src = slide.src;

    try {
      await incomingImage.decode();
    } catch (error) {
      if (!incomingImage.complete || incomingImage.naturalWidth === 0) {
        console.error(`Unable to load public photo: ${slide.src}`, error);
        return;
      }
    }
    if (version !== renderVersion) return;

    activeImage.classList.remove('is-active');
    activeImage.alt = '';
    activeImage.setAttribute('aria-hidden', 'true');
    incomingImage.alt = slide.alt;
    incomingImage.removeAttribute('aria-hidden');
    incomingImage.classList.add('is-active');
    activeImage = incomingImage;
    currentSlide = index;
    caption.textContent = slide.caption;
    status.textContent = `Photo ${index + 1} of ${slides.length}: ${slide.caption}`;
  }

  previousButton.addEventListener('click', () => {
    setPlaying(false);
    showSlide((currentSlide + slides.length - 1) % slides.length);
  });
  nextButton.addEventListener('click', () => {
    setPlaying(false);
    showSlide((currentSlide + 1) % slides.length);
  });
  toggleButton.addEventListener('click', () => setPlaying(!isPlaying));
  photoCarousel.addEventListener('focusin', event => {
    if (!pointerInput && !photoCarousel.contains(event.relatedTarget)) setPlaying(false);
  });
  photoCarousel.addEventListener('pointerdown', () => {
    pointerInput = true;
    window.setTimeout(() => { pointerInput = false; }, 0);
  });
  photoCarousel.addEventListener('keydown', event => {
    if (event.key === 'ArrowLeft') {
      event.preventDefault();
      setPlaying(false);
      showSlide((currentSlide + slides.length - 1) % slides.length);
    } else if (event.key === 'ArrowRight') {
      event.preventDefault();
      setPlaying(false);
      showSlide((currentSlide + 1) % slides.length);
    }
  });
  motionPreference.addEventListener('change', event => {
    if (event.matches) setPlaying(false);
    updateRotationControl();
  });
  mobileViewport.addEventListener('change', event => {
    if (event.matches) setPlaying(false);
    updateRotationControl();
  });

  updateRotationControl();
  if (isPlaying) setPlaying(true);
}

if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/service-worker.js').catch(error => {
      console.error('Unable to enable offline portal support.', error);
    });
  });
}

document.querySelectorAll('[data-current-year-number]').forEach(element => {
  element.textContent = String(new Date().getFullYear());
});

async function loadPublicPrograms() {
  const list = document.querySelector('[data-program-list]');
  if (!list) return;

  const status = document.querySelector('[data-program-status]');
  try {
    const response = await fetch('/api/public/admissions/programs', {
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) throw new Error(`Programme information is unavailable (${response.status}).`);
    const programs = await response.json();
    if (!Array.isArray(programs)) throw new Error('Programme information could not be read.');

    list.replaceChildren();
    if (!programs.length) {
      status.textContent = 'There are no active programmes published for public applications at this time. Please contact admissions for current information.';
      return;
    }

    programs.forEach(program => {
      const card = document.createElement('article');
      card.className = 'program-card';

      const award = document.createElement('p');
      award.className = 'program-award';
      award.textContent = program.awardTitle || 'Programme';

      const name = document.createElement('h3');
      name.textContent = program.name || 'Unnamed programme';

      const code = document.createElement('p');
      code.className = 'program-code';
      const facts = [];
      if (program.code) facts.push(`Programme code: ${program.code}`);
      if (Number.isInteger(program.durationYears) && program.durationYears > 0) {
        facts.push(`Duration: ${program.durationYears} ${program.durationYears === 1 ? 'year' : 'years'}`);
      }
      code.textContent = facts.join(' · ');

      card.append(award, name, code);
      list.append(card);
    });
    status.textContent = '';
  } catch (error) {
    status.classList.add('notice-error');
    status.textContent = `${error instanceof TypeError ? 'Programme information is temporarily unavailable.' : error.message} Try again later or call admissions support.`;
  }
}

async function loadAcademicYear() {
  const target = document.querySelector('[data-current-year]');
  if (!target) return;
  try {
    const response = await fetch('/api/public/admissions/academic-year', {
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) throw new Error(`Admissions information is unavailable (${response.status}).`);
    const year = await response.json();
    target.textContent = year.name
      ? `The current application year is ${year.name}.`
      : 'The institute has not published an active application year. Contact admissions for current information.';
  } catch (error) {
    target.textContent = `${error instanceof TypeError ? 'Current admissions information is temporarily unavailable.' : error.message} Please contact admissions support for current information.`;
  }
}

loadPublicPrograms();
loadAcademicYear();
