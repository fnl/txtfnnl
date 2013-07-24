#!/usr/bin/env python3

import os.path
import sys

from collections import namedtuple
from contextlib import closing


class COLUMN:
    'columns (0-based) in the txtfnnl output'
    POS_TAG = 5
    OFFSET = 6
    TAX_ID = -2
    UNIQUE_ID = -1


class DATABASE:
    'gnamed access variables'
    NAME = 'gnamed_tmp'
    HOST = 'padme'


class CUTOFF:
    'optimization parameters'
    AFFIX = 1.1
    WORD = 1.2
    TOKEN = 1.2


POS_TAGS = frozenset({'NN', 'JJ', 'NNP', 'NNS', 'DT', 'NULL'})

Annotation = namedtuple('Annotation', 'before prefix token suffix after pos offset')
Tokens = namedtuple('Tokens', 'before prefix token suffix after')
Counters = namedtuple('Counters', 'tp fp fn')


def yieldLines(filepath):
    lno = 0
    line = None

    with open(filepath) as handle:
        try:
            for line in handle:
                lno += 1
                yield line[:-1] if line[-1] == '\n' else line
        except Exception as e:
            print(e, 'on line', str(lno), 'while reading', filepath, file=sys.stderr)
            print(line, file=sys.stderr)
            raise e


def yieldResults(filepath, tags=None):
    for line in yieldLines(filepath):
        items = line.split('\t')

        if tags is None or items[COLUMN.POS_TAG] in tags:
            ann = Annotation(*items[:COLUMN.OFFSET + 1])
            tax = int(items[COLUMN.TAX_ID][len('taxId="'):-1])
            idx = items[COLUMN.UNIQUE_ID].index('=')
            uni = items[COLUMN.UNIQUE_ID][idx + 2:-1] # uni="...", gi="..." etc
            yield uni, tax, ann


def parseResults(filepath, tags=None):
    results = {}
    last = None
    items = None

    for uni, tax, ann in yieldResults(filepath, tags):
        if last != ann:
            last = ann

            if ann not in results:
                results[ann] = items = []
            else:
                items = results[ann]

        items.append(uni)

    return results


def parseStandard(filepath):
    data = {}

    for line in yieldLines(filepath):
        article, uni = line.split('\t')

        if article not in data:
            data[article] = set()

        data[article].add(uni)

    return data


def countHits(isHit, ids, current):
    if isHit:
        for i in ids:
            if i in current.fn:
                current.fn.remove(i)
                current.tp.add(i)
    else:
        current.fp.update(ids)


def isFiltered(ann, filters):
    return any(getattr(ann, field) in getattr(filters, field) for field in Tokens._fields)


def updateCounter(counter, sets):
    counter.fn.append(len(sets.fn))
    counter.fp.append(len(sets.fp))
    counter.tp.append(len(sets.tp))


def logPerformance(kind, count):
    sum_tp = sum(count.tp)
    p = sum_tp / (sum(count.fp) + sum_tp) if sum_tp > 0 else 0.0
    r = sum_tp / (sum(count.fn) + sum_tp) if sum_tp > 0 else 0.0
    f = 2 * p * r / (p + r) if (p + r) > 0 else 0.0
    print(kind, 'filter: p={:.3f} r={:.3f} f={:.3f} (tp={}, fn={}, fp={})'.format(
        p * 100, r * 100, f * 100, sum_tp, sum(count.fn), sum(count.fp)
    ), file=sys.stderr)


try:
    print('parsing DEV from', sys.argv[1], file=sys.stderr)
except IndexError:
    print('usage: {} DEV GOLD FILE...'.format(__file__))
    sys.exit(0)

DEV = parseStandard(sys.argv[1])
print('parsing GOLD from', sys.argv[2], file=sys.stderr)
GOLD = parseStandard(sys.argv[2])
FILES = sys.argv[3:]
NUM_ART = 0
TOKENS = Tokens({}, {}, {}, {}, {})
COUNT = Counters([], [], [])
COUNT_FILTERED = Counters([], [], [])
FILTER = Tokens(set(), set(), set(), set(), set())

