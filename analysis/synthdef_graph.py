#!/usr/bin/env python3
"""Signal-flow graphs for SuperCollider SynthDefs, parsed from source.

Deliberately *not* a UGen-level graph. A node here is one variable assignment,
labelled by the outermost UGen in its expression - so `bongo1` comes out as
eight nodes rather than sixty, which is the level a signal flow chart is
actually read at.

That granularity is also what makes the parse robust. At UGen level you have to
model multichannel expansion, nested expression trees and array arithmetic. At
assignment level none of that matters: all a node needs to know is which UGens
appear in its expression and which earlier variables it reads.

**Why parse at all, rather than ask SuperCollider.** sclang can dump a
SynthDef's true graph, and `sc3-dot` renders it - but only for a SynthDef it
can compile *now*. The archive needs topology at every historical revision,
including 2024 files referencing samples that no longer exist against a core
that has since been refactored. Executing those is not viable. Parsing never
runs anything, so it works uniformly across history. `validate_against_sclang`
in the test script uses the sclang path as an oracle for current revisions
only.

Known approximations, all a consequence of the chosen granularity:

- A chain written as one nested expression with no intermediate variable is one
  node. Marked with `dense: True` so the page can say so.
- UGens created inside `.do`/`.collect` collapse into a single node (3 files).
- Arrays are one node, not N channels: `freq * [1, 2.1]` is one edge.
"""

import re

# Rate suffixes that make something a UGen call rather than a method call.
# The optional CamelCase tail catches the `*Fill`/`*Rand` constructors -
# `Splay.arFill(12, ...)` is a UGen exactly as `Splay.ar(...)` is. It cannot
# match `.array` or `.irregular` because the tail must start with a capital.
UGEN_CALL = re.compile(r'\b([A-Z][A-Za-z0-9_]*)\.(ar|kr|ir)(?:[A-Z][A-Za-z0-9_]*)?\b')
IDENTIFIER = re.compile(r'\b([a-z_][A-Za-z0-9_]*)\b')

# An envelope played as a method - `Env.perc(0.001, 0.01).ar` - is an EnvGen,
# but there is no `Class.ar` for the pattern above to find: the receiver is the
# Env instance, not a class.
ENV_AS_UGEN = re.compile(r'\bEnv\.\w+\s*\(.*?\)\s*\.(ar|kr)\b', re.DOTALL)

# Demand-rate UGens are constructed without a rate suffix - `Dseq([...], inf)`
# - so the pattern above cannot see them. Listed explicitly rather than
# matching every `Capitalised(` because that would sweep up Env, Color, Point
# and every other plain class construction.
DEMAND_UGENS = {
    'Dseq', 'Dser', 'Drand', 'Dxrand', 'Dwrand', 'Dwhite', 'Dbrown',
    'Diwhite', 'Dibrown', 'Dgeom', 'Dseries', 'Dswitch', 'Dswitch1',
    'Dbufrd', 'Dbufwr', 'Dconst', 'Dreset', 'Dpoll', 'Dstutter', 'Dunique',
    'Dshuf', 'Dstep',
}
DEMAND_CALL = re.compile(r'\b(D[a-z][A-Za-z0-9_]*)\s*\(')


def ugen_calls(expression):
    """[(name, rate)] for every UGen constructed in an expression."""
    calls = UGEN_CALL.findall(expression)
    calls += [(name, 'dr') for name in DEMAND_CALL.findall(expression)
              if name in DEMAND_UGENS]
    calls += [('EnvGen', rate) for rate in ENV_AS_UGEN.findall(expression)]
    return calls

