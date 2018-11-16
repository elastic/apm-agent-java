#!/usr/bin/env bash

if [ -z "${ELASTIC_DOCS}" -o ! -d "${ELASTIC_DOCS}" ]; then
  echo "ELASTIC_DOCS is not defined, it should point to a folder where you checkout https://github.com/elastic/docs.git."
  echo "You also can define BUILD_DOCS_ARGS for aditional build options."
  exit 1
fi

${ELASTIC_DOCS}/build_docs.pl --chunk=1 ${BUILD_DOCS_ARGS} --doc docs/index.asciidoc -out docs/html
