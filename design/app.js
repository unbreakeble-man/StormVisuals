const modules = [
  {id:'effects',name:'Эффекты',key:'P',description:'Показывает выбранный эффект',enabled:true,category:'Render'},
  {id:'targetesp',name:'TargetESP',key:'R',description:'Выделение выбранной цели',enabled:true,category:'Render'},
  {id:'norender',name:'NoRender',description:'Скрывает мешающие эффекты',enabled:false,category:'Render'},
  {id:'jumpcircles',name:'Jumpcircles',description:'Круги при прыжке',enabled:true,category:'Render'},
  {id:'fullbright',name:'FullBright',description:'Светлее в тёмных местах',enabled:false,category:'Render'},
  {id:'crosshair',name:'CustomCrosshair',description:'Свой стиль прицела',enabled:false,category:'Render'},
  {id:'waypoints',name:'Точки маршрута',key:'M',description:'Ориентиры и расстояние',enabled:false,category:'World'}
];
const columns = document.querySelectorAll('.column');
const search = document.querySelector('.search input');
const gui = document.querySelector('.gui');
const reopen = document.querySelector('.reopen');
let category = 'Render';

function switchButton(label, checked) {
  const button = document.createElement('button');
  button.className = 'switch';
  button.setAttribute('role', 'switch');
  button.setAttribute('aria-label', label);
  button.setAttribute('aria-checked', String(checked));
  button.addEventListener('click', () => button.setAttribute('aria-checked', String(button.getAttribute('aria-checked') !== 'true')));
  return button;
}

for (const module of modules) {
  const card = document.createElement('article');
  card.className = 'card';
  card.dataset.id = module.id;
  card.setAttribute('aria-label', module.name);
  const summary = document.createElement('div');
  summary.className = 'card-summary';
  summary.tabIndex = 0;
  summary.setAttribute('role', 'button');
  summary.setAttribute('aria-label', `Настройки: ${module.name}`);
  summary.setAttribute('aria-expanded', 'false');
  const heading = document.createElement('div');
  heading.className = 'card-heading';
  const title = document.createElement('span');
  title.className = 'module-title';
  title.textContent = module.name;
  heading.append(title);
  if (module.key) {
    const key = document.createElement('span');
    key.className = 'keybind';
    key.textContent = `[${module.key}]`;
    heading.append(key);
  }
  const toggle = switchButton(module.name, module.enabled);
  heading.append(toggle);
  const description = document.createElement('p');
  description.className = 'description';
  description.textContent = module.description;
  summary.append(heading, description);
  const settings = document.createElement('div');
  settings.className = 'settings';
  settings.id = `settings-${module.id}`;
  settings.hidden = true;
  summary.setAttribute('aria-controls', settings.id);
  if (module.id === 'effects') {
    const effectRow = document.createElement('label');
    effectRow.className = 'setting';
    effectRow.innerHTML = '<span>Эффект</span><select aria-label="Эффект"><option>Огнестойкость</option><option>Скорость</option><option>Сила</option></select>';
    settings.append(effectRow);
    for (const label of ['Показывать таймер', 'Скрывать без эффекта']) {
      const row = document.createElement('div');
      row.className = 'setting';
      const text = document.createElement('span');
      text.textContent = label;
      row.append(text, switchButton(label, true));
      settings.append(row);
    }
  }
  const slider = document.createElement('label');
  slider.className = 'slider-setting';
  slider.innerHTML = `<span class="slider-label"><span>Масштаб</span><output for="scale-${module.id}">100%</output></span><input id="scale-${module.id}" aria-label="Масштаб: ${module.name}" type="range" min="50" max="200" value="100">`;
  const range = slider.querySelector('input');
  range.addEventListener('input', () => {
    slider.querySelector('output').value = `${range.value}%`;
    range.style.setProperty('--fill', `${(range.value - 50) / 150 * 100}%`);
  });
  settings.append(slider);
  function expand() {
    settings.hidden = !settings.hidden;
    card.classList.toggle('expanded', !settings.hidden);
    summary.setAttribute('aria-expanded', String(!settings.hidden));
  }
  summary.addEventListener('click', event => {
    if (!event.target.closest('button')) toggle.click();
  });
  summary.addEventListener('keydown', event => {
    if (event.target !== summary) return;
    if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); toggle.click(); }
    if (event.key === 'ContextMenu' || (event.shiftKey && event.key === 'F10')) { event.preventDefault(); expand(); }
  });
  summary.addEventListener('contextmenu', event => { event.preventDefault(); expand(); });
  card.append(summary, settings);
  module.element = card;
}

function render() {
  columns.forEach(column => column.replaceChildren());
  const query = search.value.trim().toLocaleLowerCase('ru');
  const visible = modules.filter(module => (module.category === category || (category === 'Waypoints' && module.id === 'waypoints')) && `${module.name} ${module.description}`.toLocaleLowerCase('ru').includes(query));
  visible.forEach((module, index) => columns[index % 2].append(module.element));
  document.querySelector('.empty').hidden = visible.length > 0;
}
search.addEventListener('input', render);
document.querySelectorAll('.nav-item').forEach(button => button.addEventListener('click', () => {
  category = button.dataset.category;
  document.querySelectorAll('.nav-item').forEach(item => {
    item.classList.toggle('active', item === button);
    if (item === button) item.setAttribute('aria-current', 'page'); else item.removeAttribute('aria-current');
  });
  render();
}));
function setOpen(open) { gui.hidden = !open; reopen.hidden = open; if (open) search.focus(); else reopen.focus(); }
document.querySelector('.close').addEventListener('click', () => setOpen(false));
reopen.addEventListener('click', () => setOpen(true));
document.addEventListener('keydown', event => { if (event.key === 'Escape') setOpen(gui.hidden); });
render();
