#!/usr/bin/env python3

import os.path
import sys

from collections import namedtuple, defaultdict
from contextlib import closing

# columns (0-based) in the txtfnnl output
POS_TAG = 5
OFFSET = 6
TAX_ID = -2
UNIQUE_ID = -1

DATABASE = 'gnamed_tmp'
POSTGRES_HOST = 'padme'

AFFIX_CUTOFF = 1.1
WORD_CUTOFF = 1.2
TOKEN_CUTOFF = 1.2

POS_TAGS = frozenset({'NN', 'JJ', 'NNP', 'NNS', 'DT'})

annotation = namedtuple("annotation", "before prefix token suffix after pos offset")
counters = namedtuple('counters', 'tp fp fn')

def ParseStandard(filepath):
    data = {}

    with open(filepath) as handle:
        try:
            for line in handle:
                article, uni = line.split('\t')

                if article not in data:
                    data[article] = set()

                data[article].add(uni.strip())
        except Exception as e:
            print(line, file=sys.stderr)
            raise e

    return data

def YieldResults(filepath, tags=None):
    data = defaultdict(list)
    lno = 0

    with open(filepath) as handle:
        try:
            for line in handle:
                lno += 1
                items = line[:-1].split('\t')

                if tags is None or items[POS_TAG] in tags:
                    ann = annotation(*items[:OFFSET+1])
                    tax = int(items[TAX_ID][len('taxId="'):-1])
                    idx = items[UNIQUE_ID].index('=')
                    uni = items[UNIQUE_ID][idx+2:-1] # uni="...", gi="..." etc
                    yield uni, tax, ann
        except Exception as e:
            print(e, 'on line', str(lno), file=sys.stderr)
            print(line, file=sys.stderr)
            raise e

def ParseResults(filepath, tags=None):
    results = {}
    last = None
    items = None

    for uni, tax, ann in YieldResults(fp, tags):
        if last != ann:
            last = ann

            if ann not in results:
                items = []
                results[ann] = items
            else:
                items = results[ann]

        items.append(uni)

    return results

def CreateSpeciesMappingFunction():
    import psycog2
    with closing(psycopg2.connect(database=DATABASE, host=POSTGRES_HOST)) as conn:
        cur = conn.cursor()
        def GetTaxId(gene_id):
            cur.execute("select genes.species_id from genes inner join gene_refs using(id) where gene_refs.namespace = 'gi' and gene_refs.accession = '%s'", (gene_id,))
            result = cur.fetchone()
            return None if result == None else result[0]
        return GetTaxId

def CountHits(isHit, ids, current):
    if isHit:
        for i in ids:
            if i in current.fn:
                current.fn.remove(i)
            if i in hits:
                current.tp.add(i)
    else:
        current.fp.update(ids)

def Filtered(ann, filters):
    return any(getattr(ann, field) in filters[field] for field in ('before', 'prefix', 'token', 'suffix', 'after'))

def UpdateCounter(counter, sets):
    counter.fn.append(len(sets.fn))
    counter.fp.append(len(sets.fp))
    counter.tp.append(len(sets.tp))

def LogPerformance(kind, count):
    sum_tp = sum(count.tp)
    p = sum_tp / (sum(count.fp) + sum_tp) if sum_tp > 0 else 0.0
    r = sum_tp / (sum(count.fn) + sum_tp) if sum_tp > 0 else 0.0
    f = 2 * p * r / (p + r) if (p + r) > 0 else 0.0
    print(kind, 'filter: p={:.5f} r={:.5f} f={:.5f} (tp={}, fn={}, fp={})'.format(p, r, f, sum_tp, sum(count.fn), sum(count.fp)), file=sys.stderr)

DEV = ParseStandard(sys.argv[1])
print('DEV', sys.argv[1], file=sys.stderr)
GOLD = ParseStandard(sys.argv[2])
print('GOLD', sys.argv[2], file=sys.stderr)
FILES = sys.argv[3:]
BEFORE = {}
PREFIX = {}
SUFFIX = {}
AFTER = {}
TOKEN = {}
COUNT = counters([], [], [])

