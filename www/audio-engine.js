(function (root) {
  'use strict';

  class SahraAudioEngine {
    constructor() {
      this.ctx = null;
      this.isPlaying = false;
      this.currentPlaylist = null;
      this.trackIndex = 0;
      this.layers = { music: 0.72, rain: 0.25, fire: 0.2, pulse: 0.0 };
      this.masterVolume = 0.85;

      this.masterGain = null;
      this.musicGain = null;
      this.rainGain = null;
      this.fireGain = null;
      this.pulseGain = null;

      this.rainSource = null;
      this.fireSource = null;
      this.noteTimer = null;
      this.crackleTimer = null;
      this.pulseTimer = null;
      this.stepCounter = 0;
      this.onChange = null;
    }

    ensureContext() {
      if (!this.ctx) {
        const AudioCtx = window.AudioContext || window.webkitAudioContext;
        if (!AudioCtx) return null;
        this.ctx = new AudioCtx();

        this.masterGain = this.ctx.createGain();
        this.masterGain.gain.value = this.masterVolume;
        this.masterGain.connect(this.ctx.destination);

        // Soft stereo echo / lounge reverb bus for musical notes
        this.musicGain = this.ctx.createGain();
        this.musicGain.gain.value = this.layers.music;

        this.delayNode = this.ctx.createDelay(1.0);
        this.delayNode.delayTime.value = 0.36;
        this.delayFeedback = this.ctx.createGain();
        this.delayFeedback.gain.value = 0.32;
        this.delayFilter = this.ctx.createBiquadFilter();
        this.delayFilter.type = 'lowpass';
        this.delayFilter.frequency.value = 1100;

        this.musicGain.connect(this.masterGain);
        this.musicGain.connect(this.delayNode);
        this.delayNode.connect(this.delayFilter);
        this.delayFilter.connect(this.delayFeedback);
        this.delayFeedback.connect(this.delayNode);
        this.delayFilter.connect(this.masterGain);

        this.rainGain = this.ctx.createGain();
        this.rainGain.gain.value = 0;
        this.rainGain.connect(this.masterGain);

        this.fireGain = this.ctx.createGain();
        this.fireGain.gain.value = 0;
        this.fireGain.connect(this.masterGain);

        this.pulseGain = this.ctx.createGain();
        this.pulseGain.gain.value = 0;
        this.pulseGain.connect(this.masterGain);
      }
      if (this.ctx.state === 'suspended') {
        this.ctx.resume().catch(() => {});
      }
      return this.ctx;
    }

    createBrownNoiseBuffer(seconds) {
      const ctx = this.ctx;
      const bufferSize = Math.floor(ctx.sampleRate * seconds);
      const buffer = ctx.createBuffer(1, bufferSize, ctx.sampleRate);
      const output = buffer.getChannelData(0);
      let lastOut = 0.0;
      for (let i = 0; i < bufferSize; i++) {
        const white = Math.random() * 2 - 1;
        output[i] = (lastOut + 0.02 * white) / 1.02;
        lastOut = output[i];
        output[i] *= 2.4;
      }
      return buffer;
    }

    startAmbientBeds() {
      const ctx = this.ensureContext();
      if (!ctx) return;
      this.stopAmbientBeds();

      const noiseBuf = this.createBrownNoiseBuffer(4);

      // Soft night rain bed
      const rainSrc = ctx.createBufferSource();
      rainSrc.buffer = noiseBuf;
      rainSrc.loop = true;
      const rainFilter = ctx.createBiquadFilter();
      rainFilter.type = 'bandpass';
      rainFilter.frequency.value = 780;
      rainFilter.Q.value = 0.55;
      rainSrc.connect(rainFilter);
      rainFilter.connect(this.rainGain);
      rainSrc.start();
      this.rainSource = rainSrc;

      // Warm fireplace hearth bed
      const fireSrc = ctx.createBufferSource();
      fireSrc.buffer = noiseBuf;
      fireSrc.loop = true;
      const fireFilter = ctx.createBiquadFilter();
      fireFilter.type = 'lowpass';
      fireFilter.frequency.value = 210;
      fireSrc.connect(fireFilter);
      fireFilter.connect(this.fireGain);
      fireSrc.start();
      this.fireSource = fireSrc;

      this.applyLayerVolumes();

      // Gentle wood crackles when fire > 0.05
      this.crackleTimer = setInterval(() => {
        if (!this.isPlaying || this.layers.fire <= 0.05 || !this.ctx) return;
        if (Math.random() < 0.55) {
          this.triggerCrackle();
        }
      }, 650);

      // Soft warm lounge heartbeat when pulse > 0.05
      this.pulseTimer = setInterval(() => {
        if (!this.isPlaying || this.layers.pulse <= 0.05 || !this.ctx) return;
        this.triggerHeartbeat(this.layers.pulse * 0.35);
      }, 1650);
    }

    stopAmbientBeds() {
      if (this.rainSource) {
        try { this.rainSource.stop(); } catch (_) {}
        this.rainSource = null;
      }
      if (this.fireSource) {
        try { this.fireSource.stop(); } catch (_) {}
        this.fireSource = null;
      }
      if (this.crackleTimer) {
        clearInterval(this.crackleTimer);
        this.crackleTimer = null;
      }
      if (this.pulseTimer) {
        clearInterval(this.pulseTimer);
        this.pulseTimer = null;
      }
    }

    triggerCrackle() {
      const ctx = this.ctx;
      if (!ctx) return;
      const now = ctx.currentTime;
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      const filter = ctx.createBiquadFilter();
      filter.type = 'bandpass';
      filter.frequency.value = 1400 + Math.random() * 1400;
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(180 + Math.random() * 320, now);
      gain.gain.setValueAtTime(0.0001, now);
      gain.gain.exponentialRampToValueAtTime(0.03 * this.layers.fire, now + 0.008);
      gain.gain.exponentialRampToValueAtTime(0.0001, now + 0.055);
      osc.connect(filter);
      filter.connect(gain);
      gain.connect(this.masterGain);
      osc.start(now);
      osc.stop(now + 0.06);
    }

    triggerHeartbeat(amp) {
      const ctx = this.ensureContext();
      if (!ctx) return;
      const playBeat = (offset, peak) => {
        const t = ctx.currentTime + offset;
        const osc = ctx.createOscillator();
        const g = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(68, t);
        osc.frequency.exponentialRampToValueAtTime(42, t + 0.22);
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(peak, t + 0.035);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 0.26);
        osc.connect(g);
        g.connect(this.masterGain);
        osc.start(t);
        osc.stop(t + 0.28);
      };
      playBeat(0, amp);
      playBeat(0.28, amp * 0.68);
    }

    playRomanticChordStep() {
      if (!this.isPlaying || !this.currentPlaylist || !this.ctx) return;
      const ctx = this.ctx;
      const scale = this.currentPlaylist.scale || [261.63, 329.63, 392.0, 523.25];
      const wave = this.currentPlaylist.waveType || 'sine';

      const patterns = [
        [0, 2, 4],
        [1, 3, 5 % scale.length],
        [2, 4, 0],
        [0, 3, 4]
      ];
      const chord = patterns[this.stepCounter % patterns.length];
      this.stepCounter += 1;

      if (this.stepCounter % 16 === 0 && this.currentPlaylist.tracks) {
        this.trackIndex = (this.trackIndex + 1) % this.currentPlaylist.tracks.length;
        this.emitChange();
      }

      // Warm bass pad root
      const rootFreq = scale[chord[0]] * 0.5;
      this.triggerWarmTone(rootFreq, 'sine', 2.8, 0.14);

      // Gentle staggered arpeggio notes
      chord.forEach((scaleIdx, idx) => {
        const freq = scale[scaleIdx % scale.length];
        const delaySec = idx * 0.34;
        setTimeout(() => {
          if (!this.isPlaying) return;
          this.triggerWarmTone(freq, wave, 2.2, 0.11 - idx * 0.015);
        }, delaySec * 1000);
      });
    }

    triggerWarmTone(freq, waveType, durationSec, peakGain) {
      const ctx = this.ctx;
      if (!ctx || this.layers.music <= 0.02) return;
      const now = ctx.currentTime;

      const osc1 = ctx.createOscillator();
      const osc2 = ctx.createOscillator();
      const filter = ctx.createBiquadFilter();
      const env = ctx.createGain();

      osc1.type = waveType;
      osc2.type = 'sine';
      osc1.frequency.setValueAtTime(freq, now);
      osc2.frequency.setValueAtTime(freq * 1.003, now); // warm chorus shimmer

      filter.type = 'lowpass';
      filter.frequency.setValueAtTime(980, now);
      filter.frequency.exponentialRampToValueAtTime(420, now + durationSec);

      env.gain.setValueAtTime(0.0001, now);
      env.gain.linearRampToValueAtTime(peakGain, now + 0.16);
      env.gain.exponentialRampToValueAtTime(0.0001, now + durationSec);

      osc1.connect(filter);
      osc2.connect(filter);
      filter.connect(env);
      env.connect(this.musicGain);

      osc1.start(now);
      osc2.start(now);
      osc1.stop(now + durationSec + 0.05);
      osc2.stop(now + durationSec + 0.05);
    }

    playPlaylist(playlist) {
      if (!playlist) return;
      this.ensureContext();
      this.currentPlaylist = playlist;
      this.trackIndex = 0;
      if (playlist.defaultLayers) {
        this.layers = Object.assign({}, playlist.defaultLayers);
      }
      this.isPlaying = true;

      if (this.noteTimer) clearInterval(this.noteTimer);
      this.startAmbientBeds();
      this.applyLayerVolumes();

      const intervalMs = Math.round((60 / (playlist.tempo || 66)) * 2600);
      this.playRomanticChordStep();
      this.noteTimer = setInterval(() => this.playRomanticChordStep(), intervalMs);
      this.emitChange();
    }

    toggle(defaultPlaylist) {
      if (this.isPlaying) {
        this.pause();
      } else if (this.currentPlaylist) {
        this.playPlaylist(this.currentPlaylist);
      } else if (defaultPlaylist) {
        this.playPlaylist(defaultPlaylist);
      }
    }

    pause() {
      this.isPlaying = false;
      if (this.noteTimer) {
        clearInterval(this.noteTimer);
        this.noteTimer = null;
      }
      this.stopAmbientBeds();
      this.emitChange();
    }

    setLayerVolume(layerName, value) {
      const v = Math.max(0, Math.min(1, Number(value) || 0));
      this.layers[layerName] = v;
      this.applyLayerVolumes();
      this.emitChange();
    }

    applyLayerVolumes() {
      if (!this.ctx) return;
      const now = this.ctx.currentTime;
      if (this.musicGain) this.musicGain.gain.setTargetAtTime(this.layers.music, now, 0.08);
      if (this.rainGain) this.rainGain.gain.setTargetAtTime(this.layers.rain * 0.22, now, 0.08);
      if (this.fireGain) this.fireGain.gain.setTargetAtTime(this.layers.fire * 0.34, now, 0.08);
      if (this.pulseGain) this.pulseGain.gain.setTargetAtTime(this.layers.pulse * 0.3, now, 0.08);
    }

    playChime(kind) {
      const ctx = this.ensureContext();
      if (!ctx) return;
      const now = ctx.currentTime;

      if (kind === 'heartbeat') {
        this.triggerHeartbeat(0.22);
        return;
      }

      const notes =
        kind === 'complete'
          ? [523.25, 659.25, 783.99, 1046.5]
          : kind === 'unlock'
          ? [440, 554.37, 659.25]
          : [587.33, 783.99];

      notes.forEach((freq, idx) => {
        const t = now + idx * 0.11;
        const osc = ctx.createOscillator();
        const g = ctx.createGain();
        osc.type = 'sine';
        osc.frequency.setValueAtTime(freq, t);
        g.gain.setValueAtTime(0.0001, t);
        g.gain.linearRampToValueAtTime(0.12, t + 0.02);
        g.gain.exponentialRampToValueAtTime(0.0001, t + 0.65);
        osc.connect(g);
        g.connect(this.masterGain);
        osc.start(t);
        osc.stop(t + 0.7);
      });
    }

    emitChange() {
      if (typeof this.onChange === 'function') {
        this.onChange({
          isPlaying: this.isPlaying,
          playlist: this.currentPlaylist,
          trackName:
            this.currentPlaylist && this.currentPlaylist.tracks
              ? this.currentPlaylist.tracks[this.trackIndex % this.currentPlaylist.tracks.length]
              : '',
          layers: Object.assign({}, this.layers)
        });
      }
    }
  }

  root.SahraAudio = new SahraAudioEngine();
})(typeof globalThis !== 'undefined' ? globalThis : this);
