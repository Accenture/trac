#  Copyright 2021 Accenture Global Solutions Limited
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

# from __future__ import annotations

import typing as tp
import dataclasses as dc

import trac.rt.metadata as meta


def _empty(factory: tp.Callable):
    return dc.field(default_factory=factory)


class StorageConfig:

    def __init__(self,
                 storageType: str,  # noqa
                 storageConfig: tp.Dict[str, str] = {}):  # noqa

        self.storageType = storageType
        self.storageConfig = storageConfig


class StorageSettings:

    def __init__(self,
                 defaultStorage: str,  # noqa
                 defaultFormat: str):  # noqa

        self.defaultStorage = defaultStorage
        self.defaultFormat = defaultFormat


class SparkSettings:

    def __init__(self,
                 sparkConfig: tp.Dict[str, str]):  # noqa

        self.sparkConfig = sparkConfig


class RuntimeConfig:

    def __init__(self,
                 storage: tp.Dict[str, StorageConfig],
                 storageSettings: StorageSettings,  # noqa
                 sparkSettings: SparkSettings):  # noqa

        self.storage = storage
        self.storageSettings = storageSettings
        self.sparkSettings = sparkSettings


@dc.dataclass
class JobConfig:

    target: tp.Optional[str] = None

    parameters: tp.Dict[str, str] = _empty(dict)
    inputs: tp.Dict[str, str] = _empty(dict)
    outputs: tp.Dict[str, str] = _empty(dict)

    objects: tp.Dict[str, meta.ObjectDefinition] = _empty(dict)

    job: tp.Optional[meta.JobDefinition] = None