for fp in FILES:
    article = os.path.splitext(os.path.basename(fp))[0]
    if article not in DEV: continue
    hits = DEV[article]
    results = ParseResults(fp, POS_TAGS)

    for tokens, ids in results.items():
        i = 0 if any(i in hits for i in ids) else 1

        if tokens.before not in BEFORE:
            BEFORE[tokens.before] = [0, 0]
        BEFORE[tokens.before][i] += 1
        if tokens.prefix not in PREFIX:
            PREFIX[tokens.prefix] = [0, 0]
        PREFIX[tokens.prefix][i] += 1
        if tokens.token not in TOKEN:
            TOKEN[tokens.token] = [0, 0]
        TOKEN[tokens.token][i] += 1
        if tokens.suffix not in SUFFIX:
            SUFFIX[tokens.suffix] = [0, 0]
        SUFFIX[tokens.suffix][i] += 1
        if tokens.after not in AFTER:
            AFTER[tokens.after] = [0, 0]
        AFTER[tokens.after][i] += 1

FILTER = {}
FILTER['before']= set()
FILTER['prefix'] = set()
FILTER['suffix'] = set()
FILTER['after'] = set()
FILTER['token'] = set()

for counts, rel in [(BEFORE, 'before'),
                    (PREFIX, 'prefix'),
                    (SUFFIX, 'suffix'),
                    (AFTER, 'after')]:
    cutoff = {
            'before': WORD_CUTOFF,
            'after': WORD_CUTOFF,
            'prefix': AFFIX_CUTOFF,
            'suffix': AFFIX_CUTOFF
        }[rel]

    for t, (hits, misses) in counts.items():
        if hits and misses:
            if misses/hits >= cutoff:
                FILTER[rel].add(t)
        elif misses >= cutoff:
            FILTER[rel].add(t)

# "uncount" tokens that are filtered by the surrounding
COUNT_ART = 0
for fp in FILES:
    article = os.path.splitext(os.path.basename(fp))[0]
    if article not in DEV: continue
    COUNT_ART += 1
    hits = DEV[article]
    results = ParseResults(fp, POS_TAGS)

    for tokens, ids in results.items():
        if not any(i in hits for i in ids):
            if tokens.before in FILTER['before'] or \
               tokens.prefix in FILTER['prefix'] or \
               tokens.suffix in FILTER['suffix'] or \
               tokens.after in FILTER['after']:
                if TOKEN[tokens.token][1] > 0:
                    TOKEN[tokens.token][1] -= 1

print('parsed', COUNT_ART, 'DEV articles', file=sys.stderr)

for t, (hits, misses) in TOKEN.items():
    if hits and misses:
        if misses/hits >= TOKEN_CUTOFF:
            FILTER['token'].add(t)
    elif misses >= TOKEN_CUTOFF:
        FILTER['token'].add(t)

COUNT_UN = counters([], [], [])
COUNT_FI = counters([], [], [])
COUNT_ART = 0

for fp in FILES:
    article = os.path.splitext(os.path.basename(fp))[0]
    if article not in GOLD: continue
    COUNT_ART += 1
    hits = GOLD[article]
    results = ParseResults(fp, POS_TAGS)
    current_un = counters(set(), set(), set(hits))
    current_fi = counters(set(), set(), set(hits))

    for ann, ids in results.items():
        CountHits(any(i in hits for i in ids), ids, current_un)

        if not Filtered(ann, FILTER):
            CountHits(any(i in hits for i in ids), ids, current_fi)

    UpdateCounter(COUNT_UN, current_un)
    UpdateCounter(COUNT_FI, current_fi)

print('parsed', COUNT_ART, 'GOLD articles', file=sys.stderr)
LogPerformance('post', COUNT_FI)
LogPerformance('pre', COUNT_UN)

for field in ('before', 'after', 'prefix', 'suffix', 'token'):
    spec_filter = {
        'before': set(),
        'prefix': set(),
        'token': set(),
        'suffix': set(),
        'after': set()
    }
    spec_filter[field] = FILTER[field]
    COUNT = counters([], [], [])

    for fp in FILES:
        article = os.path.splitext(os.path.basename(fp))[0]
        if article not in GOLD: continue
        hits = GOLD[article]
        results = ParseResults(fp, POS_TAGS)
        current = counters(set(), set(), set(hits))

        for ann, ids in results.items():
            if not Filtered(ann, spec_filter):
                CountHits(any(i in hits for i in ids), ids, current)

        UpdateCounter(COUNT, current)

    LogPerformance(field, COUNT)

for field, values in FILTER.items():
    for val in values:
        print(field, val, sep='\t')
