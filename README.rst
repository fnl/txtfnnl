=======
txtfnnl 
=======

text-funnel :: ``/tɛkstˈfʌn.əl/``

Introduction
------------

A text mining framework encapsulating content extraction, language processing
and content analysis functionality.

Dependencies
------------

**txtfnnl** currently integrates the following Apache projects:

- `Maven <http://maven.apache.org>`_ 2.2
- `Tika <http://tika.apache.org>`_ 1.1
- `UIMA <http://uima.apache.org>`_ 2.4
- `OpenNLP <http://opennlp.apache.org>`_ 1.5
  
In addition, the following direct dependencies exist:

- `uimaFIT <http://code.google.com/p/uimafit/>`_ 1.4 for configuration and
  testing
- for making gene mention annotations (via the ``entities`` pipeline), a
  gnamed_ DB has to be available on the network, which in turn (by default)
  requires `PostgreSQL <http://www.postgresql.org/>`_ 8.4+; SQL-realted tests
  for txtfnnl furthermore use the `H2 <http://www.h2database.com/>`_ in-memory
  DB.
- for the syntactic grep facilities (via the ``grep`` pipeline), libfsmg_ has
  to be in your local Maven repository. 
- for the **txtfnnl-wrappers** module, the relevant external tools need to be
  downloaded, installed, and visible on the system ``$PATH``.
  Supported external tools are listed in the section Installation below.
- `BioLemmatizer <http://biolemmatizer.sourceforge.net/>`_ 1.1 in
  **txtfnnl-wrappers**

Installation
------------

Before installing **txtfnnl** itself, the additional (independent) tools
should be installed. The following NLP tools are supported by **txtfnnl**:

`LinkGrammar <http://www.abisource.com/projects/link-grammar/>`_
  After downloading, unpacking, building, and installation (usually, just a
  curl-tar-configure-make-install loop) and assuming the default installation
  into ``/usr/local``, nothing else needs to be configured (parser version
  known to work with this AE wrapper: 4.7.6).
 
`GENIA Tagger <http://www.nactem.ac.uk/tsujii/GENIA/tagger/>`_
  The GENIA Tagger does not follow GNU Autotools' best practices, so
  after downloading, unpacking, and compiling you need to make sure that the
  ``geniatagger`` executable is on your ``$PATH``. Furthermore, you should
  put the whole directory that contains the ``morphdic`` directory somewhere
  you can remember: each time you want to use the GENIA Tagger, you will
  have to add the directory containing the ``morphdic`` directory as an
  argument. A sensible place for the tagger directory might be
  ``/usr/local/share/geniatagger`` if you have write access to it.
 
