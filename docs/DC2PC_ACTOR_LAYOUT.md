# DC2PC close-up placement and compositing

The reported Dacapo dialogue is in `1ind_0119_e1_skr.mes`: text at `0x85cc`, message wait at `0x85ec`. It intentionally shows two actors, not an abandoned sprite from an earlier scene.

| MES offset | Command | Arguments/resource |
| --- | --- | --- |
| `0x84c3` | actor position | `(87, 0, slot 0, 0)` |
| `0x84cc` | actor position | `(-125, 0, slot 1, 0)` |
| `0x84d5` | actor transform | `(75, 441, 322, 16)` |
| `0x84de` | actor 0 | `bst/skr_0c_008*`, 882 × 756 |
| `0x84f0` | actor transform | `(100, 441, 296, 17)` |
| `0x84f9` | actor 1 | `bst/otm_0b_200_b*`, 1260 × 1080 |

Read-only disassembly of local `dc2pc.EXE` establishes the behavior:

- `0x41E8C5` stores position and resets percentage to 100; `0x41E9D4..0x41EA02` stores percentage and anchor for transform selectors 16–19.
- `0x41A950..0x41AB57` draws actors in ascending slot order, 0–3. The later slot covers earlier actors. The interpreter previously drew them in reverse order.
- At percentages other than 100, `0x41A985..0x41AA78` scales dimensions and offsets with integer arithmetic. The position is `position + anchor - trunc(anchor * percentage / 100)`.
- At exactly 100%, `0x41AA7D..0x41AB14` keeps the bitmap dimensions. If width exceeds 882, the x offset is `positionX + anchorX - trunc(trunc(width * 100 / 882) * anchorX / 100)`. Height independently uses the same rule with 756. These are two integer divisions, not a floating-point ratio.
- `0x41BA90..0x41BC78`, the native damage-region calculation, independently repeats the same geometry.
- Native blitters `0x40B840`/`0x40B5F0` and `0x40D7C0` take the resulting destination coordinates directly; they do not add a CRX header origin. All 3,691 local `BST` CRXG resources have zero header origins.

For the reported frame, Otome's large back-view is therefore drawn at **(-310, -124)**, not the former **(-125, 0)**. Sakura's 75% image starts at **(198, 81)** with size **661 × 567**. Combined with the correct slot order, this gives Otome's back in the left foreground, facing Sakura on the right. The subsequent reverse angle at `0x9a13..0x9abf` uses the same rules with the actors exchanged.

The implementation is restricted to profile `dc2pc-5769`; the DM and PC1 placement paths are unchanged. Geometry is evaluated during rendering, including captures, so existing interpreter snapshots require no field migration.

Validation: `PcActorLayoutTests` checks nine independently calculated rectangles, including signed anchors, rounding and width/height thresholds. `PcActorLayoutAudit` runs original START initialization, explicitly transfers to the reported script for this audit, reaches both camera angles, restores a pre-fix snapshot, checks a save round trip and tests slot/accessory compositing with independent solid-color pixels. Ten audit checks pass. Media completion is simulated; this is not a complete-route playthrough or a comparison against a running Windows game. Local images and disassembly are in `build/dc2pc-sprite-residue/` and are not shipped as game assets.
