#!/usr/bin/env python

#  Copyright 2020 Accenture Global Solutions Limited
#
#  Licensed under the Apache License, Version 2.0 (the "License");
#  you may not use this file except in compliance with the License.
#  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.

import sys
import logging
import itertools as it

import google.protobuf.compiler.plugin_pb2 as pb_plugin

import generator as gen


class TracPlugin:

    def __init__(self, pb_request: pb_plugin.CodeGeneratorRequest):

        logging.basicConfig(level=logging.DEBUG)
        self._log = logging.getLogger(TracPlugin.__name__)

        self._request = pb_request

    def generate(self):

        generator = gen.TracGenerator()
        generated_response = pb_plugin.CodeGeneratorResponse()

        input_files = self._request.proto_file
        input_files = filter(lambda f: f.name in self._request.file_to_generate, input_files)
        input_files = filter(lambda f: f.source_code_info.ByteSize() > 0, input_files)

        sorted_files = sorted(input_files, key=lambda f: f.package)
        packages = it.groupby(sorted_files, lambda f: f.package)

        for package, files in packages:

            # Take files out of iter group, they may be used multiple times
            files = list(files)

            self._log.info("package '{}' ({} files)".format(package, len(files)))

            package_files = generator.generate_package(package, files)
            generated_response.file.extend(package_files)

        # Generate package-level files for empty packages
        # (this creates a valid package tree from the root package)

        proto_packages = set(map(lambda f: f.package, self._request.proto_file))
        all_packages = self.expand_parent_packages(proto_packages)

        for package in all_packages:
            if package not in proto_packages:
                package_files = generator.generate_package(package, [])
                generated_response.file.extend(package_files)

        return generated_response

    def expand_parent_packages(self, packages):

        all_packages = set()

        for package in packages:

            package_path = package.split(".")

            for i in range(len(package_path)):
                parent_package = ".".join(package_path[:i+1])
                all_packages.add(parent_package)

        return all_packages


if __name__ == "__main__":

    data = sys.stdin.buffer.read()

    request = pb_plugin.CodeGeneratorRequest()
    request.ParseFromString(data)

    plugin = TracPlugin(request)

    response = plugin.generate()
    output = response.SerializeToString()

    sys.stdout.buffer.write(output)
