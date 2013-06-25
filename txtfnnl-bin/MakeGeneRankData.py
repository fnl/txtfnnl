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
ENTREZ = 11
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
        yield items[ENTREZ], Gene(*items)

def ParseSentenceLines(filepath):
    # 0:match, 1:offset, 2:"Sentence" 3:conf
    for line in open(filepath):
        items = line.split('\t')
        start, end = items[1].split(":")
        yield (int(start), int(end)), float(items[3])

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

def JoinData(genes, taxa, sentences, gold):
    pass

def WriteLines(result_generator):
    pass

def Process(gene_dir, taxon_dir, sentence_dir, gold_file):
    genes = {}
    taxa = {}
    sentences = {}
    gold = dict(ParseGold(gold_file))

    for filepath in os.listdir(gene_dir):
        genes = dict(ParseGeneLines(os.path.join(gene_dir, filepath)))
        taxa = dict(ParseTaxonLines(os.path.join(taxon_dir, filepath)))
        sentences = list(ParseSentenceLines(os.path.join(sentence_dir, filepath)))
        WriteLines(JoinData(genes, taxa, sentences, gold))

if __name__ == '__main__':
    # gene_dir, taxon_dir, sentence_dir, gold_file
    Process(*sys.argv[1:])
