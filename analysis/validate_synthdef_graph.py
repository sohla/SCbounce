#!/usr/bin/env python3
"""Check the source parser against SuperCollider's own view of a SynthDef.

`synthdef_graph.py` reads SuperCollider source and never runs it, which is the
only way to get topology for historical revisions. The risk is obvious: a
parser can be confidently wrong. This checks it against the authority.

The oracle is sclang itself. Each personality is compiled in a throwaway
interpreter, and every SynthDef it added is asked for its real UGen list via
`SynthDescLib`. That list is exact - it is what the server would run.

The comparison is deliberately coarse, because the parser is deliberately
coarse. A signal-flow chart at assignment granularity cannot be checked
node-for-node against a UGen graph; ten `BinaryOpUGen`s and a `MulAdd` have no
counterpart in it. What *can* be checked, and what actually catches parse
failures:

  1. every named SynthDef is found at all
  2. the set of real UGen classes the parser saw matches sclang's, ignoring
     the arithmetic and control plumbing the chart does not draw
  3. the parser's terminal node exists exactly when sclang has an Out

Run it after changing the parser:  python3 validate_synthdef_graph.py
Requires SuperCollider installed; it is a development check, and nothing in
the shipped pipeline depends on sclang.
"""

import glob
import json
import re
import subprocess
import sys
from pathlib import Path

import synthdef_graph as sg

HERE = Path(__file__).resolve().parent
SCLANG_CANDIDATES = [
    '/Applications/SuperCollider/SuperCollider.app/Contents/MacOS/sclang',
    '/Applications/SuperCollider.app/Contents/MacOS/sclang',
    'sclang',
]

# Structural plumbing sclang reports that a signal-flow chart has no business
# drawing: arithmetic, controls, constants, output proxies.
PLUMBING = {
    'BinaryOpUGen', 'UnaryOpUGen', 'MulAdd', 'Sum3', 'Sum4', 'Control',
    'AudioControl', 'TrigControl', 'LagControl', 'OutputProxy', 'Rand',
    'IRand', 'ExpRand', 'TExpRand', 'TRand', 'LinRand', 'NRand',
}

# UGens that exist only because SuperCollider turns a *method* into one:
# `sig.lag(0.1)` is a Lag, `x.clip(0, 1)` is a Clip. They modify a signal
# rather than forming a stage, so they sit below the granularity of the chart.
OPERATORS = {
    'Lag', 'Lag2', 'Lag3', 'LagUD', 'Lag2UD', 'Lag3UD', 'VarLag',
    'Clip', 'Fold', 'Wrap', 'Schmidt', 'InRange', 'Round', 'Trunc',
    'LinExp', 'LinLin', 'ExpRand', 'Slew', 'Integrator', 'Decay', 'Decay2',
    'Slope', 'ToggleFF', 'SetResetFF',
    # `a.blend(b, 0.2)` is an XFade2. The node and both its input edges are
    # captured; only the class label is absent, because the receiver is a
    # variable rather than a UGen class. Excused only when the parser did not
    # already find an explicit XFade2.ar.
    'XFade2',
}

# Composite UGens expand into primitives on compile: `DynKlank` becomes a bank
# of `Ringz`, `Splay` becomes `Pan2`, `SoundIn` becomes `In`. Rather than
# ignoring the primitives outright - which would let a genuinely missed stage
# hide behind the exemption - a primitive is only excused when the parser
# actually found a composite that produces it.
EXPANSIONS = {
    'Ringz': {'DynKlank', 'Klank', 'Formlet'},
    'Pan2': {'Splay', 'PanAz', 'SplayAz', 'Balance2'},
    'XFade2': {'LinXFade2', 'SelectX', 'SelectXFocus', 'Splay'},
    'Select': {'SelectX', 'SelectXFocus'},
    'In': {'SoundIn', 'AudioIn'},
    'NumOutputBuses': {'SoundIn', 'AudioIn'},
    'GreyholeRaw': {'Greyhole'},
    'JPverbRaw': {'JPverb'},
    'Impulse': {'Dust', 'Blip'},
    'EnvGen': {'Env', 'Linen'},
    'Linen': {'Env'},
    'DelayN': {'CombN', 'AllpassN'},
    'BufRateScale': {'PlayBuf'},
}


def find_sclang():
    for candidate in SCLANG_CANDIDATES:
        if Path(candidate).exists():
            return candidate
        try:
            subprocess.run([candidate, '-v'], capture_output=True, timeout=5)
            return candidate
        except (OSError, subprocess.SubprocessError):
            continue
    return None


SYNTHDEF_HEAD = re.compile(r'^SynthDef\s*\(\s*(\\(\w+)|"(\w+)"|\'(\w+)\')')


def rename_def(raw, unique):
    """Rewrite a SynthDef literal's name so defs from different files coexist.

    SynthDescLib is keyed by name and several personalities define the same
    one (`\\stereoSampler` appears in a number of them), so without this the
    later file silently overwrites the earlier and the attribution is wrong.
    """
    return SYNTHDEF_HEAD.sub(f'SynthDef(\\\\{unique}', raw, count=1)


