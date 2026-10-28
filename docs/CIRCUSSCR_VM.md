# CircusScr1.0 virtual machine

This frontend is for the Unity 5.5 / Mono version of Da Capo 3 R. It is separate from the older MES frontend. The descriptions below come from static inspection of the local managed dispatch and read-only parsing of its original `script0` bundle. No original game scripts or decompiled game implementation are distributed with the runtime.

## Evidence and coverage

The inspected `Assembly-CSharp.dll` has SHA-256 `19bd91ed62f22e2c95dd3ffa944a9d97a76ef22a7b9e222503cfdc3225795c31`; `GameDataTypes.dll` has SHA-256 `678d8fee31169667cb25768f255bddd206836bb83a9ebb8ed9384182606de438`. Relevant dispatch structures are `CircusScript.PerformAdvance`, `CircusScript.Function`, `ScriptSystem`, `GameMemory`, `BaseCommands`, and `ScriptCommands`.

`CircusScrCorpusAudit` parsed all 634 scripts in the local bundle: 2,041,888 VM instructions, 344,899 host calls, 149,127 strings, 5,923 named labels, and 33,586 branch targets. Every declared preload target was present. These counts establish bytecode format coverage; they do not establish presentation or complete route compatibility.

## Binary format

All integer fields are signed 32-bit little endian unless specified otherwise.

| Field | Representation |
| --- | --- |
| Signature | Exactly 12 ASCII bytes, `CircusScr1.0` |
| Instruction count | Integer |
| Instructions | Count repetitions of `(opcode, operand)` |
| String count | Integer |
| Strings | .NET BinaryReader string: unsigned 7-bit encoded UTF-8 byte count, then those bytes |
| Label count | Integer |
| Labels | Count repetitions of `(string name, instruction index)` |
| Branch count | Integer |
| Branch targets | Array of instruction indices |
| Preload count | Integer |
| Preloads | Array of script/bundle names encoded as strings |
| Own bundle flag | Single boolean byte |

Label and branch targets address eight-byte instruction records by their zero-based index. They are not byte offsets. Named labels are case-sensitive. The resource library normalizes script filenames to lower case. Texture, sound, and script resolution remain platform resource operations.

The parser validates sizes, UTF-8, table references, branches, function descriptors, duplicate labels, and the exact end of the file before execution.

## Registers and operators

The VM has signed 32-bit registers `R1` and `R2`, an integer expression stack, and a separate stack of typed host arguments. Java arithmetic follows the original unchecked 32-bit addition, subtraction, and multiplication. Division truncates toward zero; division by zero and signed division overflow fault.

| Opcode | Operand | Operation |
| --- | --- | --- |
| 0 | Integer | `R1 = operand` |
| 1 | Ignored | Push `R1` onto expression stack |
| 2 | Ignored | Pop expression stack into `R2` |
| 3 | Type | Push `R1` as integer if type is zero; otherwise push string table entry `R1` |
| 4 | Packed descriptor | Invoke host command; high 16 bits are argument count, low 8 bits are command ID |
| 5 | Ignored | `R1 = flags[R1]` |
| 6 | Packed flag address | `flags[operand] = R1` |
| 7 | Ignored | `R1 = R2 + R1` |
| 8 | Ignored | `R1 = R2 - R1` |
| 9 | Ignored | `R1 = R2 * R1` |
| 10 | Ignored | `R1 = R2 / R1` |
| 11 | Ignored | Logical OR of the nonzero tests of both registers, resulting in 0 or 1 |
| 12 | Ignored | Logical AND of the nonzero tests of both registers, resulting in 0 or 1 |
| 13 | Ignored | Bitwise OR |
| 14 | Ignored | Bitwise AND |
| 15 | Ignored | `R2 >= R1`, resulting in 0 or 1 |
| 16 | Ignored | `R2 <= R1`, resulting in 0 or 1 |
| 17 | Ignored | `R2 > R1`, resulting in 0 or 1 |
| 18 | Ignored | `R2 < R1`, resulting in 0 or 1 |
| 19 | Ignored | Equality, resulting in 0 or 1 |
| 20 | Ignored | Inequality, resulting in 0 or 1 |
| 21, 22, 25 | — | Enum values without an original runtime handler; not emitted in the inspected corpus |
| 23 | Ignored | End of script |
| 24 | Branch table index | If `R1 == 0`, jump to the referenced instruction |
| 26 | Source line | Diagnostic source line metadata |
| 27 | Branch table index | Unconditional branch |
| 28 | String table index | Set current source filename metadata |

