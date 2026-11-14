import 'package:audioplayers/audioplayers.dart';

class SoundService {
  final AudioPlayer _sfxPlayer = AudioPlayer();
  final AudioPlayer _musicPlayer = AudioPlayer();

  bool _soundEnabled = true;
  bool _musicEnabled = false;
  bool _isMusicPlaying = false;

  bool get soundEnabled => _soundEnabled;
  bool get musicEnabled => _musicEnabled;

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
      await _musicPlayer.setReleaseMode(ReleaseMode.loop);
      await _musicPlayer.setVolume(0.22);
      await _musicPlayer.play(AssetSource('sounds/ambient_soft.wav'));
      _isMusicPlaying = true;
    } catch (_) {
      _isMusicPlaying = false;
    }
  }

  Future<void> stopAmbientMusic() async {
    try {
      await _musicPlayer.stop();
    } catch (_) {
      // Ignore in test / headless environments
    } finally {
      _isMusicPlaying = false;
    }
  }

  Future<void> _playSfx(String assetPath, {double volume = 0.7}) async {
    try {
      await _sfxPlayer.stop();
      await _sfxPlayer.setVolume(volume);
      await _sfxPlayer.play(AssetSource(assetPath));
    } catch (_) {
      // Ignore audio errors on unsupported/test devices
    }
  }

  Future<void> dispose() async {
    try {
      await _sfxPlayer.dispose();
      await _musicPlayer.dispose();
    } catch (_) {}
  }
}
