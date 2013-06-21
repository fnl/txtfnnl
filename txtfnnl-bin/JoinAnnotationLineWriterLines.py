#!/usr/bin/env python
"""Convert txtfnnl AnnotationLineWriter output into a single line per annotated string.

Consecutive output lines with the same tokens are joined together,
writing each token content and a tab-separated list of annotations on that
set of tokens. Takes the file to process as input argument and prints
the joined results.
"""

import os
import sys
from collections import namedtuple

def ParseLines(filepath, col=7):
    for line in open(filepath):
        items = line.split('\t')
        yield tuple(items[0:col]), items[col:]

def JoinLines(line_generator):
    last = None
    results = []
    for tok, res in line_generator:
        if last != tok:
            if last is not None:
                yield last, results
            last = tok
            results = [res]
        else:
            results.append(res)
    if last is not None:
        yield last, results

def WriteLines(result_generator):
    for tok, results in result_generator:
        print '\t'.join(tok),
        for r in results:
            print u'\t%s' % ','.join(r).strip(),
        print

def Process(filename):
    WriteLines(JoinLines(ParseLines(filename)))

if __name__ == '__main__':
    Process(sys.argv[1])
