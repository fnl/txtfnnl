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

- `uimaFIT <http://code.google.com/p/uimafit/>`_ 1.4 for configuration and testing
- for making gene mention annotations (via the ``entities`` pipeline), a gnamed_ DB
  has to be available on the network, which in turn (by default) requires
  `PostgreSQL <http://www.postgresql.org/>`_ 8.4+; SQL-realted tests for txtfnnl
  furthermore use the `H2 <http://www.h2database.com/>`_ in-memory DB.
- for the **txtfnnl-wrappers** module, the relevant external tools need to be
  downloaded, installed, and visible on the system ``$PATH``.
  Supported external tools are listed in the section Installation.
  the `LinkGrammar <http://www.link.cs.cmu.edu/link/>`_ parser and
  the `GENIA Tagger <http://www.nactem.ac.uk/tsujii/GENIA/tagger/>`_
  for tokenization, lemmatization, tagging, and chunking.
- `BioLemmatizer <http://biolemmatizer.sourceforge.net/>`_ 1.1 in **txtfnnl-wrappers**

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
- ``entities`` annotates known entity mentions on documents by supplying a mapping
  of input file names (w/o sufffix) to entity identifiers (type, namespace,
  identifier), looking up the names for those entity IDs in a DB, and
  matching any of those names in the extracted plain-text. Example use: for
  gene mention annotations using gnamed_
- ``patterns`` extracts relationship patterns between named entities in a known
  relationship. A relationship is defined as one or more entity IDs (as for
  ``patterns``) together with the input file name and is supposed to be contained
  within a single sentence. If a sentence with all required entities is found,
  a number of patterns used to syntactically combine the entities are
  extracted. Each pattern is printed on a single line and patterns for
  different sentences are separated by an empty line.

License
-------

**txtfnnl** is governed by the
`Apache 2.0 License <http://www.apache.org/licenses/LICENSE-2.0.html>`_ -
see ``LICENSE.txt`` in this directory for details.

.. _gnamed: http://github.com/fnl/gnamed