for fp in FILES:
    article = os.path.splitext(os.path.basename(fp))[0]
    if article not in DEV: continue
    NUM_ART += 1
    hits = DEV[article]
    results = parseResults(fp, POS_TAGS)

    for Tokens, ids in results.items():
        i = 0 if any(i in hits for i in ids) else 1

        if Tokens.before not in TOKENS.before:
            TOKENS.before[Tokens.before] = [0, 0]
        TOKENS.before[Tokens.before][i] += 1
        if Tokens.prefix not in TOKENS.prefix:
            TOKENS.prefix[Tokens.prefix] = [0, 0]
        TOKENS.prefix[Tokens.prefix][i] += 1
        if Tokens.token not in TOKENS.token:
            TOKENS.token[Tokens.token] = [0, 0]
        TOKENS.token[Tokens.token][i] += 1
        if Tokens.suffix not in TOKENS.suffix:
            TOKENS.suffix[Tokens.suffix] = [0, 0]
        TOKENS.suffix[Tokens.suffix][i] += 1
        if Tokens.after not in TOKENS.after:
            TOKENS.after[Tokens.after] = [0, 0]
        TOKENS.after[Tokens.after][i] += 1

print('parsed', NUM_ART, 'DEV articles', file=sys.stderr)

for counts, rel in [(TOKENS.before, 'before'),
                    (TOKENS.prefix, 'prefix'),
                    (TOKENS.suffix, 'suffix'),
                    (TOKENS.after, 'after')]:
    if rel in ('before', 'after'):
        cutoff = CUTOFF.WORD
    elif rel in ('prefix', 'suffix'):
        cutoff = CUTOFF.AFFIX
    else:
        raise RuntimeError('rel ' + rel + ' unknown')

    for tok, (hits, misses) in counts.items():
        if hits and misses:
            if misses / hits >= cutoff:
                getattr(FILTER, rel).add(tok)
        elif misses >= cutoff:
            getattr(FILTER, rel).add(tok)

# "uncount" tokens that are filtered by the surrounding
for fp in FILES:
    article = os.path.splitext(os.path.basename(fp))[0]
    if article not in DEV: continue
    hits = DEV[article]
    results = parseResults(fp, POS_TAGS)

    for Tokens, ids in results.items():
        if not any(i in hits for i in ids):
            if Tokens.before in FILTER.before or \
                            Tokens.prefix in FILTER.prefix or \
                            Tokens.suffix in FILTER.suffix or \
                            Tokens.after in FILTER.after:
                if TOKENS.token[Tokens.token][1] > 0:
                    TOKENS.token[Tokens.token][1] -= 1

for tok, (hits, misses) in TOKENS.token.items():
    if hits and misses:
        if misses / hits >= CUTOFF.TOKEN:
            FILTER.token.add(tok)
    elif misses >= CUTOFF.TOKEN:
        FILTER.token.add(tok)

NUM_ART = 0

# measure un-/filtered performance on gold articles
for fp in FILES:
    article = os.path.splitext(os.path.basename(fp))[0]
    if article not in GOLD: continue
    NUM_ART += 1
    hits = GOLD[article]
    results = parseResults(fp, POS_TAGS)
    current = Counters(set(), set(), set(hits))
    current_filtered = Counters(set(), set(), set(hits))

    for ann, ids in results.items():
        countHits(any(i in hits for i in ids), ids, current)

        if not isFiltered(ann, FILTER):
            countHits(any(i in hits for i in ids), ids, current_filtered)

    updateCounter(COUNT, current)
    updateCounter(COUNT_FILTERED, current_filtered)

print('parsed', NUM_ART, 'GOLD articles', file=sys.stderr)
logPerformance('post', COUNT_FILTERED)
logPerformance('pre', COUNT)

# measure performance of each token position of GOLD
for field in Tokens._fields:
    spec_filter = Tokens(set(), set(), set(), set(), set())
    spec_filter = spec_filter._replace(**{field: getattr(FILTER, field)})
    COUNT = Counters([], [], [])

    for fp in FILES:
        article = os.path.splitext(os.path.basename(fp))[0]
        if article not in GOLD: continue
        hits = GOLD[article]
        results = parseResults(fp, POS_TAGS)
        current = Counters(set(), set(), set(hits))

        for ann, ids in results.items():
            if not isFiltered(ann, spec_filter):
                countHits(any(i in hits for i in ids), ids, current)

        updateCounter(COUNT, current)

    logPerformance(field, COUNT)

for field in FILTER._fields:
    for val in getattr(FILTER, field):
        print(field, val, sep='\t')
