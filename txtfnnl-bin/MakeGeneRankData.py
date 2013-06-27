#!/usr/bin/env python3
"""
Convert txtfnnl output from norm and match to rank data.
"""

import os
import sys
from collections import namedtuple

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
        start, end = items[OFFSET].split(":")
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

def ParseSentenceLines(filepath):
    # 0:match, 1:offset, 2:"Sentence" 3:conf
    for line in open(filepath):
        items = line.split('\t')
        start, end = items[1].split(":")
        yield int(start), int(end)

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

def ParseLinkoutCount(filepath):
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

def JoinData(count, genes, taxa, sentences, gold, links, symbols, references):
    sym_counts = {}
    for gid, data in genes.items():
        for sym , mentions in data.items():
            if sym not in sym_counts:
                sym_counts[sym] = len(mentions)
            else:
                sym_counts[sym] += len(mentions)

    taxa_sentences = {}
    for tid, offsets in taxa.items():
        for toff in offsets:
            for sent in offsets:
                if toff[0] >= sent[0] and toff[1] <= sent[1]:
                    if tid not in taxa_sentences:
                        taxa_sentences[tid] = [sent]
                    else:
                        taxa_sentences[tid].append(sent)

    for gid, data in genes.items():
        count_GID = sum(len(m) for m in data.values())


        for sym, mentions in data.items():
            name = mentions[0].name
            count_SYM = sym_counts[sym]
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
            count_tids = 0
            if taxa:
                try:
                    count_tids = len(taxa[mentions[0].taxon])
                except KeyError:
                    print('unknown taxon "{}" in mention "{}"'.format(mentions[0].taxon, gid), file=sys.stderr)
                    continue
            tid_in_sent = 0
            if mentions[0].taxon in taxa_sentences:
                sent_offsets = taxa_sentences[mentions[0].taxon]
                for m in mentions:
                    if any(m.offset[0] >= s[0] and m.offset[1] <= s[1] for s in sent_offsets):
                        print('found taxon "{}" for gene "{}" in a sentence'.format(mentions[0].taxon, name), file=sys.stderr)
                        tid_in_sent = 1
                        break

            yield count, [mentions[0].entrez in gold, float(mentions[0].sim), count_GID, count_SYM, count_GIDSYM, count_links, count_sym, count_refs, count_tids, tid_in_sent]

def WriteLines(result_generator):
    data = []
    qid = None
    for qid, features in result_generator:
        data.append(features)
    r0 = data[0]
    for i, val in enumerate(r0):
        if type(val) is int:
            m = max(r[i] for r in data)
            if m == 0:
                print("all vaules zero in position", i+1, file=sys.stderr)
                for r in data:
                    r[i] = 0.0
            else:
                for r in data:
                    r[i] /= m
    for r in data:
        print(int(r[0]), 'qid:{}'.format(qid), ' '.join('{}:{:.8f}'.format(i+1, f) for i, f in enumerate(r[1:])))

def Process(gene_dir, taxon_dir, sentence_dir, gold_file, counter_file, linkoutcount_file):
    gold = {}

    for article_id, entrez_id in ParseGold(gold_file):
        if article_id in gold:
            gold[article_id].add(entrez_id)
        else:
            gold[article_id] = {entrez_id}

    print("parsed {} gold items".format(len(gold)), file=sys.stderr)

    link_counts = dict(ParseLinkoutCount(linkoutcount_file))

    print("parsed {} link items".format(len(link_counts)), file=sys.stderr)

    sym_counts = {}
    ref_counts = {}

    for gid, sym, refc, symc in ParseCounters(counter_file):
        if sym not in sym_counts:
            sym_counts[sym] = symc
        if gid not in ref_counts:
            ref_counts[gid] = {sym: refc}
        else:
            ref_counts[gid][sym] = refc

    print("parsed {} refcount items".format(len(ref_counts)), file=sys.stderr)
    count = 0

    for filepath in os.listdir(gene_dir):
        article_id = filepath[:filepath.rfind('.')]

        if article_id not in gold:
            #print("skipping", article_id, file=sys.stderr)
            continue

        print("processing", article_id, file=sys.stderr)
        genes = {}
        taxa = {}

        for (gid, mention), gene in ParseGeneLines(os.path.join(gene_dir, filepath)):
            if gid in genes:
                if mention in genes[gid]:
                    genes[gid][mention].append(gene)
                else:
                    genes[gid][mention] = [gene]
            else:
                genes[gid] = {mention: [gene]}

        for tid, offset in ParseTaxonLines(os.path.join(taxon_dir, filepath)):
            if tid in taxa:
                taxa[tid].add(offset)
            else:
                taxa[tid] = {offset}

        sentences = list(ParseSentenceLines(os.path.join(sentence_dir, filepath)))
        count += 1
        WriteLines(JoinData(count, genes, taxa, sentences, gold[article_id], link_counts, sym_counts, ref_counts))

if __name__ == '__main__':
    # gene_dir, taxon_dir, sentence_dir, gold_file, counter_file, refcount_file
    Process(*sys.argv[1:])
