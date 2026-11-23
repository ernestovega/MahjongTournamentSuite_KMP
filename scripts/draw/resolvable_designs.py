#!/usr/bin/env python3
"""
Finds the perfect patterns of 28 and 40 players kept in PerfectPattern.kt (docs/draw.md): rounds of tables of 4 where
every pair of players meets exactly once.

- 28: the 28 points of the Hermitian curve x^4 + y^4 + z^4 = 0 of the projective plane over GF(9); its secant lines
  meet it in 4 points (63 blocks).
- 40: the 40 points of the projective space PG(3, 3); its 130 lines have 4 points each.

In both, two points share exactly one block. The script splits the blocks into (N - 1) / 3 rounds that seat every
point once (an exact cover, Knuth's Algorithm X) and prints them as JSON: rounds, tables, positions 0..N-1.

    python3 scripts/draw/resolvable_designs.py 28 [seed]
    python3 scripts/draw/resolvable_designs.py 40 [seed]

It takes less than a second. Paste the output into PerfectPattern.kt; PerfectPatternTest checks it.
"""
import itertools, random, sys, json

def solve_x(X, Y, solution, limit_nodes=[0]):
    if not X:
        yield list(solution); return
    c = min(X, key=lambda c: len(X[c]))
    rows = list(X[c]); random.shuffle(rows)
    for r in rows:
        solution.append(r)
        cols = select(X, Y, r)
        for s in solve_x(X, Y, solution): yield s
        deselect(X, Y, r, cols)
        solution.pop()

def select(X, Y, r):
    cols = []
    for j in Y[r]:
        for i in X[j]:
            for k in Y[i]:
                if k != j: X[k].remove(i)
        cols.append(X.pop(j))
    return cols

def deselect(X, Y, r, cols):
    for j in reversed(Y[r]):
        X[j] = cols.pop()
        for i in X[j]:
            for k in Y[i]:
                if k != j: X[k].add(i)

def resolve(points, blocks, classes):
    Y = {}
    for b, block in enumerate(blocks):
        for c in range(classes):
            if b == 0 and c != 0: continue  # symmetry
            Y[(b, c)] = [('L', b)] + [('P', p, c) for p in block]
    X = {}
    for r, cols in Y.items():
        for col in cols: X.setdefault(col, set()).add(r)
    for sol in solve_x(X, Y, []):
        out = [[] for _ in range(classes)]
        for b, c in sol: out[c].append(sorted(blocks[b]))
        return out

def pg33():
    pts = []
    for v in itertools.product(range(3), repeat=4):
        if any(v):
            first = next(x for x in v if x)
            if first == 1: pts.append(v)
    idx = {p: i for i, p in enumerate(pts)}
    def norm(v):
        first = next(x for x in v if x); inv = 1 if first == 1 else 2
        return tuple((x * inv) % 3 for x in v)
    lines = set()
    for a, b in itertools.combinations(pts, 2):
        line = set()
        for s, t in itertools.product(range(3), repeat=2):
            v = tuple((s * x + t * y) % 3 for x, y in zip(a, b))
            if any(v): line.add(idx[norm(v)])
        lines.add(frozenset(line))
    return 40, [sorted(l) for l in lines]

# GF(9) = GF(3)[i]/(i^2+1): element (a,b) = a + b i
def gf9():
    els = [(a, b) for a in range(3) for b in range(3)]
    add = lambda x, y: ((x[0] + y[0]) % 3, (x[1] + y[1]) % 3)
    mul = lambda x, y: ((x[0] * y[0] - x[1] * y[1]) % 3, (x[0] * y[1] + x[1] * y[0]) % 3)
    return els, add, mul

def unital28():
    els, add, mul = gf9()
    zero, one = (0, 0), (1, 0)
    def pw(x, n):
        r = one
        for _ in range(n): r = mul(r, x)
        return r
    pts = []
    for v in itertools.product(els, repeat=3):
        if any(c != zero for c in v):
            first = next(c for c in v if c != zero)
            if first == one: pts.append(v)
    def herm(v):
        s = zero
        for c in v: s = add(s, pw(c, 4))
        return s
    U = [p for p in pts if herm(p) == zero]
    assert len(U) == 28, len(U)
    idx = {p: i for i, p in enumerate(U)}
    def norm(v):
        first = next(c for c in v if c != zero)
        inv = next(x for x in els if mul(x, first) == one)
        return tuple(mul(c, inv) for c in v)
    blocks = set()
    for a, b in itertools.combinations(U, 2):
        line = set()
        for s, t in itertools.product(els, repeat=2):
            v = tuple(add(mul(s, x), mul(t, y)) for x, y in zip(a, b))
            if any(c != zero for c in v):
                n = norm(v)
                if n in idx: line.add(idx[n])
        blocks.add(frozenset(line))
    blocks = [sorted(b) for b in blocks]
    assert all(len(b) == 4 for b in blocks), set(len(b) for b in blocks)
    return 28, blocks

which = sys.argv[1]
random.seed(int(sys.argv[2]) if len(sys.argv) > 2 else 1)
n, blocks = pg33() if which == '40' else unital28()
print(n, len(blocks), file=sys.stderr)
res = resolve(n, blocks, (n - 1) // 3)
print(json.dumps(res))
