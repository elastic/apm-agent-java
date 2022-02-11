SHELL = /bin/bash -eo pipefail

AWS_FOLDER = .aws
AWS_LAMBDA_ZIP_LOCATION = elastic-apm-agent/target

export AWS_FOLDER

dist:
	@cp ../$(AWS_LAMBDA_ZIP_LOCATION)/elastic-apm-java-aws-lambda-layer-*.zip ../$(AWS_LAMBDA_ZIP_LOCATION)/$(ELASTIC_LAYER_NAME).zip

# List all the AWS regions
get-all-aws-regions:
	@aws \
		ec2 \
		describe-regions \
		--region us-east-1 \
		--output json \
		--no-cli-pager \
		| jq -r '.Regions[].RegionName' > .regions

# Publish the given LAYER in all the AWS regions
publish-in-all-aws-regions: validate-layer-name get-all-aws-regions dist
	@mkdir -p $(AWS_FOLDER)
	@while read AWS_DEFAULT_REGION; do \
		echo "publish '$(ELASTIC_LAYER_NAME)' in $${AWS_DEFAULT_REGION}"; \
		AWS_DEFAULT_REGION="$${AWS_DEFAULT_REGION}" ELASTIC_LAYER_NAME=$(ELASTIC_LAYER_NAME) $(MAKE) publish > $(AWS_FOLDER)/$${AWS_DEFAULT_REGION}; \
		AWS_DEFAULT_REGION="$${AWS_DEFAULT_REGION}" ELASTIC_LAYER_NAME=$(ELASTIC_LAYER_NAME) $(MAKE) grant-public-layer-access; \
	done <.regions

# Publish the given LAYER in the given AWS region
publish: validate-layer-name validate-aws-default-region
	@aws lambda \
		--output json \
		publish-layer-version \
		--layer-name "$(ELASTIC_LAYER_NAME)" \
		--description "AWS Lambda Extension Layer for the Elastic APM Java Agent" \
		--compatible-runtimes java8.al2 java11 \
		--license "Apache-2.0" \
		--zip-file "fileb://./$(AWS_LAMBDA_ZIP_LOCATION)/$(ELASTIC_LAYER_NAME).zip"

# Grant public access to the given LAYER in the given AWS region
grant-public-layer-access: validate-layer-name validate-aws-default-region
	@echo "[debug] $(ELASTIC_LAYER_NAME) with version: $$($(MAKE) -s --no-print-directory get-version)"
	@aws lambda \
		--output json \
		add-layer-version-permission \
		--layer-name "$(ELASTIC_LAYER_NAME)" \
		--action lambda:GetLayerVersion \
		--principal '*' \
		--statement-id "$(ELASTIC_LAYER_NAME)" \
		--version-number $$($(MAKE) -s --no-print-directory get-version) > $(AWS_FOLDER)/.$(AWS_DEFAULT_REGION)-public

# Generate the file with the ARN entries
create-arn-file: validate-suffix-arn-file
	@./create-arn-table.sh

upload-lambda-asset: validate-release-version validate-layer-name
	@gh release list
	@gh \
		release \
		upload $(RELEASE_VERSION) \
		./$(AWS_LAMBDA_ZIP_LOCATION)/$(ELASTIC_LAYER_NAME).zip

# Get the ARN Version for the AWS_REGIONS
# NOTE: jq -r .Version "$(AWS_FOLDER)/$(AWS_DEFAULT_REGION)" fails in the CI
#       with 'parse error: Invalid numeric literal at line 1, column 5'
get-version: validate-aws-default-region
	@grep '"Version"' "$(AWS_FOLDER)/$(AWS_DEFAULT_REGION)" | cut -d":" -f2 | sed 's/ //g' | cut -d"," -f1

validate-release-version:
ifndef RELEASE_VERSION
	$(error RELEASE_VERSION is undefined)
endif

validate-suffix-arn-file:
ifndef SUFFIX_ARN_FILE
	$(error SUFFIX_ARN_FILE is undefined)
endif

validate-layer-name:
ifndef ELASTIC_LAYER_NAME
	$(error ELASTIC_LAYER_NAME is undefined)
endif

validate-aws-default-region:
ifndef AWS_DEFAULT_REGION
	$(error AWS_DEFAULT_REGION is undefined)
endif
