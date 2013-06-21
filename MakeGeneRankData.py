#!/usr/bin/env python
"""
Convert txtfnnl output from norm and match to rank data.
"""

import os
import sys
from collections import namedtuple

# 0:before, 1:prefix, 2:match, 3:suffix, 4:after, 5:pos, 6:offset, 7:gid, 8:name, 9:taxon, 10:entrez, 11:sim
GENE_ITEMS = ["before", "prefix", "match", "suffix", "after", "pos", "offset", "gid", "name", "taxon", "entrez", "sim"]
OFFSET = 6
INTEGERS = [7, 9, 10]
KEYS = [8, 9, 10]
Gene = namedtuple('Gene', GENE_ITEMS)

def ParseGeneLines(filepath):
    for line in open(filepath):
        items = line.split('\t')
        start, end = items[OFFSET].split(":")
        items[OFFSET] = tuple(int(start), int(end))
        for i in KEYS:
            txt = items[i]
            items[i] = txt[txt.find('"') + 1:txt.rfind('"')]
        for i in INTEGERS:
            items[i] = int(items[i])
        yield Gene(items)

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

def JoinData(genes, taxa, gold):
    pass

def WriteLines(result_generator):
    for tok, results in result_generator:
        print '\t'.join(tok),
        for r in results:
            print u'\t%s' % ','.join(r).strip(),
        print

def Process(gene_dir, taxon_dir, gold_file):
    genes = {}
    taxa = {}
    for filepath in os.listdir(gene_dir):
        genes[filepath] = list(ParseGeneLines(filepath))
    for filepath in os.listdir(taxon_dir):
        taxa[filepath] = dict(ParseTaxonLines(filepath))
    gold = dict(ParseGold(gold_file))
    WriteData(JoinData(genes, taxa, gold))

if __name__ == '__main__':
    # gene_dir, taxon_dir, gold_file
    Process(*sys.argv[1:])
