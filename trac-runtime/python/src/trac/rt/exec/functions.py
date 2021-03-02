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

from __future__ import annotations

from .graph import *
from .context import ModelContext

import trac.rt.api as api
import trac.rt.config as config

import trac.rt.impl.repositories as _repos
import trac.rt.impl.storage as _storage
import trac.rt.impl.data as _data

import abc
import typing as tp


NodeContext = tp.Dict[NodeId, object]  # Available prior node results when a node function is called
NodeResult = tp.Any  # Result of a node function (will be recorded against the node ID)


class NodeFunction(tp.Callable[[NodeContext], NodeResult]):

    @abc.abstractmethod
    def __call__(self, ctx: NodeContext) -> NodeResult:
        pass


class NoopNode(NodeFunction):

    def __call__(self, _: NodeContext) -> NodeResult:
        return None


class IdentityFunc(NodeFunction):

    def __call__(self, ctx: NodeContext) -> NodeResult:
        return ctx


class JobNodeFunc(NodeFunction):

    def __call__(self, ctx: NodeContext) -> NodeResult:
        return ctx


class ContextPushFunc(NodeFunction):

    def __init__(self, node: ContextPushNode):
        self.node = node

    def __call__(self, ctx: NodeContext) -> NodeResult:

        target_ctx = dict()

        for target_id, source_id in self.node.mapping.items():

            source_item = ctx.get(source_id)

            if source_item is None:
                raise RuntimeError()  # TODO, should never happen

            if target_id.namespace != self.node.namespace:
                raise RuntimeError()  # TODO, should never happen

            target_ctx[target_id] = source_item

        return target_ctx


class ContextPopFunc(NodeFunction):

    def __init__(self, node: ContextPopNode):
        self.node = node

    def __call__(self, ctx: NodeContext) -> NodeResult:

        target_ctx = dict()

        for source_id, target_id in self.node.mapping.items():

            source_item = ctx.get(source_id)

            if source_item is None:
                raise RuntimeError()  # TODO, should never happen

            target_ctx[target_id] = source_item

        return target_ctx


class MapIdentityFunc(NodeFunction):

    def __init__(self, node: MapIdentityNode):
        self.node = node

    def __call__(self, ctx: NodeContext) -> NodeResult:
        return ctx[self.node.src_id].result


class MapKeyedItemFunc(NodeFunction):

    def __init__(self, node: MapKeyedItemNode):
        self.node = node

    def __call__(self, ctx: NodeContext) -> NodeResult:
        src_node_result = ctx[self.node.src_id].result
        src_item = src_node_result.get(self.node.src_item)
        return src_item


class MapDataFunc(NodeFunction):

    def __init__(self):
        super().__init__()

    def __call__(self, ctx: NodeContext) -> NodeResult:
        pass


class DataViewFunc(NodeFunction):

    def __init__(self, node: DataViewNode):
        super().__init__()
        self.node = node

    def __call__(self, ctx: NodeContext) -> NodeResult:

        root_node = ctx.get(self.node.root_item)  # noqa
        root_item: _data.DataItem = root_node.result  # noqa
        root_part_key = _data.DataPartKey.for_root()

        return _data.DataView(self.node.schema, {root_part_key: [root_item]})


class DataItemFunc(NodeFunction):

    def __init__(self, node: MapDataItemNode):
        super().__init__()
        self.node = node

    def __call__(self, ctx: NodeContext) -> NodeResult:

        data_view: _data.DataView = ctx[self.node.data_view_id].result

        # TODO: Support selecting data item described by self.node

        # Selecting data item for part-root, delta=0
        part_key = _data.DataPartKey.for_root()
        part = data_view.parts[part_key]
        delta: _data.DataItem = part[0]  # selects delta=0

        return delta


class LoadDataFunc(NodeFunction):

    def __init__(self, node: LoadDataNode, storage: _storage.StorageManager):
        super().__init__()
        self.node = node
        self.storage = storage

    def __call__(self, ctx: NodeContext) -> NodeResult:

        data_item = self.node.data_item
        data_copy = self.choose_copy(data_item, self.node.storage_def)

        file_storage = self.storage.get_file_storage(data_copy.storageKey)
        data_storage = self.storage.get_data_storage(data_copy.storageKey)

        stat = file_storage.stat(data_copy.storagePath)

        if stat.file_type == _storage.FileType.FILE:

            df = data_storage.read_pandas_table(
                self.node.data_def.schema,
                data_copy.storagePath, data_copy.storageFormat,
                storage_options={})

            return _data.DataItem(pandas=df)

        else:

            raise NotImplementedError("Directory storage format not available yet")

    def choose_copy(self, data_item: str, storage_def: meta.StorageDefinition) -> meta.StorageCopy:

        storage_info = storage_def.dataItems.get(data_item)

        if storage_info is None:
            raise RuntimeError("Invalid metadata")  # TODO: Error

        incarnation = next(filter(
            lambda i: i.incarnationStatus == meta.IncarnationStatus.INCARNATION_AVAILABLE,
            reversed(storage_info.incarnations)), None)

        if incarnation is None:
            raise RuntimeError("Data item not available (it has been expunged)")  # TODO: Error

        copy = next(filter(
            lambda c: c.copyStatus == meta.CopyStatus.COPY_AVAILABLE
            and self.storage.has_data_storage(c.storageKey),
            incarnation.copies), None)

        if copy is None:
            raise RuntimeError("No copy of the data is available in a connected storage location")  # TODO: Error

        return copy


