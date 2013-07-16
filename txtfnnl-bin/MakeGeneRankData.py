#!/usr/bin/env python3
"""
Convert txtfnnl match, bioner, and norm output to RankLib input.
"""

import os
import sys
from collections import namedtuple, defaultdict

GREEK_ORDINALS = set(range(ord('Α'), ord('ω') + 1))

GREEK_LOWER = {
    "alpha": "α",
    "beta": "β",
    "gamma": "γ",
    "delta": "δ",
    "epsilon": "ε",
    "zeta": "ζ",
    "eta": "η",
    "theta": "θ",
    "iota": "ι",
    "kappa": "κ",
    "lambda": "λ",
    "mu": "μ",
    "nu": "ν",
    "xi": "ξ",
    "omicron": "ο",
    "pi": "π",
    "rho": "ρ",
    "sigma": "σ",
    "tau": "τ",
    "upsilon": "υ",
    "ypsilon": "υ",
    "phi": "φ",
    "chi": "χ",
    "psi": "ψ",
    "omega": "ω",
}

GREEK_UPPER = {
    "Alpha": "Α",
    "Beta": "Β",
    "Gamma": "Γ",
    "Delta": "Δ",
    "Epsilon": "Ε",
    "Zeta": "Ζ",
    "Eta": "Η",
    "Theta": "Θ",
    "Iota": "Ι",
    "Kappa": "Κ",
    "Lambda": "Λ",
    "Mu": "Μ",
    "Nu": "Ν",
    "Xi": "Ξ",
    "Omicron": "Ο",
    "Pi": "Π",
    "Rho": "Ρ",
    "Sigma": "Σ",
    "Tau": "Τ",
    "Upsilon": "Υ",
    "Ypsilon": "Υ",
    "Phi": "Φ",
    "Chi": "Χ",
    "Psi": "Ψ",
    "Omega": "Ω",
}

GREEK2LATIN = {v: k for k, v in GREEK_UPPER.items()}
GREEK2LATIN.update({v: k for k, v in GREEK_LOWER.items()})

# 0:before, 1:prefix, 2:match, 3:suffix, 4:after, 5:pos, 6:offset, 7:gid, 8:sim, 9:name, 10:taxon, 11:entrez
GENE_ITEMS = ["before", "prefix", "match", "suffix", "after", "pos", "offset", "gid", "sim", "name", "taxon", "entrez"]
OFFSET = 6
MENTION = 2
GID = 7
KEYS = [9, 10, 11]
INTEGERS = [7, 10, 11]
Gene = namedtuple('Gene', GENE_ITEMS)

def ParseGeneLines(filepath):
    for line in open(filepath):
        items = line.split('\t')
        try:
            start, end = items[OFFSET].split(":")
        except IndexError:
            print(filepath, file=sys.stderr)
            print(line, file=sys.stderr)
            print("offset:", items[OFFSET], file=sys.stderr)
            raise
        items[OFFSET] = (int(start), int(end))
        for i in KEYS:
            txt = items[i]
            items[i] = txt[txt.find('"') + 1:txt.rfind('"')]
        for i in INTEGERS:
            try:
                items[i] = int(items[i])
            except ValueError:
                print(filepath, file=sys.stderr)
                print(line, file=sys.stderr)
                print(items[i], i, file=sys.stderr)
                raise
        yield (items[GID], items[MENTION]), Gene(*items)

def ParseNerLines(filepath):
    # 0:match, 1:offset, 2:type 3:conf
    for line in open(filepath):
        items = line.split('\t')
        start, end = items[1].split(":")
        yield (int(start), int(end)), items[2]

def ParseTaxonLines(filepath):
    # 0:match, 1:offset, 2:tid 3:sim
    for line in open(filepath):
        items = line.split('\t')
        start, end = items[1].split(":")
        yield int(items[2]), (int(start), int(end))

def ParseGold(filepath):
    for line in open(filepath):
        file_id, entrez = line.split('\t')
        yield file_id, int(entrez)

def ParseLinkout(filepath):
    for line in open(filepath):
        gid, count = line.split()
        yield int(gid), int(count)

def ParseCounters(filepath):
    for line in open(filepath):
        try:
            gid, sym, refc, globc = line.split('\t')
        except ValueError:
            print(filepath, '"{}"'.format(line))
            raise
        yield int(gid), sym, int(refc), int(globc)