# Roles, so the chart can colour a spine rather than print class names. Order
# matters: the first match wins, so specific entries precede general ones.
ROLES = [
    (('PlayBuf', 'BufRd', 'GrainBuf', 'Warp1', 'DiskIn', 'VDiskIn'), 'sample'),
    (('SoundIn', 'AudioIn', 'In'), 'input'),
    (('SinOsc', 'Saw', 'LFSaw', 'Pulse', 'LFTri', 'LFCub', 'VarSaw', 'Blip',
      'Formant', 'Gendy1', 'Gendy2', 'Gendy3', 'Osc', 'COsc'), 'oscillator'),
    (('WhiteNoise', 'PinkNoise', 'BrownNoise', 'GrayNoise', 'ClipNoise',
      'Dust', 'Dust2', 'Crackle', 'Impulse'), 'noise'),
    (('DynKlank', 'Klank', 'Ringz', 'Pluck', 'Resonz', 'Formlet'), 'resonator'),
    (('RLPF', 'LPF', 'BLowPass', 'RHPF', 'HPF', 'BHiPass', 'BPF', 'BRF',
      'MoogFF', 'Median', 'LeakDC'), 'filter'),
    (('FreeVerb', 'GVerb', 'JPverb', 'Greyhole'), 'reverb'),
    (('DelayC', 'DelayN', 'DelayL', 'CombC', 'CombN', 'CombL', 'AllpassC',
      'AllpassN', 'AllpassL'), 'delay'),
    (('Compander', 'Limiter', 'Normalizer', 'CompanderD'), 'dynamics'),
    (('Pan2', 'Balance2', 'LinPan2', 'Splay', 'PanAz', 'Mix', 'XFade2'), 'mix'),
    (('EnvGen', 'Line', 'XLine', 'LFNoise0', 'LFNoise1', 'LFNoise2',
      'Amplitude', 'Lag', 'Lag2', 'Lag3', 'Slew', 'Integrator'), 'control'),
    (('DetectSilence', 'FreeSelf', 'Done', 'SendTrig', 'SendReply'), 'housekeeping'),
]


def role_of(ugen):
    for names, role in ROLES:
        if ugen in names:
            return role
    return 'other'


def strip_comments(content):
    """Source with comments blanked out, newlines preserved.

    Lives here rather than in the analyzer because every SuperCollider reader
    in this directory needs it and it must not be defined twice. The analyzer
    imports it from this module.

    Two consumers, two reasons. The analyzer needs it because these p-files
    keep long shelves of commented-out alternatives, and counting those makes
    every personality look as though it reads every sensor. The parser here
    needs it because a comment above an assignment defeats the statement match
    entirely - which is exactly how `bongo1`, whose SynthDef is thoroughly
    commented, first came out as ten disconnected nodes.

    Character scanner rather than a regex because `//` inside a string literal
    (a sample path) is not a comment, and a regex cannot see that.
    """
    out = []
    i, n = 0, len(content)
    in_string = False
    in_block = False
    while i < n:
        char = content[i]
        nxt = content[i + 1] if i + 1 < n else ''
        if in_block:
            if char == '*' and nxt == '/':
                in_block = False
                out.append('  ')
                i += 2
                continue
            out.append('\n' if char == '\n' else ' ')
        elif in_string:
            out.append(char)
            if char == '\\':
                out.append(nxt)
                i += 2
                continue
            if char == '"':
                in_string = False
        elif char == '"':
            in_string = True
            out.append(char)
        elif char == '/' and nxt == '/':
            while i < n and content[i] != '\n':
                i += 1
            continue
        elif char == '/' and nxt == '*':
            in_block = True
            out.append('  ')
            i += 2
            continue
        else:
            out.append(char)
        i += 1
    return ''.join(out)


def split_top_level(text, separator=';'):
    """Split on a separator that is not inside brackets, braces or a string."""
    parts, depth, current = [], 0, ''
    in_string = False
    i = 0
    while i < len(text):
        char = text[i]
        if in_string:
            current += char
            if char == '\\':
                if i + 1 < len(text):
                    current += text[i + 1]
                    i += 2
                    continue
            elif char == '"':
                in_string = False
        elif char == '"':
            in_string = True
            current += char
        elif char in '([{':
            depth += 1
            current += char
        elif char in ')]}':
            depth -= 1
            current += char
        elif char == separator and depth == 0:
            parts.append(current)
            current = ''
        else:
            current += char
        i += 1
    if current.strip():
        parts.append(current)
    return parts


def matching_paren(text, start):
    """Index of the parenthesis closing the one at `start`, or -1."""
    depth = 0
    in_string = False
    i = start
    while i < len(text):
        char = text[i]
        if in_string:
            if char == '\\':
                i += 2
                continue
            if char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char == '(':
            depth += 1
        elif char == ')':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def matching_brace(text, start):
    """Index of the brace closing the one at `start`, or -1."""
    depth = 0
    in_string = False
    i = start
    while i < len(text):
        char = text[i]
        if in_string:
            if char == '\\':
                i += 2
                continue
            if char == '"':
                in_string = False
        elif char == '"':
            in_string = True
        elif char == '{':
            depth += 1
        elif char == '}':
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def find_synthdefs(source):
    """[{name, args, body}] for every SynthDef literal in the source."""
    source = strip_comments(source)
    found = []
    for match in re.finditer(r'SynthDef\s*\(\s*\\?(\w+)', source):
        name = match.group(1)
        brace = source.find('{', match.end())
        if brace < 0:
            continue
        close = matching_brace(source, brace)
        if close < 0:
            continue
        body = source[brace + 1:close]
        args, body = extract_args(body)

        # The whole `SynthDef(...)` expression verbatim, so the validator can
        # hand it to sclang on its own. A p-file cannot be loaded wholesale
        # outside the AirKit environment - `var m = ~model` is nil and the
        # error aborts the file before any `.add` runs - so the literal is the
        # only part that can be compiled in isolation.
        open_paren = source.find('(', match.start())
        close_paren = matching_paren(source, open_paren)
        raw = (source[match.start():close_paren + 1]
               if close_paren > 0 else source[match.start():close + 1])

        found.append({'name': name, 'args': args, 'body': body, 'raw': raw})
    return found