class SaveDataFunc(NodeFunction):

    def __init__(self, node: SaveDataNode, storage: _storage.StorageManager):
        super().__init__()
        self.node = node
        self.storage = storage

    def __call__(self, ctx: NodeContext) -> NodeResult:

        data_item: _data.DataItem = ctx[self.node.data_item_id].result
        df = data_item.pandas

        # TODO: Feed through these values
        # data_item_name= self.node.data_item_id.name
        # data_copy: meta.StorageCopy = self.choose_copy(data_item_name, self.node.storage_def)
        storage_key = "example_data"
        storage_path = "temp_output.csv"
        storage_format = "CSV"

        file_storage = self.storage.get_file_storage(storage_key)
        data_storage = self.storage.get_data_storage(storage_key)

        # TODO!: decide where to store!

        data_storage.write_pandas_table(
            self.node.data_def.schema, df,
            storage_path, storage_format,
            storage_options={})

        return True


class ModelFunc(NodeFunction):

    def __init__(self, node: ModelNode, job_config: config.JobConfig, model_class: api.TracModel.__class__):
        super().__init__()
        self.node = node
        self.job_config = job_config
        self.model_class = model_class

    def __call__(self, ctx: NodeContext) -> NodeResult:

        input_ids = set(map(lambda mi: NodeId(mi, self.node.id.namespace), self.node.model_def.input))
        output_ids = set(map(lambda mo: NodeId(mo, self.node.id.namespace), self.node.model_def.output))

        data_inputs = {
            nid.name: ctx[nid].result
            for nid in input_ids}

        data_outputs = {
            nid.name: _data.DataView(schema=self.node.model_def.output[nid.name], parts={})
            for nid in output_ids}

        data_ctx = {**data_inputs, **data_outputs}

        model_ctx = ModelContext(
            self.node.model_def, self.model_class,
            parameters=self.job_config.parameters,
            data=data_ctx)

        model = self.model_class()
        model.run_model(model_ctx)

        return data_ctx


class FunctionResolver:

    __ResolveFunc = tp.Callable[['FunctionResolver', config.JobConfig, Node], NodeFunction]

    def __init__(self, repositories: _repos.Repositories, storage: _storage.StorageManager):
        self._repos = repositories
        self._storage = storage

    def resolve_node(self, job_config, node: Node) -> NodeFunction:

        basic_node_class = self.__basic_node_mapping.get(node.__class__)

        if basic_node_class:
            return basic_node_class(node)

        resolve_func = self.__node_mapping[node.__class__]

        if resolve_func is None:
            raise RuntimeError()  # TODO: Error

        return resolve_func(self, job_config, node)

    def resolve_load_data(self, job_config: config.JobConfig, node: LoadDataNode):
        return LoadDataFunc(node, self._storage)

    def resolve_save_data(self, job_config: config.JobConfig, node: SaveDataNode):
        return SaveDataFunc(node, self._storage)

    def resolve_model_node(self, job_config: config.JobConfig, node: ModelNode) -> NodeFunction:

        model_loader = self._repos.get_model_loader(node.model_def.repository)
        model_class = model_loader.load_model(node.model_def)

        return ModelFunc(node, job_config, model_class)

    __basic_node_mapping: tp.Dict[Node.__class__, NodeFunction.__class__] = {
        ContextPushNode: ContextPushFunc,
        ContextPopNode: ContextPopFunc,
        MapIdentityNode: MapIdentityFunc,
        MapKeyedItemNode: MapKeyedItemFunc,
        DataViewNode: DataViewFunc,
        MapDataItemNode: DataItemFunc}

    __node_mapping: tp.Dict[Node.__class__, __ResolveFunc] = {

        IdentityNode: lambda s, j, n: IdentityFunc(),

        JobOutputMetadataNode: lambda s, j, n: NoopNode(),
        JobResultMetadataNode: lambda s, j, n: NoopNode(),
        JobNode: lambda s, j, n: JobNodeFunc(),


        LoadDataNode: resolve_load_data,
        SaveDataNode: resolve_save_data,
        ModelNode: resolve_model_node
    }