libfmsg_
  A generic finite state machine library developed by the principal author
  of the ``txtfnnl`` framework. Clone from github (``git clone
  git://github.com/fnl/libfsmg.git``), and run ``mvn install``
  in the newly created ``libsfsmg`` directory to install. 

All Java dependencies should be resolved by Maven (if you have a working
Internet connection). To "install" **txtfnnl** itself, execute ``mvn install``
in the TLD. **txtfnnl** is known to work on Apple OSX, Ubuntu and CentOS.
The framework requires the use of Java 1.5 or later (tested on 1.5 and 1.6).

After installing the Maven project, the ``txtfnnl`` shell script in the
**txtfnnl-bin** module can be put anywhere on the system ``$PATH``.

Usage
-----

To use the pipelines from the command line, execute the ``txtfnnl`` script in
the **txtfnnl-bin** module directory (or copy it to your ``$PATH``).
The script expects to find the local Maven repository either in
``~/.m2/repository`` or otherwise defined as the environment variable 
``$M2_REPO``.

Currently, the following pipelines are available:

- ``split`` splits any kind of data Tika can extract plain-text from into 
  sentences, one per line.
- ``pre`` pre-processes any kind of data Tika can extract, generating XMI files
  with sentence, token, and chunk annotations. The tokens are PoS tagged and
  lemmatized. 
- ``tag`` works just as ``pre``, but outputs the content in plaintext format
  instead of XMI. 
- ``grep`` enables the use of syntax patterns (written in a style similar to
  the output of ``tag``) to annotate semantic entities and relationships
  between those entities.
  In other words, this pipeline provides a regular syntax expression language
  for matching token sequences and their part-of-speech, chunk tags, and lemmas
  in UIMA. This is a functionality similar to that provided by GATE's
  `JAPE <http://gate.ac.uk/wiki/jape-repository/>`_, but a much simpler grammar
  with far less features. 
- ``entities`` annotates known entity mentions on documents by supplying a
  mapping of input file names (w/o sufffix) to entity identifiers (type,
  namespace, identifier), looking up the names for those entity IDs in a DB,
  and matching any of those names in the extracted plain-text. Example use: for
  gene mention annotations using gnamed_
- ``patterns`` extracts relationship patterns between named entities in a known
  relationship. A relationship is defined as one or more entity IDs (as for
  ``patterns``) together with the input file name and is supposed to be
  contained within a single sentence. If a sentence with all required entities
  is found, a number of patterns used to syntactically combine the entities are
  extracted. Each pattern is printed on a single line and patterns for
  different sentences are separated by an empty line.

A quick reference of the CF regular expression grammar for syntax patterns::

  S -> Capture S? | Phrase S? | Token S?
  Capture -> "(" S ")" # => Semantic Relationship Annotations
  
  Token -> "." Quantifier? | RegEx Quantifier? # dot "." matches any token
  Quantifier -> "*" | "?" | "+" # zero-or-more, zero-or-one, one-or-more
  
  Phrase -> "[" Chunk InPhrase "]" "?"? # may be skipped with "?"
  Chunk -> "NP" | "VP" | "PP" | "ADVP" ... # i.e., a chunker tag
  InPhrase -> CaptureInPhrase InPhrase? | Token InPhrase?
  CaptureInPhrase -> "(" InPhrase ")" # => Semantic Relationship Ann.
  # InPhrase and CaptureInPhrase ensure that phrases are never nested
  
  RegEx -> RE1 | RE2 | RE3 # token annotation-specific matching
  RE1 -> "<lemma>" # a Java regex used to match the token's lemma
  RE2 -> "<PoS>_<lemma>" # as RE1, two regex patterns separated by underscore
  RE3 -> "<word>_<PoS>_<lemma>" # idem, w/ 3 patterns (PoS = Part-of-Speech)
  # to allow any match for a word, PoS or lemma annotation in RE2 or RE3:
  #    use a "*" in stead of the corresponding regex, e.g.: "IN_*" 
  # all terminals must be separated by white-spaces

An example line in a pattern resource file that will annotate relationships
between two entities: the first entity is a noun phrase with a head lemma of
gene or protein, any number of tokens, a verb phrase with a head lemma of
bind, and optional IN-preposition, and the second entity, which may be any
other noun phrase::

  [ NP DT_* ? ( . + ) gene|protein|factor ] . * [ VP . * bind ] IN_* ? [ NP DT_* ? ( . + ) ]  interaction PPI actor   source    actor target
  
After the pattern, separated by tabs, the annotations are specified: a match
will result in a RelationshipAnnotation with namespace "interaction" and ID
"PPI" between the matched entities, which are annotated as SemanticAnnotations
with namespace "actor", IDs "source" and "target", respectively. I.e., the
first namespace-ID-pair defines the relationship annotation, all following
pairs should correspond with the number of capture groups in the pattern and
define the semantic (entity) annotations that should be made.

License, Author and Copyright Notice
------------------------------------

**txtfnnl** is free, open software provided via a
`Apache 2.0 License <http://www.apache.org/licenses/LICENSE-2.0.html>`_ -
see ``LICENSE.txt`` in this directory for details.

Copyright 2012, 2013 - Florian Leitner (fnl). All rights reserved.

.. _gnamed: http://github.com/fnl/gnamed