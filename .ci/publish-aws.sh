#!/usr/bin/env bash
set -uo pipefail

#
# Publishes the created lambda layer zip to AWS as AWS Lambda Layers in every region.
# Finalized by generating an ARN table which will be used in the release notes.
#
# AWS_FOLDER is used for temporary output of publishing layers used to create the arn table. (Optional)
# ELASTIC_LAYER_NAME is the name of the lambda layer e.g. elastic-apm-java-ver-3-44-1 for the git tag v3.44.1 (Required)
# MAX_RETRIES is the number of retries for transient failures (Optional, default: 1)


# This needs to be set in GH actions
# https://dotjoeblog.wordpress.com/2021/03/14/github-actions-aws-error-exit-code-255/
# eu-west-1 is just a random region
export AWS_DEFAULT_REGION=${AWS_DEFAULT_REGION:-eu-west-1}

export AWS_FOLDER=${AWS_FOLDER:-.ci/.aws}
FULL_LAYER_NAME=${ELASTIC_LAYER_NAME:?layer name not provided}
MAX_RETRIES=${MAX_RETRIES:-1}
ALL_AWS_REGIONS=$(aws ec2 describe-regions --output json --no-cli-pager | jq -r '.Regions[].RegionName')

rm -rf "${AWS_FOLDER}"
mkdir -p "${AWS_FOLDER}"

zip_file="./apm-agent-lambda-layer/target/${FULL_LAYER_NAME}.zip"

mv ./apm-agent-lambda-layer/target/elastic-apm-java-aws-lambda-layer-*.zip "${zip_file}"

failed_regions=()

publish_to_region() {
  local region=$1
  local attempt=1

  while [ $attempt -le $MAX_RETRIES ]; do
    echo "Publish ${FULL_LAYER_NAME} in ${region} (attempt ${attempt}/${MAX_RETRIES})"
    if publish_output=$(aws lambda \
      --output json \
      publish-layer-version \
      --region="${region}" \
      --layer-name="${FULL_LAYER_NAME}" \
      --description="AWS Lambda Extension Layer for the Elastic APM Java Agent" \
      --license-info="Apache-2.0" \
      --compatible-runtimes java8.al2 java11 java17 java21 \
      --zip-file="fileb://${zip_file}" 2>&1); then

      echo "${publish_output}" > "${AWS_FOLDER}/${region}"
      layer_version=$(echo "${publish_output}" | jq '.Version')
      echo "Grant public layer access ${FULL_LAYER_NAME}:${layer_version} in ${region}"

      if grant_access_output=$(aws lambda \
        --output json \
        add-layer-version-permission \
        --region="${region}" \
        --layer-name="${FULL_LAYER_NAME}" \
        --action="lambda:GetLayerVersion" \
        --principal='*' \
        --statement-id="${FULL_LAYER_NAME}" \
        --version-number="${layer_version}" 2>&1); then

        echo "${grant_access_output}" > "${AWS_FOLDER}/.${region}-public"
        return 0
      else
        echo "WARNING: Failed to grant public access in ${region}: ${grant_access_output}"
      fi
    else
      echo "WARNING: Failed to publish to ${region}: ${publish_output}"
    fi

    attempt=$((attempt + 1))
    if [ $attempt -le $MAX_RETRIES ]; then
      echo "Retrying in 10 seconds..."
      sleep 10
    fi
  done

  return 1
}

for region in $ALL_AWS_REGIONS; do
  if ! publish_to_region "$region"; then
    echo "ERROR: Failed to publish to ${region} after ${MAX_RETRIES} attempts, skipping"
    failed_regions+=("$region")
  fi
done

sh -c "./.ci/create-arn-table.sh"

if [ ${#failed_regions[@]} -gt 0 ]; then
  echo ""
  echo "========================================="
  echo "WARNING: Failed to publish to the following regions:"
  for region in "${failed_regions[@]}"; do
    echo "  - ${region}"
  done
  echo "========================================="
  echo "The ARN table has been generated for successful regions only."
  echo "You may need to manually publish to the failed regions later."
  exit 1
fi
