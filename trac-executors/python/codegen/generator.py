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

import itertools as it
import re
import typing as tp
import pathlib
import logging

import google.protobuf.descriptor_pb2 as pb_desc
import google.protobuf.compiler.plugin_pb2 as pb_plugin


class LocationContext:

    def __init__(self, src_locations: tp.List[pb_desc.SourceCodeInfo.Location],
                 src_loc_code: int, src_loc_index: int, indent: int):

        self.src_locations = src_locations
        self.src_loc_code = src_loc_code
        self.src_loc_index = src_loc_index
        self.indent = indent

    def for_index(self, index: int) -> 'LocationContext':

        return LocationContext(self.src_locations, self.src_loc_code, index, self.indent)


class PythonicGenerator:

    INDENT_TEMPLATE = " " * 4

    PACKAGE_IMPORT_TEMPLATE = "from .{MODULE_NAME} import {SYMBOL}\n"

    FILE_TEMPLATE = \
        """# Code generated by TRAC\n""" \
        """\n""" \
        """{IMPORT_STATEMENTS}\n""" \
        """\n""" \
        """{ENUMS_CODE}\n""" \
        """{MESSAGES_CODE}\n"""

    MESSAGE_TEMPLATE = \
        """{INDENT}\n""" \
        """@dataclass\n""" \
        """{INDENT}class {CLASS_NAME}:\n""" \
        """{NEXT_INDENT}\n""" \
        """{NEXT_INDENT}\"\"\"\n""" \
        """{DOC_COMMENT}\n""" \
        """{NEXT_INDENT}\"\"\"\n""" \
        """{NEXT_INDENT}pass\n"""

    ENUM_TEMPLATE = \
        """{INDENT}\n""" \
        """{INDENT}class {CLASS_NAME}(enum.Enum):\n""" \
        """{NEXT_INDENT}\n""" \
        """{NEXT_INDENT}\"\"\"\n""" \
        """{DOC_COMMENT}\n""" \
        """{NEXT_INDENT}\"\"\"\n""" \
        """{INDENT}\n""" \
        """{ENUM_VALUES}\n"""

    ENUM_VALUE_TEMPLATE = \
        """{INDENT}{ENUM_VALUE_NAME} = {ENUM_VALUE_NUMBER}, {QUOTED_COMMENT}\n"""

    def __init__(self):

        logging.basicConfig(level=logging.DEBUG)
        self._log = logging.getLogger(PythonicGenerator.__name__)

        self._enum_type_field = self.get_field_number(pb_desc.FileDescriptorProto, "enum_type")
        self._message_type_field = self.get_field_number(pb_desc.FileDescriptorProto, "message_type")
        self._enum_value_field = self.get_field_number(pb_desc.EnumDescriptorProto, "value")

    def generate_package(self, package: str, files: tp.List[pb_desc.FileDescriptorProto]) \
            -> tp.List[pb_plugin.CodeGeneratorResponse.File]:

        output_files = []

        # Use the protobuf package as the Python package
        package_path = pathlib.Path(*package.split("."))
        package_imports = ""

        for file_descriptor in files:

            # Run the generator to produce code for the Python module
            src_locations = file_descriptor.source_code_info.location
            file_code = self.generate_file(src_locations, 0, file_descriptor)

            # Find the module name inside the package - this is the stem of the .proto file
            file_path = pathlib.PurePath(file_descriptor.name)
            file_stem = file_path.stem

            # Create a generator response for the module
            file_response = pb_plugin.CodeGeneratorResponse.File()
            file_response.content = file_code

            # File name is formed from the python package and the module name (.proto file stem)
            file_response.name = str(package_path.joinpath(file_stem + ".py"))

            output_files.append(file_response)

            # Generate import statements to include in the package-level __init__ file
            package_imports += self.generate_package_imports(file_descriptor)

        # Add an extra generator response file for the package-level __init__ file
        package_init_file = pb_plugin.CodeGeneratorResponse.File()
        package_init_file.name = str(package_path.joinpath("__init__.py"))
        package_init_file.content = package_imports

        output_files.append(package_init_file)

        return output_files

    def generate_package_imports(self, descriptor: pb_desc.FileDescriptorProto) -> str:

        file_path = pathlib.Path(descriptor.name)
        module_name = file_path.stem

        imports = ""

        if len(descriptor.enum_type) > 0 or len(descriptor.message_type) > 0:
            imports += "\n"

        for enum_type in descriptor.enum_type:
            imports += self.PACKAGE_IMPORT_TEMPLATE.format(
                MODULE_NAME=module_name,
                SYMBOL=enum_type.name)

        for message_type in descriptor.message_type:
            imports += self.PACKAGE_IMPORT_TEMPLATE.format(
                MODULE_NAME=module_name,
                SYMBOL=message_type.name)

        return imports

    def generate_file(self, src_loc, indent: int, descriptor: pb_desc.FileDescriptorProto) -> str:

        # print(descriptor.name)
        # self._log.info(descriptor.name)

        imports = []
        imports.append("from dataclasses import dataclass")

        if len(descriptor.enum_type) > 0:
            imports.append("import enum")

        # Generate enums
        enum_ctx = self.index_sub_ctx(src_loc, self._enum_type_field, indent)
        enum_code = list(it.starmap(self.generate_enum, zip(enum_ctx, descriptor.enum_type)))

        # Generate message classes
        message_ctx = self.index_sub_ctx(src_loc, self._message_type_field, indent)
        message_code = list(it.starmap(self.generate_message, zip(message_ctx, descriptor.message_type)))

        # Populate the template
        code = self.FILE_TEMPLATE \
            .replace("{INDENT}", self.INDENT_TEMPLATE * indent) \
            .replace("{IMPORT_STATEMENTS}", "\n".join(imports)) \
            .replace("{ENUMS_CODE}", "\n\n".join(enum_code)) \
            .replace("{MESSAGES_CODE}", "\n\n".join(message_code))

        return code

    def generate_message(self, ctx: LocationContext, descriptor: pb_desc.DescriptorProto) -> str:

        filtered_loc = self.filter_src_location(ctx.src_locations, ctx.src_loc_code, ctx.src_loc_index)

        # Comments from current code location
        current_loc = self.current_location(filtered_loc)

        if current_loc is not None:
            current_comment = current_loc.leading_comments
        else:
            current_comment = None

        current_comment = re.sub("^(\\*\n)|/", "", current_comment, count=1)
        current_comment = re.sub("\n$", "", current_comment)
        current_comment = re.sub("^ ?", self.INDENT_TEMPLATE * (ctx.indent + 1), current_comment)
        current_comment = re.sub("\\n ?", "\n" + self.INDENT_TEMPLATE * (ctx.indent + 1), current_comment)

        return self.MESSAGE_TEMPLATE \
            .replace("{INDENT}", self.INDENT_TEMPLATE * ctx.indent) \
            .replace("{NEXT_INDENT}", self.INDENT_TEMPLATE * (ctx.indent + 1)) \
            .replace("{CLASS_NAME}", descriptor.name) \
            .replace("{DOC_COMMENT}", current_comment)

    def generate_enum(self, ctx: LocationContext, descriptor: pb_desc.EnumDescriptorProto) -> str:

        filtered_loc = self.filter_src_location(ctx.src_locations, ctx.src_loc_code, ctx.src_loc_index)

        # Generate enum values
        values_ctx = self.index_sub_ctx(filtered_loc, self._enum_value_field, ctx.indent + 1)
        values_code = list(it.starmap(self.generate_enum_value, zip(values_ctx, descriptor.value)))

        # Comments from current code location
        current_loc = self.current_location(filtered_loc)

        if current_loc is not None:
            current_comment = current_loc.leading_comments
        else:
            current_comment = None

        current_comment = re.sub("^(\\*\n)|/", "", current_comment, count=1)
        current_comment = re.sub("\n$", "", current_comment)
        current_comment = re.sub("^ ?", self.INDENT_TEMPLATE * (ctx.indent + 1), current_comment)
        current_comment = re.sub("\\n ?", "\n" + self.INDENT_TEMPLATE * (ctx.indent + 1), current_comment)

        # Populate the template
        code = self.ENUM_TEMPLATE \
            .replace("{INDENT}", self.INDENT_TEMPLATE * ctx.indent) \
            .replace("{NEXT_INDENT}", self.INDENT_TEMPLATE * (ctx.indent + 1)) \
            .replace("{DOC_COMMENT}", current_comment) \
            .replace("{CLASS_NAME}", descriptor.name) \
            .replace("{ENUM_VALUES}", "\n".join(values_code))

        return code

    def generate_enum_value(self, ctx: LocationContext, descriptor: pb_desc.EnumValueDescriptorProto) -> str:

        filtered_loc = self.filter_src_location(ctx.src_locations, ctx.src_loc_code, ctx.src_loc_index)

        # Comments from current code location
        current_loc = self.current_location(filtered_loc)

        if current_loc is not None:
            current_comment = current_loc.leading_comments
        else:
            current_comment = None

        current_comment = re.sub("^(\\*\n)|/", "", current_comment, count=1)
        current_comment = re.sub("\n$", "", current_comment)
        current_comment = re.sub("^ ?", "", current_comment)
        current_comment = re.sub("\\n ?", "\n" + self.INDENT_TEMPLATE * (ctx.indent + 1), current_comment)

        if "\n" in current_comment:
            quoted_comment = '"""' + current_comment + '"""'
        else:
            quoted_comment = '"' + current_comment + '"'

        # Populate the template
        code = self.ENUM_VALUE_TEMPLATE \
            .replace("{INDENT}", self.INDENT_TEMPLATE * ctx.indent) \
            .replace("{QUOTED_COMMENT}", quoted_comment) \
            .replace("{ENUM_VALUE_NAME}", descriptor.name) \
            .replace("{ENUM_VALUE_NUMBER}", str(descriptor.number))

        return code

    # Helpers

    def filter_src_location(self, locations, loc_type, loc_index):

        def relative_path(loc: pb_desc.SourceCodeInfo.Location):

            return pb_desc.SourceCodeInfo.Location(
                path=loc.path[2:], span=loc.span,
                leading_comments=loc.leading_comments,
                trailing_comments=loc.trailing_comments,
                leading_detached_comments=loc.leading_detached_comments)

        filtered = filter(lambda l: len(l.path) >= 2 and l.path[0] == loc_type and l.path[1] == loc_index, locations)
        return list(map(relative_path, filtered))

    def current_location(self, locations) -> pb_desc.SourceCodeInfo.Location:

        return next(filter(lambda l: len(l.path) == 0, locations), None)

    def index_sub_ctx(self, src_locations, field_number, indent):

        base_ctx = LocationContext(src_locations, field_number, 0, indent)
        return iter(map(base_ctx.for_index, it.count(0)))

    def get_field_number(self, message_descriptor, field_name: str):

        field_descriptor = next(filter(
            lambda f: f.name == field_name,
            message_descriptor.DESCRIPTOR.fields), None)

        if field_descriptor is None:
            raise RuntimeError(f"Field {field_name} not found int type {message_descriptor.DESCRIPTOR.name}")

        return field_descriptor.number
