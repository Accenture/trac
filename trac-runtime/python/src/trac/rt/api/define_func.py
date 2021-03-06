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


import typing as tp
import inspect
from trac.rt.metadata import *

__field_def_params = inspect.signature(FieldDefinition.__init__).parameters


class NamedParameter:

    def __init__(self, param_name: str, param: ModelParameter):
        self.paramName = param_name
        self.param = param


def define_parameters(
        *params: tp.Union[NamedParameter, tp.List[NamedParameter]]) \
        -> tp.Dict[str, ModelParameter]:

    if len(params) == 1 and isinstance(params[0], list):
        return {p.paramName: p.param for p in params[0]}
    else:
        return {p.paramName: p.param for p in params}


def define_parameter(
        param_name: str,
        param_type: tp.Union[TypeDescriptor, BasicType],
        label: str,
        default_value: tp.Optional[tp.Any] = None) \
        -> NamedParameter:

    if isinstance(param_type, TypeDescriptor):
        param_type_descriptor = param_type
    else:
        param_type_descriptor = TypeDescriptor(param_type, None, None)

    return NamedParameter(param_name, ModelParameter(label, param_type_descriptor, default_value))


def define_table(*fields: FieldDefinition):
    return TableDefinition([*fields])


def define_field(*args, **kwargs):

    arg_names = list(kwargs.keys())

    for arg_name in arg_names:

        if arg_name in __field_def_params:
            continue

        # Convert arg names starting with "field", e.g. label -> fieldLabel
        prefix_name = "field" + arg_name[0].upper() + arg_name[1:]

        if prefix_name in __field_def_params:
            kwargs[prefix_name] = kwargs[arg_name]
            kwargs.pop(arg_name)
            continue

        # Convert snake-case to camel-case
        camel_words = arg_name.split('_')
        camel_name = camel_words[0] + ''.join(word.title() for word in camel_words[1:])

        if camel_name in __field_def_params:
            kwargs[camel_name] = kwargs[arg_name]
            kwargs.pop(arg_name)

    return FieldDefinition(*args, **kwargs)


P = define_parameter
F = define_field
