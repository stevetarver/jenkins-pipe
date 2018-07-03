# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](http://keepachangelog.com/en/1.0.0/)
and this project adheres to [Semantic Versioning](http://semver.org/spec/v2.0.0.html).

**What is a release?** Our releases refer to the API, the calling convention of the containerPipeline() library. We will continue to add functionality to the latest release until we need to make a backwards incompatable change to the containerPipeline() invocations. E.g.

* Additional configuration that a client must specify or the build will break
* Change in the shape of the config hash passed to containerPipeline()

**Why?** This versioning strategy similar to being a customer of a Continuously Deployed service: you call the service and always get the latest and greatest functionality. If the calling convention changes, you will frequently have to change the url to get to the service, say `/v1/service` to `/v2/service`. For consumers, this provides:

* the latest functionality without having to modify your Jenkinsfile
* not having to update a fleet of services because of an incremental functionality addition

Note: there will never be a "micro" in the semver "major.minor.micro" version because we will just fix bugs, instead of cutting a new release containing bug fixes.

# [unreleased]

### Changes


### Migration


