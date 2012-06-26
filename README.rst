======================
txtfnnl /tɛkstˈfʌn.əl/
======================

Introduction
------------

A text mining framework providing various language processing pipelines.

Dependencies
------------

``txtfnnl`` currently integrates the following Apache projects:

  - `Tika <http://tika.apache.org>`_ 1.1
  - `UIMA <http://uima.apache.org>`_ 2.4
  - `OpenNLP <http://opennlp.apache.org>`_ 1.5 
  - `Maven <http://maven.apache.org>`_ 3.0

Installation
------------

Execute ``mvn install`` in the TLD.
``txtfnnl`` is known to work on Apple OSX, Ubuntu and CentOS.

Usage
-----

To use the pipelines from the command line, execute the ``txtfnnl`` script in
the ``txtfnnl-bin`` module directory (and/or place it on your PATH).
The script expects to find the local Maven repository either in
``~/.m2/repository`` or otherwise configured as an environment variable called
``M2_REPO``.

License
-------

Just as all code ``txtfnnl`` is based on, the
`Apache 2.0 License <http://www.apache.org/licenses/LICENSE-2.0.html>`_
applies.
See ``LICENSE.txt`` in the TLD directory for details.