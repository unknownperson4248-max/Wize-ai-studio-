// Tools the assistant can ask for. "phone" tools run on the Android app; "server" tools run here.
// `confirm: true` means the app must ask the user before doing it.

export const TOOLS = [
  { name: 'open_app', where: 'phone', description: 'Open an installed app by its name, e.g. "WhatsApp", "YouTube", "Camera".',
    parameters: { type: 'object', properties: { app: { type: 'string', description: 'App name as shown on the phone' } }, required: ['app'] } },
  { name: 'open_url', where: 'phone', description: 'Open a web address in the browser.',
    parameters: { type: 'object', properties: { url: { type: 'string' } }, required: ['url'] } },
  { name: 'web_search', where: 'phone', description: 'Search the web for something.',
    parameters: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'] } },
  { name: 'call', where: 'phone', confirm: true, description: 'Prepare a phone call to a number or a saved contact name. The user presses call.',
    parameters: { type: 'object', properties: { to: { type: 'string', description: 'Phone number or contact name' } }, required: ['to'] } },
  { name: 'send_sms', where: 'phone', confirm: true, description: 'Write an SMS to a number or contact name. The user presses send.',
    parameters: { type: 'object', properties: { to: { type: 'string' }, message: { type: 'string' } }, required: ['to', 'message'] } },
  { name: 'set_alarm', where: 'phone', description: 'Set an alarm at a time of day (24-hour clock).',
    parameters: { type: 'object', properties: { hour: { type: 'integer' }, minute: { type: 'integer' }, label: { type: 'string' } }, required: ['hour', 'minute'] } },
  { name: 'set_timer', where: 'phone', description: 'Start a countdown timer.',
    parameters: { type: 'object', properties: { seconds: { type: 'integer' }, label: { type: 'string' } }, required: ['seconds'] } },
  { name: 'torch', where: 'phone', description: 'Turn the flashlight on or off.',
    parameters: { type: 'object', properties: { on: { type: 'boolean' } }, required: ['on'] } },
  { name: 'set_volume', where: 'phone', description: 'Set media volume as a percentage 0-100.',
    parameters: { type: 'object', properties: { percent: { type: 'integer' } }, required: ['percent'] } },
  { name: 'battery_status', where: 'phone', description: 'Read battery level and whether it is charging.',
    parameters: { type: 'object', properties: {} } },
  { name: 'open_settings', where: 'phone', description: 'Open a settings page: wifi, bluetooth, display, sound, battery, location, apps or main.',
    parameters: { type: 'object', properties: { page: { type: 'string' } }, required: ['page'] } },
  { name: 'navigate', where: 'phone', description: 'Start map directions to a place.',
    parameters: { type: 'object', properties: { destination: { type: 'string' } }, required: ['destination'] } },
  { name: 'whatsapp_message', where: 'phone', confirm: true, description: 'Send a WhatsApp message to a saved contact name or a phone number. The phone asks the user to confirm first.',
    parameters: { type: 'object', properties: { to: { type: 'string', description: 'Contact name or phone number' }, message: { type: 'string' } }, required: ['to', 'message'] } },
  { name: 'read_screen', where: 'phone', description: 'See what is on the phone screen now: the app, texts, [tap] items and [field] text boxes. Use it before tapping or typing, and after, to check.',
    parameters: { type: 'object', properties: {} } },
  { name: 'tap', where: 'phone', description: 'Tap a button, link or list item on the screen by its visible text or label, exactly as read_screen shows it.',
    parameters: { type: 'object', properties: { target: { type: 'string', description: 'Visible text or label to tap' } }, required: ['target'] } },
  { name: 'type_text', where: 'phone', description: 'Type text into a text box on the screen. field: the box hint or label (optional; default the focused or first box).',
    parameters: { type: 'object', properties: { text: { type: 'string' }, field: { type: 'string' } }, required: ['text'] } },
  { name: 'scroll', where: 'phone', description: 'Scroll the screen up or down.',
    parameters: { type: 'object', properties: { direction: { type: 'string', enum: ['up', 'down'] } }, required: ['direction'] } },
  { name: 'press', where: 'phone', description: 'Press a system button: back, home, recents, notifications, quick_settings, lock or screenshot.',
    parameters: { type: 'object', properties: { button: { type: 'string', enum: ['back', 'home', 'recents', 'notifications', 'quick_settings', 'lock', 'screenshot'] } }, required: ['button'] } },
  { name: 'remember', where: 'server', description: 'Save a lasting fact about the user or their preferences, when the user asks you to remember something.',
    parameters: { type: 'object', properties: { fact: { type: 'string' } }, required: ['fact'] } },
  { name: 'forget', where: 'server', description: 'Delete saved facts that contain the given words, when the user asks you to forget something.',
    parameters: { type: 'object', properties: { about: { type: 'string' } }, required: ['about'] } },
];

export const TOOL_BY_NAME = Object.fromEntries(TOOLS.map((t) => [t.name, t]));

// Phone tools that may run several times in a row on purpose (the screen changes in between).
export const REPEATABLE = new Set(['read_screen', 'scroll', 'press', 'tap']);

// What the model sees (no internal fields).
export const MODEL_TOOLS = TOOLS.map(({ name, description, parameters }) => ({ name, description, parameters }));
