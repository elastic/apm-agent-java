#!/usr/bin/env bash
set -o pipefail

#
# Create the AWS ARN table given the below environment variables:
#
#   - AWS_FOLDER      - that's the location of the publish-layer-version output for each region
#	- SUFFIX_ARN_FILE - that's the output file.
#	- RELEASE_VERSION - that's the release version to be published in GitHub.
#

ARN_FILE=${SUFFIX_ARN_FILE}

{
    echo "[Release Notes for ${RELEASE_VERSION}](https://www.elastic.co/guide/en/apm/agent/java/current/release-notes-1.x.html#release-notes-${RELEASE_VERSION})"
	echo ''
	echo "### ARN"
	echo ''
	echo '|Region|ARN|'
	echo '|------|---|'
} > "${ARN_FILE}"

for f in $(ls "${AWS_FOLDER}"); do
	LAYER_VERSION_ARN=$(grep '"LayerVersionArn"' "$AWS_FOLDER/${f}" | cut -d":" -f2- | sed 's/ //g' | sed 's/"//g' | cut -d"," -f1)
	echo "INFO: create-arn-table ARN(${LAYER_VERSION_ARN}):region(${f}))"
	echo "|${f}|${LAYER_VERSION_ARN}|" >> "${ARN_FILE}"
done

echo '' >> "${ARN_FILE}"
