import 'package:audioplayers/audioplayers.dart';

class SoundService {
  AudioPlayer? _sfxPlayer;
  AudioPlayer? _musicPlayer;

  bool _soundEnabled = true;
  bool _musicEnabled = false;
  bool _isMusicPlaying = false;

  bool get soundEnabled => _soundEnabled;
  bool get musicEnabled => _musicEnabled;

  AudioPlayer _ensureSfxPlayer() => _sfxPlayer ??= AudioPlayer();
  AudioPlayer _ensureMusicPlayer() => _musicPlayer ??= AudioPlayer();

  void updatePreferences({
    required bool soundEnabled,
    required bool musicEnabled,
  }) {
    _soundEnabled = soundEnabled;
    _musicEnabled = musicEnabled;
    if (!musicEnabled) {
      stopAmbientMusic();
    } else {
      startAmbientMusic();
    }
  }

  Future<void> playSplashIntro() async {
    if (!_soundEnabled) return;
    await _playSfx('sounds/splash_intro.wav', volume: 0.75);
  }

  Future<void> playExpenseAdd() async {
    if (!_soundEnabled) return;
    await _playSfx('sounds/expense_add.wav', volume: 0.70);
  }

  Future<void> playSuccess() async {
    if (!_soundEnabled) return;
    await _playSfx('sounds/success_chime.wav', volume: 0.80);
  }

  Future<void> startAmbientMusic() async {
    if (!_musicEnabled || _isMusicPlaying) return;
    try {
      final player = _ensureMusicPlayer();
      await player.setReleaseMode(ReleaseMode.loop);
      await player.setVolume(0.22);
      await player.play(AssetSource('sounds/ambient_soft.wav'));
      _isMusicPlaying = true;
    } catch (_) {
      _isMusicPlaying = false;
    }
  }

  Future<void> stopAmbientMusic() async {
    final player = _musicPlayer;
    if (player == null) {
      _isMusicPlaying = false;
      return;
    }
    try {
      await player.stop();
    } catch (_) {
      // Ignore in test / headless environments
    } finally {
      _isMusicPlaying = false;
    }
  }

  Future<void> _playSfx(String assetPath, {double volume = 0.7}) async {
    try {
      final player = _ensureSfxPlayer();
      await player.stop();
      await player.setVolume(volume);
      await player.play(AssetSource(assetPath));
    } catch (_) {
      // Ignore audio errors on unsupported/test devices
    }
  }

  Future<void> dispose() async {
    try {
      await _sfxPlayer?.dispose();
      await _musicPlayer?.dispose();
    } catch (_) {}
  }
}
