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
- for the gene mention annotations (``gma``), a gnamed_ DB has to be
  available on the network, which in turn (by default) requires
  `PostgreSQL <http://www.postgresql.org/>`_ 8.4+; the tests for this part
  of txtfnnl furthermore use the `H2 <http://www.h2database.com/>`_
  in-memory DB.

Installation
------------

Execute ``mvn install`` in the TLD.
**txtfnnl** is known to work on Apple OSX, Ubuntu and CentOS.
The framework requires the use of Java 1.5 or later.

Usage
-----

To use the pipelines from the command line, execute the ``txtfnnl`` script in
the **txtfnnl-bin** module directory (and/or place/copy it on/to your PATH).
The script expects to find the local Maven repository either in
``~/.m2/repository`` or otherwise defined as the environment variable 
``M2_REPO``.

Currently, the following pipelines are available:

- ``ss`` splits any kind of data Tika can extract plain-text from into 
  sentences, one per line.
- ``gma`` annotates known gene mentions on documents by supplying a mapping of
  input file names (w/o the sufffix) to gene identifiers (type, namespace,
  identifier), looking up the names for those gene IDs in a gnamed_ DB, and
  matching any of those names in the extracted plain-text.

License
-------

**txtfnnl** is governed by the
`Apache 2.0 License <http://www.apache.org/licenses/LICENSE-2.0.html>`_ -
see ``LICENSE.txt`` in this directory for details.

.. _gnamed: http://github.com/fnl/gnamed