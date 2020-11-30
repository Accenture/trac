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

import typing as tp
import trac.rt.metadata as meta


class NodeId:

    def __init__(self, name: str, ctx: tp.List[str]):
        self.name = name
        self.ctx = ctx


class Node:
    pass


class Graph:

    def __init__(self):
        pass


class ContextPushNode:

    def __init__(self, mapping: tp.Dict[str, NodeId]):
        self.mapping = mapping


class ContextPopNode:

    def __init__(self, mapping: tp.Dict[str, NodeId]):
        self.mapping = mapping


class LoadDataNode:

    def __init__(self, node_id: NodeId, data_def: meta.DataDefinition):
        self.node_id = node_id
        self.data_def = data_def