def JoinData(count, genes, taxa, entities, gold, links, symbols, references):
    sym_counts = defaultdict(set)
    for gid, data in genes.items():
        for sym, mentions in data.items():
            sym_counts[sym].update(m.offset for m in mentions)

    for gid, data in genes.items():
        count_GID = sum(len(m) for m in data.values())


        for sym, mentions in data.items():
            name = mentions[0].name
            count_SYM = len(sym_counts[sym])
            count_GIDSYM = len(mentions)
            count_links = links[gid] if gid in links else 0
            try:
                count_sym = symbols[name]
            except KeyError:
                if any(ord(i) in GREEK_ORDINALS for i in name):
                    replaced = []
                    for i in name:
                        if i in GREEK2LATIN:
                            replaced.append(GREEK2LATIN[i])
                        else:
                            replaced.append(i)
                    name = ''.join(replaced)
                    try:
                        count_sym = symbols[name]
                    except KeyError:
                        print('unknown name "{}" in mention "{}"'.format(name, gid), file=sys.stderr)
                        continue
                else:
                    print('unknown name "{}" in mention "{}"'.format(name, gid), file=sys.stderr)
                    continue
            try:
                count_refs = references[gid][name]
            except KeyError:
                if gid not in references:
                    print('unknown gid "{}" with name "{}"'.format(gid, name), file=sys.stderr)
                else:
                    print('unknown name "{}" for gid "{}"'.format(name, gid), file=sys.stderr)
                continue

            try:
                count_tids = len(taxa[mentions[0].taxon])
            except KeyError:
                print('unknown taxon "{}" in mention "{}"'.format(mentions[0].taxon, gid), file=sys.stderr)
                continue

            for mention in mentions:
                offset = mention.offset
                # distance tid <-> mention as 1/distance
                taxon_distance = 0
                for taxon_offset in taxa[mention.taxon]:
                    taxon_distance = min(taxon_distance,
                                         min(abs(offset[0] - taxon_offset[1]),
                                             abs(taxon_offset[0] - offset[1])))
                taxon_distance = 1.0 / taxon_distance

                # is mention an entity, and which?
                entity_type = None
                for entity_offset in entities:
                    if offset[0] >= entity_offset[0] and offset[1] <= entity_offset[1]:
                        entity_type = entities[entity_offset]
                        break

                e = [
                    1.0 if entity_type is None else 0.0,
                    1.0 if entity_type == 'cell_line' else 0.0,
                    1.0 if entity_type == 'cell_type' else 0.0,
                    1.0 if entity_type == 'DNA' else 0.0,
                    1.0 if entity_type == 'protein' else 0.0,
                    1.0 if entity_type == 'RNA' else 0.0,
                ]

                yield count, mention.entrez in gold, e[0], e[1], e[2], e[3], e[4], e[5], float(mention.sim), count_GID, count_SYM, count_GIDSYM, count_links, count_sym, count_refs, count_tids, taxon_distance
                count += 1

def WriteLines(result_generator):
    data = []
    for features in result_generator:
        data.append(features)
    r0 = data[0]
    for i, val in enumerate(r0):
        if type(val) is int and i != 0:
            m = max(r[i] for r in data)
            if m == 0:
                print("all vaules zero in position", i+1, file=sys.stderr)
                for r in data:
                    r[i] = 0.0
            else:
                for r in data:
                    r[i] /= m
    for r in data:
        print(int(r[1]), 'qid:{}'.format(r[0]), ' '.join('{}:{:.8f}'.format(i+1, f) for i, f in enumerate(r[2:])))

def Process(gene_dir, taxon_dir, ner_dir, gold_file, counter_file, linkout_file):
    gold = defaultdict(set)
    for article_id, entrez_id in ParseGold(gold_file):
        gold[article_id].add(entrez_id)
    print("parsed {} gold items".format(len(gold)), file=sys.stderr)

    link_counts = dict(ParseLinkout(linkout_file))
    print("parsed {} linkout items".format(len(link_counts)), file=sys.stderr)

    sym_counts = {}
    ref_counts = defaultdict(dict)
    for gid, sym, refc, symc in ParseCounters(counter_file):
        if sym not in sym_counts:
            sym_counts[sym] = symc
        ref_counts[gid][sym] = refc
    print("parsed {} refcount items".format(len(ref_counts)), file=sys.stderr)

    count = 1
    for filepath in os.listdir(gene_dir):
        article_id = filepath[:filepath.rfind('.')]

        if article_id not in gold:
            #print("skipping", article_id, file=sys.stderr)
            continue

        print("processing", article_id, file=sys.stderr)

        genes = {}
        for (gid, mention), gene in ParseGeneLines(os.path.join(gene_dir, filepath)):
            if gid in genes:
                if mention in genes[gid]:
                    genes[gid][mention].append(gene)
                else:
                    genes[gid][mention] = [gene]
            else:
                genes[gid] = {mention: [gene]}

        taxa = defaultdict(set)
        for tid, offset in ParseTaxonLines(os.path.join(taxon_dir, filepath)):
            taxa[tid].add(offset)

        entities = dict(ParseNerLines(os.path.join(ner_dir, filepath)))
        WriteLines(JoinData(count, genes, taxa, entities, gold[article_id], link_counts, sym_counts, ref_counts))
        count += sum(len(mentions) for mention_group in genes.values() for mentions in mention_group.values())


if __name__ == '__main__':
    # gene_dir, taxon_dir, ner_dir, gold_file, counter_file, refcount_file
    Process(*sys.argv[1:])
