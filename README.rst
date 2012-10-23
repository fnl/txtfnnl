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

- `uimaFIT <http://code.google.com/p/uimafit/>`_ 1.4 for testing and the
  **txtfnnl-bin** module
- for making gene mention annotations (via the ``ema`` pipeline), a gnamed_ DB
  has to be available on the network, which in turn (by default) requires
  `PostgreSQL <http://www.postgresql.org/>`_ 8.4+; the tests for the entity
  annotator of txtfnnl furthermore use the `H2 <http://www.h2database.com/>`_
  in-memory DB.
- for the **txtfnnl-parsers** module, the relevant parsers need to be
  downloaded, installed, and visible on the system ``$PATH``.
  Right now, the only supported parser is
  `LinkGrammar <http://www.link.cs.cmu.edu/link/>`_
- for the lemmatization in **txtfnnl-uima**, the currently supplied tool is
  the `BioLemmatizer <http://biolemmatizer.sourceforge.net/>`_ 1.1.

Installation
------------

Before installing **txtfnnl** itself, the additional (independent) parsers
should be installed. The following parsers are supported by **txtfnnl**:

`LinkGrammar <http://www.abisource.com/projects/link-grammar/>`_
  After downloading, unpacking, building, and installation (usually, just a
  curl-tar-configure-make-install loop) and assuming the default installation
  into ``/usr/local``, nothing else needs to be configured (parser version
  known to work with this AE wrapper: 4.7.6).
  
All other dependencies will be resolved by Maven (if you have a working
Internet connection). To "install" **txtfnnl** itself, execute ``mvn install``
in the TLD. **txtfnnl** is known to work on Apple OSX, Ubuntu and CentOS.
The framework requires the use of Java 1.5 or later (tested on 1.5 and 1.6).

After installing the Maven project, the ``txtfnnl`` shell script in the
**txtfnnl-bin** module can be put anywhere on the system ``$PATH``. If non-
standard locations were used for the parsers, the correct ``java.library.path``
setting should be configured in the script's ``JAVA_LIB_PATH`` variable.

Usage
-----

To use the pipelines from the command line, execute the ``txtfnnl`` script in
the **txtfnnl-bin** module directory (or copy it to your PATH).
The script expects to find the local Maven repository either in
``~/.m2/repository`` or otherwise defined as the environment variable 
``M2_REPO``.

Currently, the following pipelines are available:

- ``ss`` splits any kind of data Tika can extract plain-text from into 
  sentences, one per line.
- ``ema`` annotates known entity mentions on documents by supplying a mapping
  of input file names (w/o sufffix) to entity identifiers (type, namespace,
  identifier), looking up the names for those entity IDs in a DB, and
  matching any of those names in the extracted plain-text. Example use: for
  gene mention annotations using gnamed_
- ``rpe`` extracts relationship patterns between named entities in a known
  relationship. A relationship is defined as one or more entity IDs (as for
  ``ema``) together with the input file name and is supposed to be contained
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