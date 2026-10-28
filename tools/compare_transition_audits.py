"""Compare DdTransitionAudit runs on the same graphics backend."""
import argparse
import json
from pathlib import Path


def compare(before, after):
    old, new = [json.loads(path.read_text(encoding='utf-8')) for path in (before, after)]
    if old['checks'] < 11 or new['checks'] < 11 or len(old['phases']) != 3 or len(new['phases']) != 3:
        raise ValueError('Both runs must complete all three eyelid transitions')
    phases = []
    for a, b in zip(old['phases'], new['phases']):
        for key in ['phase', 'pc', 'duration_ms', 'mask', 'frame_sha256']:
            if a[key] != b[key]:
                raise ValueError(f"Phase {a['phase']}: {key} changed")
        if len(a['frame_sha256']) != 9:
            raise ValueError('Expected nine sampled frames per phase')
        phases.append({
            'phase': a['phase'], 'matching_frames': 9,
            'before_cold_ms': a['render_ns'][0] / 1e6,
            'after_cold_ms': b['render_ns'][0] / 1e6,
            'before_warm_median_ms': a['warm_median_ms'],
            'after_warm_median_ms': b['warm_median_ms'],
            'warm_speedup': a['warm_median_ms'] / b['warm_median_ms'],
        })
    return {'matching_frames': 27, 'phases': phases,
            'scope': 'Render-only elapsed time on the same backend; not physical-device FPS or full media playback.'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('before', type=Path)
    parser.add_argument('after', type=Path)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    report = json.dumps(compare(args.before, args.after), indent=2) + '\n'
    if args.output:
        args.output.write_text(report, encoding='utf-8')
    print(report, end='')
