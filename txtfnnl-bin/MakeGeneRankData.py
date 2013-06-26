#!/usr/bin/env python3
"""
Convert txtfnnl output from norm and match to rank data.
"""

import os
import sys
from collections import namedtuple

# 0:before, 1:prefix, 2:match, 3:suffix, 4:after, 5:pos, 6:offset, 7:gid, 8:sim, 9:name, 10:taxon, 11:entrez
GENE_ITEMS = ["before", "prefix", "match", "suffix", "after", "pos", "offset", "gid", "sim", "name", "taxon", "entrez"]
OFFSET = 6
MENTION = 2
GID = 11
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
        gid, count = line.split('\t')
        yield int(gid), int(count)

def ParseCounters(filepath):
    for line in open(filepath):
        try:
            gid, sym, refc, globc = line.split('\t')
        except ValueError:
            print(filepath, '"{}"'.format(line))
            raise
        yield int(gid), sym, int(refc), int(globc)

def JoinData(genes, taxa, sentences, gold, links, symbols, references):
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
            count_SYM = sym_counts[sym]
            count_GIDSYM = len(mentions)
            count_links = links[gid] if gid in links else 0
            count_sym = symbols[sym]
            count_refs = references[gid][sym]
            count_tids = len(taxa[mentions.taxon])
            tid_in_sent = 0
            if mentions[0].taxon in taxa_sentences:
                sent_offsets = taxa_sentences[mentions[0].taxon]
                for m in mentions:
                    if any(m.offset[0] >= s[0] and m.offset[1] <= s[1] for s in sent_offsets):
                        tid_in_sent = 1
                        break
            yield gid in gold, counter(), (count_GID, count_SYM, count_GIDSYM, count_links, count_sym, count_refs, count_tids, tid_in_sent)

def WriteLines(result_generator):
    for is_hit, qid, features in result_generator:
        print(int(is_hit), 'qid:{}'.format(qid), ' '.join('{}:{}'.format(i+1, f) for f in enumerate(features)))

def Process(gene_dir, taxon_dir, sentence_dir, gold_file, counter_file, linkoutcount_file):
    gold = dict(ParseGold(gold_file))
    link_counts = dict(ParseLinkoutCount(linkoutcount_file))
    sym_counts = {}
    ref_counts = {}

    for gid, sym, refc, symc in ParseCounters(counter_file):
        if sym not in sym_counts:
            sym_counts[sym] = symc
        if gid not in ref_counts:
            ref_counts[gid] = {sym: refc}
        else:
            ref_counts[gid][sym] = refc

    for filepath in os.listdir(gene_dir):
        article_id = filepath[:filepath.rfind('.')]

        if article_id not in gold:
            continue

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
                tid.add(offset)
            else:
                taxa[tid] = {offset}

        sentences = list(ParseSentenceLines(os.path.join(sentence_dir, filepath)))
        WriteLines(JoinData(genes, taxa, sentences, gold[article_id], link_counts, sym_counts, ref_counts))

if __name__ == '__main__':
    # gene_dir, taxon_dir, sentence_dir, gold_file, counter_file, refcount_file
    Process(*sys.argv[1:])