Parameters are popped in reverse order into the host argument array, preserving their original left-to-right order. Integer and string arguments stay distinct. A string argument's numeric field remains zero, matching `CsData.Val`; reading an integer as a string fails. Unknown host commands fault unless the host explicitly implements them or identifies an original no-op.

## Variables

Flags are dictionary entries addressed as `(bank << 16) + index`. Their values are signed 32-bit integers; old MES byte wrapping must not be applied. Missing entries return zero. Addresses below `10 << 16` belong to local story state; the others belong to global state. Clearing local flags preserves global flags and restores `AllowSkip` (`5:1`) to 1.

Some flags describe live host state, including audio completion and whether the current script has already been viewed. Those values are supplied by the host. The VM itself owns no synthetic system flag arrays.

## Script control functions

| Command | Arguments | Meaning |
| --- | --- | --- |
| 37 | Script name | The original implementation supports the `advmenu` call, which pushes the current context even inside another quick script |
| 38 | None | Return from quick script; if `99:19` is 4, jump to `rnew`; reset that flag |
| 39 | None | Return from quick script without the `rnew` condition |
| 59 | None | Exit |
| 74 | Flag address, value | Write a 32-bit flag |
| 89 | Label | Jump to a label in the current script |
| 134 | Script | Quick script change |
| 135 | Script, label | Quick script change with an entry label |
| 148 | Script | Replace the script and clear the quick-call stack |
| 149 | Script, label | Replace the script at an entry label and clear the quick-call stack |

A quick script change saves the caller only when the quick-call stack is empty. Further quick script changes replace the current quick context. The explicit `advmenu` call forces an additional push. A saved context contains the script, next instruction, both registers, expression and parameter stacks, and source location.

The host sees control functions before the VM switches scripts so that it can commit the outgoing scene and earned progress. An `afterTransfer` callback can restore a suspended text wait after returning from an original system menu. Host presentation state and VM state are both part of a runtime snapshot.

The inspected corpus has 34 explicit calls, all to `advmenu`, and no missing targets for literal named jumps or script transfers. Some system assets use upper-case names (`SAVE`, `LOAD`, `OPTION`); their resource lookup is case-insensitive. System screens continue through those original scripts and their host operations.

## Verification

`CircusScrVmTests` uses synthetic scripts for arithmetic, comparison, flag width, argument ordering, UTF-8, branch targets, named jumps, cooperative waits, quick-call replacement, save/restore of expression and call stacks, and malformed input. It does not contain original dialogue or assets.

`CircusScrCorpusAudit GAME_DIRECTORY` performs the original-data format audit in memory. `UnityStartupAudit GAME_DIRECTORY OUTPUT_DIRECTORY` separately exercises the original startup/menu/story path and records whether media completion was simulated. Format audits and simulated media completion cannot substitute for audiovisual playback checks.

The startup audit accepts an optional maximum dialogue count and an optional `RangeStorage` output path. A limit of 2000 reaches the first story choice in this game: the inspected path contains 1,145 dialogue waits, and both choice buttons were selected independently from the same saved state. Each branch reached its own next dialogue. The audit also compares scene pixels before and after restoring a story snapshot and converts each encountered FSB5 audio resource to Ogg.

`UnitySystemAudit GAME_DIRECTORY OUTPUT_DIRECTORY` uses an isolated save directory and operates the original controls for SAVE, LOAD, OPTION, LOG, and EXTRA. It recreates the engine while SAVE is open, then confirms that the selected slot contains the suspended story state and returns to the same dialogue. It also checks settings changes, history, CG/Scene Jump/Music entry and return, and Music Play/Stop. Gallery entry checks start with ordinary progress; they do not force unlocks or establish that every ending is completable.

Range recordings contain only file ranges touched by the audit, plus visible button states, transition masks, and the current animation packs needed between sampled frames. They are local test fixtures containing original resource bytes, not redistributable runtime assets.
