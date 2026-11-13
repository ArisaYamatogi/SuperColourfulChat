const mineflayer = require('mineflayer');
const fs = require('fs');
const HOST = process.env.SCC_HOST || '127.0.0.1';
const PORT = parseInt(process.env.SCC_PORT || '25565', 10);
const VERSION = process.env.SCC_VERSION || '1.21.8';
const OUT = process.env.SCC_OUT || 'capture-cc.json';
const TOGGLE = process.env.SCC_TOGGLE || '/colorfulchat';

const capture = [];
const bot = mineflayer.createBot({ host: HOST, port: PORT, username: 'TestBot', version: VERSION, auth: 'offline', hideErrors: false });
function rec(kind, data) { capture.push({ t: Date.now(), kind, data }); }
bot.on('error', (e) => console.error('BOT ERROR', e.message));
bot.on('kicked', (r) => { console.error('KICKED', r); finish(1); });
bot.on('end', () => finish(0));
let done = false;
function finish(code) {
  if (done) return; done = true;
  fs.writeFileSync(OUT, JSON.stringify(capture, null, 2));
  console.log('=== RAW CHAT ENTRIES ===');
  for (const e of capture) {
    if (e.kind === 'player_chat' || e.kind === 'system_chat') {
      console.log('[' + e.kind + '] ' + JSON.stringify(e.data));
    }
  }
  console.log('entries:', capture.length, '->', OUT);
  setTimeout(() => process.exit(code), 200);
}
const sleep = (ms) => new Promise(r => setTimeout(r, ms));
bot.once('spawn', async () => {
  const c = bot._client;
  c.on('player_chat', (p) => rec('player_chat', p));
  c.on('system_chat', (p) => rec('system_chat', p));
  await sleep(2000);
  console.log('>>> ' + TOGGLE); bot.chat(TOGGLE);
  await sleep(1500);
  console.log('>>> hello gradient 12345'); bot.chat('hello gradient 12345');
  await sleep(2500);
  console.log('>>> second message abc'); bot.chat('second message abc');
  await sleep(2500);
  bot.quit('done'); finish(0);
});
setTimeout(() => { console.error('TIMEOUT'); finish(2); }, 120000);