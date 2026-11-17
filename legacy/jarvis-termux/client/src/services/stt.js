// Web Speech API — Speech-to-Text (Bangla/English/multilingual)

class STTService {
  constructor() {
    this.recognition = null;
    this.isListening = false;
    this.onResult = null;
    this.onError = null;
    this.onEnd = null;
    this.lang = "en-US";
  }

  init(lang = "en-US") {
    if (!("webkitSpeechRecognition" in window) && !("SpeechRecognition" in window)) {
      console.warn("SpeechRecognition not supported in this browser");
      return false;
    }
    const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    this.recognition = new SR();
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.lang = lang;

    this.recognition.onresult = (event) => {
      let final = "";
      let interim = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const t = event.results[i][0].transcript;
        if (event.results[i].isFinal) final += t;
        else interim += t;
      }
      if (this.onResult) this.onResult(final, interim);
    };

    this.recognition.onerror = (e) => {
      if (this.onError) this.onError(e);
    };

    this.recognition.onend = () => {
      if (this.isListening && !this._paused) {
        // Auto-restart for continuous listening
        try { this.recognition.start(); } catch {}
      }
      if (this.onEnd) this.onEnd();
    };

    return true;
  }

  start(lang = "en-US") {
    if (!this.recognition || this.recognition.lang !== lang) {
      this.init(lang);
    }
    if (!this.recognition) return false;
    this.isListening = true;
    try { this.recognition.start(); } catch {}
    return true;
  }

  stop() {
    this.isListening = false;
    if (this.recognition) {
      try { this.recognition.stop(); } catch {}
    }
  }

  setLang(lang) {
    this.lang = lang;
    if (this.recognition) this.recognition.lang = lang;
  }
}

export const stt = new STTService();