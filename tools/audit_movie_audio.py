"""Compare native MP2 PCM against FFmpeg, with synthetic and optional local movies.

Compile runtime/native/tests/mpeg_audio_probe.c as C11 first, then pass --probe.
FFmpeg is a developer-only independent oracle; it is not bundled with the APK.
"""
import argparse
import json
import subprocess
import tempfile
import wave
from pathlib import Path
import numpy as np


def audit(probe, ffmpeg, movie, seconds):
    native = subprocess.run([str(probe), str(movie), str(seconds)], capture_output=True, check=True)
    info = json.loads(native.stderr)
    cmd = [str(ffmpeg), '-v', 'error', '-i', str(movie), '-vn', '-t', str(seconds)]
    if info['channels'] == 1:
        cmd += ['-af', 'pan=stereo|c0=c0|c1=c0']
    reference = subprocess.check_output(cmd + ['-c:a', 'pcm_s16le', '-f', 's16le', '-'])
    a = np.frombuffer(native.stdout, dtype='<i2').astype(np.float64)
    b = np.frombuffer(reference, dtype='<i2').astype(np.float64)
    n = min(len(a), len(b))
    if n == 0 or abs(len(a) - len(b)) > 2304:
        raise AssertionError(f'{movie.name}: sample count mismatch {len(a)} / {len(b)}')
    a, b = a[:n], b[:n]
    error = a - b
    mse = float(np.mean(error * error))
    snr = float(10 * np.log10(np.mean(b * b) / max(mse, 1e-20)))
    result = dict(name=movie.name, sample_rate=info['rate'], source_channels=info['channels'],
                  compared_frames=n // 2, snr_db=snr, rms_error_16bit=float(np.sqrt(mse)),
                  max_error_16bit=float(np.max(np.abs(error))),
                  native_clipped_samples=int(np.count_nonzero(np.abs(a) >= 32767)),
                  reference_clipped_samples=int(np.count_nonzero(np.abs(b) >= 32767)))
    # No time alignment or gain adjustment is allowed to hide decoder errors.
    if snr < 60:
        raise AssertionError(f'{movie.name}: PCM fidelity below 60 dB: {result}')
    return result


def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('--probe', required=True, type=Path)
    cli.add_argument('--ffmpeg', required=True, type=Path)
    cli.add_argument('--movie', action='append', default=[], type=Path)
    cli.add_argument('--seconds', type=float, default=20)
    cli.add_argument('--out', type=Path, default=Path('build/movie-audio-audit.json'))
    args = cli.parse_args()
    args.out.parent.mkdir(parents=True, exist_ok=True)
    results = []
    # Distinct frequencies in both channels test sample rate, channel mapping,
    # silence, changing amplitude, and quantization over multiple MP2 bitrates.
    with tempfile.TemporaryDirectory(prefix='audio-fixtures-', dir=args.out.parent) as temp:
        temp = Path(temp)
        for rate, channels, bitrate in [(32000, 1, 64), (44100, 2, 192), (48000, 2, 192), (48000, 2, 384)]:
            t = np.arange(rate * 3) / rate
            envelope = np.where(t < .15, 0., .4 + .3 * np.sin(2 * np.pi * .7 * t)**2)
            left = envelope * (.6*np.sin(2*np.pi*440*t) + .3*np.sin(2*np.pi*6571*t))
            right = envelope * (.6*np.sin(2*np.pi*913*t) + .3*np.sin(2*np.pi*11231*t))
            samples = left if channels == 1 else np.stack([left, right], axis=1)
            wav = temp/'tone.wav'; movie = temp/f'tone-{rate}-{channels}-{bitrate}.mpg'
            with wave.open(str(wav), 'wb') as out:
                out.setnchannels(channels);out.setsampwidth(2);out.setframerate(rate)
                out.writeframes((samples * 32767).astype('<i2').tobytes())
            subprocess.run([str(args.ffmpeg), '-v', 'error', '-f', 'lavfi', '-i', 'color=c=black:s=32x32:r=25',
                            '-i', str(wav), '-t', '3', '-c:v', 'mpeg1video', '-c:a', 'mp2', '-b:a', f'{bitrate}k',
                            '-f', 'mpeg', '-y', str(movie)], check=True)
            results.append(audit(args.probe, args.ffmpeg, movie, 3))
        for movie in args.movie:
            results.append(audit(args.probe, args.ffmpeg, movie, args.seconds))
    args.out.write_text(json.dumps({'passed': True, 'cases': results}, indent=2)+'\n', encoding='utf-8')
    for result in results:
        print(f"PASS {result['name']} SNR={result['snr_db']:.2f} dB RMS={result['rms_error_16bit']:.3f}")


if __name__ == '__main__':
    main()