def oracle(sclang, entries):
    """{"file|defname": [ugen classes]} straight from sclang.

    Each SynthDef literal is compiled on its own, in a protect block, so one
    that will not build in this environment cannot take the run down with it.
    """
    # One file per SynthDef, loaded inside a try.
    #
    # Emitting all 207 literals into a single script does not work: a syntax
    # error anywhere means the whole file fails to *parse*, so nothing runs at
    # all - including the `0.exit` - and sclang sits at its prompt until the
    # timeout. `.load` fails at runtime instead, where try can catch it, and
    # the driver script stays small enough to be obviously correct.
    probe_dir = HERE / '.validate_defs'
    probe_dir.mkdir(exist_ok=True)
    for old in probe_dir.glob('*.scd'):
        old.unlink()

    written = []
    for key, raw in entries:
        unique = key.replace('|', '__').replace('.', '_').replace('-', '_')
        probe = probe_dir / f'{unique}.scd'
        probe.write_text(f'({rename_def(raw, unique)}).add;\n')
        written.append(str(probe))

    # An explicit list rather than PathName(dir).files: the probe directory is
    # hidden, and relying on a directory listing to find it silently yielded
    # nothing.
    listing = '[\n' + ',\n'.join(f'"{p}"' for p in written) + '\n]'
    script = f'''
var result = IdentityDictionary.new;
{listing}.do {{ |path|
    try {{ path.load }} {{ |err| nil }};
}};
SynthDescLib.global.synthDescs.keysValuesDo {{ |key, desc|
    var ugens = nil;
    try {{ ugens = desc.def.children.collect {{ |u| u.class.name.asString }} }} {{ nil }};
    if(ugens.notNil) {{ result[key] = ugens }};
}};
("@@JSON@@" ++ result.asCompileString ++ "@@END@@").postln;
0.exit;
'''
    script_path = HERE / '.validate_probe.scd'
    script_path.write_text(script)
    try:
        run = subprocess.run([sclang, str(script_path)], capture_output=True,
                             text=True, timeout=420, errors='replace')
    except subprocess.TimeoutExpired:
        subprocess.run(['pkill', '-f', 'MacOS/sclang'], capture_output=True)
        return None, 'sclang timed out and was stopped.'
    finally:
        script_path.unlink(missing_ok=True)
        for probe in probe_dir.glob('*.scd'):
            probe.unlink()
        probe_dir.rmdir()

    match = re.search(r'@@JSON@@(.*?)@@END@@', run.stdout, re.DOTALL)
    if not match:
        return None, run.stdout

    # sclang's asCompileString, not JSON. An IdentityDictionary renders as
    #   IdentityDictionary[ ('key' -> [ "A", "B" ]), ... ]
    # with an arrow, not a colon - which is what silently matched nothing and
    # made every SynthDef look as though it had failed to compile.
    parsed = {}
    for entry in re.finditer(r"'([^']+)'\s*->\s*\[([^\]]*)\]", match.group(1)):
        key = entry.group(1)
        ugens = re.findall(r'"([^"]+)"', entry.group(2))
        parsed[key] = ugens
    return parsed, run.stdout


def main():
    sclang = find_sclang()
    if not sclang:
        print("sclang not found - this is a development check and needs "
              "SuperCollider installed.")
        return 2

    # Extract every SynthDef literal so each can be compiled in isolation.
    entries = []
    mine = {}
    for path in sorted(glob.glob('../personalities/*.sc')):
        stem = Path(path).stem
        source = Path(path).read_text(errors='replace')
        for definition, graph in zip(sg.find_synthdefs(source), sg.graphs_for(source)):
            key = f"{stem}|{definition['name']}"
            entries.append((key, definition['raw']))
            mine[key.replace('|', '__').replace('.', '_').replace('-', '_')] = \
                (stem, definition['name'], graph)

    print(f"Compiling {len(entries)} SynthDef literals in sclang "
          f"(this takes a minute)...")
    truth, raw = oracle(sclang, entries)
    if truth is None:
        print("Could not read sclang's answer. Tail of its output:")
        print('\n'.join(raw.strip().splitlines()[-15:]))
        return 2
    print(f"sclang compiled {len(truth)} SynthDefs\n")

    compared = mismatched = 0
    problems = []

    for key, real_ugens in sorted(truth.items()):
        if key not in mine:
            continue
        stem, defname, graph = mine[key]
        compared += 1

        parsed = {u for n in graph['nodes'] for u in n['ugens']}
        actual = set(real_ugens) - PLUMBING - OPERATORS

        # Classes sclang saw that the parser did not: the failure that matters,
        # because it means a whole stage is absent from the chart. A primitive
        # is excused only when the parser found a composite that expands to it.
        overlooked = set()
        for ugen in actual - parsed:
            if EXPANSIONS.get(ugen, set()) & parsed:
                continue
            overlooked.add(ugen)
        if overlooked:
            mismatched += 1
            problems.append(
                f"MISSED   {stem:24s} \\{defname:20s} {sorted(overlooked)}")

        has_out = graph['terminal'] is not None
        if ('Out' in real_ugens) != has_out:
            problems.append(
                f"OUT      {stem:24s} \\{defname:20s} "
                f"sclang={'Out' in real_ugens} parser={has_out}")

    print(f"  SynthDefs sclang built  : {len(truth)}")
    print(f"  compared                : {compared}")
    print(f"  missing a stage         : {mismatched}")
    print(f"  clean                   : {compared - mismatched}")
    uncompiled = len(mine) - compared
    if uncompiled:
        print(f"  sclang could not build  : {uncompiled} "
              f"(not a parser failure - reported for honesty)")
    if problems:
        print("\nProblems:")
        for line in problems[:40]:
            print("  " + line)
        if len(problems) > 40:
            print(f"  ... and {len(problems) - 40} more")
    return 0 if not problems else 1


if __name__ == '__main__':
    sys.exit(main())