def extract_args(body):
    """(arg names, body with the header removed).

    Both declaration styles appear in this repo: `{ |out=0, amp=1| ... }` in
    143 places and `{ arg out=0, amp=1; ... }` in the rest.
    """
    stripped = body.lstrip()
    offset = len(body) - len(stripped)

    if stripped.startswith('|'):
        end = stripped.find('|', 1)
        if end > 0:
            header = stripped[1:end]
            return arg_names(header), body[offset + end + 1:]

    match = re.match(r'arg\s+([^;]*);', stripped, re.DOTALL)
    if match:
        return arg_names(match.group(1)), body[offset + match.end():]

    return [], body


def arg_names(header):
    names = []
    for piece in split_top_level(header, ','):
        piece = piece.strip()
        if not piece:
            continue
        name = piece.split('=')[0].strip()
        if re.fullmatch(r'[a-z_]\w*', name):
            names.append(name)
    return names


def build_graph(synthdef):
    """A signal-flow graph: nodes are assignments, edges are data dependencies.

    Reassignment produces a new node rather than mutating the old one, so
    `sig = RLPF.ar(sig, ...)` reads as a stage in a chain instead of a
    self-loop. That is the dominant idiom in this repo.
    """
    args = synthdef['args']
    nodes = []
    edges = []
    latest = {}                      # variable name -> index of its newest node
    declared = set(args)

    def add_edge(source_index, target_index, label=None):
        if source_index is None or source_index == target_index:
            return
        pair = (source_index, target_index, label)
        if pair not in edges:
            edges.append(pair)

    for raw in split_top_level(synthdef['body']):
        statement = raw.strip()
        if not statement:
            continue

        # `var a, b, c;` with no assignment only declares names.
        if re.fullmatch(r'var\s+[\w\s,]+', statement):
            for name in re.findall(r'[a-z_]\w*', statement[3:]):
                declared.add(name)
            continue

        out_match = re.match(r'(Out|ReplaceOut|OffsetOut)\.(ar|kr)\s*\(', statement)
        assign = re.match(r'(?:var\s+)?([a-z_]\w*)\s*=\s*(.+)', statement, re.DOTALL)

        if out_match:
            expression = statement
            target = 'Out'
        elif assign:
            target = assign.group(1)
            expression = assign.group(2)
        else:
            # A bare statement that still matters to the signal path, e.g.
            # DetectSilence.ar(sound, doneAction: Done.freeSelf).
            ugens = [u for u, _ in ugen_calls(statement)]
            if not ugens:
                continue
            target = ugens[0]
            expression = statement

        calls = ugen_calls(expression)
        ugens = [name for name, _ in calls]

        # `sig = Out.ar(out, sig * amp * env);` - an assignment whose
        # expression is the output itself. Legal, and it appears in the corpus
        # (inABottle), where it would otherwise leave the graph with no
        # terminal at all.
        if ugens and ugens[0] in ('Out', 'ReplaceOut', 'OffsetOut'):
            target = 'Out'
        rates = [rate for _, rate in calls]
        rate = 'audio' if 'ar' in rates else ('control' if 'kr' in rates else 'scalar')

        # Outermost UGen first: in `Pan2.ar(hs + sig, ...)` and
        # `RLPF.ar(sig, ...)` the enclosing UGen is also the first in source
        # order, which holds for every construction used in this corpus.
        primary = None
        for name, call_rate in calls:
            if call_rate == 'ar':
                primary = name
                break
        if primary is None and ugens:
            primary = ugens[0]

        # A stage built entirely from methods on existing signals - `.blend`,
        # `.sum`, `.tanh` - has no UGen class to name it. Label it by the
        # operation rather than by the variable, which says nothing.
        signal_method = None
        if primary is None:
            method = re.search(
                r'\.(blend|sum|mean|madd|distort|tanh|softclip|clip2|fold2|'
                r'wrap2|scramble|reverse|mirror|sqrt|squared|cubed|abs|neg)\b',
                expression)
            if method:
                signal_method = method.group(1)

        index = len(nodes)
        nodes.append({
            'id': index,
            'var': target,
            'label': primary or signal_method or target,
            'ugens': list(dict.fromkeys(ugens)),
            'role': ('out' if target == 'Out' or primary in (
                'Out', 'ReplaceOut', 'OffsetOut')
                else 'mix' if signal_method in ('blend', 'sum', 'mean')
                else role_of(primary or '')),
            'rate': rate,
            # More than two UGens in one assignment means a chain was written
            # inline; the page marks these so the reader knows the node is
            # standing in for several stages.
            'dense': len([u for u in ugens if role_of(u) not in
                          ('control', 'housekeeping')]) > 2,
            'args': sorted({name for name in IDENTIFIER.findall(expression)
                            if name in args}),
        })

        for name in set(IDENTIFIER.findall(expression)):
            if name in latest and name != target:
                add_edge(latest[name], index)
            elif name == target and name in latest:
                add_edge(latest[name], index)      # sig = f(sig): chain forward

        if target != 'Out':
            latest[target] = index
        else:
            latest.setdefault('__out__', index)

    # A node built only from methods has no rate of its own; it carries
    # whatever its inputs carry, so an audio stage is not drawn as if it were
    # a control value.
    by_id = {n['id']: n for n in nodes}
    for _ in range(3):                        # a few passes settle any chain
        for source, target, _label in edges:
            node = by_id.get(target)
            parent = by_id.get(source)
            if node and parent and node['rate'] == 'scalar':
                if parent['rate'] in ('audio', 'control'):
                    node['rate'] = parent['rate']

    # Nodes nothing reads and which read nothing are noise (stray constants).
    connected = {s for s, _, _ in edges} | {t for _, t, _ in edges}
    kept = [n for n in nodes
            if n['id'] in connected or n['role'] in ('out', 'sample', 'input')
            or n['ugens']]

    return {
        'name': synthdef['name'],
        'args': args,
        'nodes': kept,
        'edges': [{'from': s, 'to': t} for s, t, _ in edges],
        'terminal': next((n['id'] for n in nodes if n['role'] == 'out'), None),
    }


