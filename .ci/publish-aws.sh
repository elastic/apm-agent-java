#!/usr/bin/env bash
set -euo pipefail

#
# Publishes the created lambda layer zip to AWS as AWS Lambda Layers in every region.
# Finalized by generating an ARN table which will be used in the release notes.
#
# AWS_FOLDER is used for temporary output of publishing layers used to create the arn table. (Optional)
# ELASTIC_LAYER_NAME is the name of the lambda layer e.g. elastic-apm-java-ver-3-44-1 for the git tag v3.44.1 (Required)


# This needs to be set in GH actions
# https://dotjoeblog.wordpress.com/2021/03/14/github-actions-aws-error-exit-code-255/
# eu-west-1 is just a random region
export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-eu-west-1}

export AWS_FOLDER=${AWS_FOLDER:-.ci/.aws}
FULL_LAYER_NAME=${ELASTIC_LAYER_NAME:?layer name not provided}
ALL_AWS_REGIONS=$(aws ec2 describe-regions --output json --no-cli-pager | jq -r '.Regions[].RegionName')

rm -rf "${AWS_FOLDER}"
mkdir -p "${AWS_FOLDER}"

zip_file="./apm-agent-lambda-layer/target/${FULL_LAYER_NAME}.zip"

mv ./apm-agent-lambda-layer/target/elastic-apm-java-aws-lambda-layer-*.zip "${zip_file}"

for region in $ALL_AWS_REGIONS; do
  echo "Publish ${FULL_LAYER_NAME} in ${region}"
  publish_output=$(aws lambda \
    --output json \
    publish-layer-version \
    --region="${region}" \
    --layer-name="${FULL_LAYER_NAME}" \
    --description="AWS Lambda Extension Layer for the Elastic APM Java Agent" \
    --license-info="Apache-2.0" \
    --compatible-runtimes java8.al2 java11 java17 java21 \
    --zip-file="fileb://${zip_file}")
  echo "${publish_output}" > "${AWS_FOLDER}/${region}"
  layer_version=$(echo "${publish_output}" | jq '.Version')
  echo "Grant public layer access ${FULL_LAYER_NAME}:${layer_version} in ${region}"
  grant_access_output=$(aws lambda \
  		--output json \
  		add-layer-version-permission \
  		--region="${region}" \
  		--layer-name="${FULL_LAYER_NAME}" \
  		--action="lambda:GetLayerVersion" \
  		--principal='*' \
  		--statement-id="${FULL_LAYER_NAME}" \
  		--version-number="${layer_version}")
  echo "${grant_access_output}" > "${AWS_FOLDER}/.${region}-public"
done

sh -c "./.ci/create-arn-table.sh"
