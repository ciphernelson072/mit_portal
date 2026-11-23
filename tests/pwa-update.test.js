const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const test = require('node:test');

const updateScript = fs.readFileSync(
  path.join(__dirname, '../src/main/resources/static/js/pwa-update.js'),
  'utf8',
);

function createUpdateContext({ installed, iosStandalone = false, waitingWorker }) {
  const serviceWorkerListeners = {};
  const registrationListeners = {};
  const documentListeners = {};
  const elements = [];
  let reloaded = false;
  let updateChecks = 0;
  const document = {
    readyState: 'complete',
    visibilityState: 'visible',
    addEventListener(name, handler) {
      documentListeners[name] = handler;
    },
    body: {
      appendChild(element) {
        elements.push(element);
      },
    },
    createElement(tagName) {
      const listeners = {};
      return {
        tagName,
        style: {},
        children: [],
        attributes: {},
        addEventListener(name, handler) {
          listeners[name] = handler;
        },
        append(...children) {
          this.children.push(...children);
        },
        setAttribute(name, value) {
          this.attributes[name] = value;
        },
        remove() {
          this.removed = true;
        },
        click() {
          if (listeners.click) listeners.click();
        },
      };
    },
  };
  const registration = {
    waiting: waitingWorker,
    addEventListener(name, handler) {
      registrationListeners[name] = handler;
    },
    async update() {
      updateChecks += 1;
    },
  };
  const navigator = {
    standalone: iosStandalone,
    serviceWorker: {
      controller: {},
      async register(pathname, options) {
        assert.equal(pathname, '/service-worker.js');
        assert.equal(options.updateViaCache, 'none');
        return registration;
      },
      addEventListener(name, handler) {
        serviceWorkerListeners[name] = handler;
      },
    },
  };
  const window = {
    navigator,
    matchMedia: () => ({ matches: installed }),
    location: {
      reload() {
        reloaded = true;
      },
    },
  };

  vm.runInNewContext(updateScript, { window, document, navigator, console });

  return {
    document,
    elements,
    registrationListeners,
    serviceWorkerListeners,
    documentListeners,
    get reloaded() {
      return reloaded;
    },
    get updateChecks() {
      return updateChecks;
    },
  };
}

test('installed app offers an update and reloads after the waiting worker takes control', async () => {
  const postedMessages = [];
  const context = createUpdateContext({
    installed: true,
    waitingWorker: { postMessage: message => postedMessages.push(message) },
  });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(context.updateChecks, 1);
  assert.equal(context.elements.length, 1);
  const actions = context.elements[0].children[1];
  const updateButton = actions.children[0];
  assert.equal(updateButton.textContent, 'Update');

  updateButton.click();
  assert.equal(postedMessages.length, 1);
  assert.equal(postedMessages[0].type, 'SKIP_WAITING');
  context.serviceWorkerListeners.controllerchange();
  assert.equal(context.reloaded, true);

  context.documentListeners.visibilitychange();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(context.updateChecks, 2);
});

test('does not show an update prompt in a regular browser tab', async () => {
  const context = createUpdateContext({
    installed: false,
    waitingWorker: { postMessage() {} },
  });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(context.updateChecks, 1);
  assert.equal(context.elements.length, 0);
});

test('iOS home-screen apps receive the update prompt', async () => {
  const context = createUpdateContext({
    installed: false,
    iosStandalone: true,
    waitingWorker: { postMessage() {} },
  });
  await new Promise(resolve => setImmediate(resolve));

  assert.equal(context.elements.length, 1);
});