def layer_graph(graph):
    """Assign each node a depth, so the page can lay it out deterministically.

    Longest path from a source. A force simulation would settle differently on
    every timeline stop and make the diagrams impossible to compare, which is
    the entire point of showing them over time.
    """
    incoming = {n['id']: [] for n in graph['nodes']}
    ids = set(incoming)
    for edge in graph['edges']:
        if edge['to'] in ids and edge['from'] in ids:
            incoming[edge['to']].append(edge['from'])

    depth = {}

    def resolve(node_id, seen):
        if node_id in depth:
            return depth[node_id]
        if node_id in seen:
            return 0                                  # defensive: cycles
        seen = seen | {node_id}
        parents = incoming.get(node_id, [])
        value = 0 if not parents else 1 + max(resolve(p, seen) for p in parents)
        depth[node_id] = value
        return value

    for node in graph['nodes']:
        node['depth'] = resolve(node['id'], set())

    # The output belongs at the end of the chart whatever the arithmetic says.
    if graph['terminal'] is not None:
        deepest = max((n['depth'] for n in graph['nodes']), default=0)
        for node in graph['nodes']:
            if node['role'] == 'out':
                node['depth'] = deepest
    return graph


def graphs_for(source):
    """Every SynthDef in a file, as laid-out signal-flow graphs."""
    return [layer_graph(build_graph(sd)) for sd in find_synthdefs(source)]


def played_instrument(source):
    """The SynthDef named by the Pbind's \\instrument key, if there is one."""
    match = re.search(r'\\instrument\s*,\s*\\(\w+)', source)
    return match.group(1) if match else None


if __name__ == '__main__':
    import sys
    from pathlib import Path
    for path in sys.argv[1:]:
        text = Path(path).read_text(errors='replace')
        for graph in graphs_for(text):
            print(f"\n=== {Path(path).stem} : \\{graph['name']} "
                  f"({len(graph['nodes'])} nodes, {len(graph['edges'])} edges) ===")
            for node in sorted(graph['nodes'], key=lambda n: (n['depth'], n['id'])):
                flags = ' DENSE' if node['dense'] else ''
                print(f"  d{node['depth']}  {node['var']:12s} {node['label']:14s} "
                      f"{node['role']:12s} {node['rate']:8s}{flags}")
            print("  edges:", ' '.join(f"{e['from']}->{e['to']}" for e in graph['edges']))
