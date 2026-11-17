// Web Speech API — Text-to-Speech

class TTSService {
  constructor() {
    this.synth = window.speechSynthesis || null;
    this.voices = [];
    this.enabled = true;
    this.lang = "en-US";
    if (this.synth) {
      this.loadVoices();
      this.synth.onvoiceschanged = () => this.loadVoices();
    }
  }

  loadVoices() {
    if (!this.synth) return;
    this.voices = this.synth.getVoices();
  }

  speak(text, lang) {
    if (!this.enabled || !this.synth) return;
    const utter = new SpeechSynthesisUtterance(text);
    utter.lang = lang || this.lang;
    // Prefer a matching-language voice
    const match = this.voices.find((v) => v.lang === utter.lang) || this.voices.find((v) => v.lang.startsWith(utter.lang.split("-")[0]));
    if (match) utter.voice = match;
    utter.rate = 1;
    utter.pitch = 1;
    this.synth.speak(utter);
  }

  stop() {
    if (this.synth) this.synth.cancel();
  }

  setEnabled(v) { this.enabled = v; }
  setLang(lang) { this.lang = lang; }
}

export const tts = new TTSService();