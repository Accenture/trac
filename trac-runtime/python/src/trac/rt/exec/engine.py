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

from __future__ import annotations

import typing as tp
from copy import copy
from dataclasses import dataclass, field

import trac.rt.config as config
import trac.rt.impl.util as util
import trac.rt.impl.repositories as repos

import trac.rt.exec.actors as actors
import trac.rt.exec.graph_builder as _graph
import trac.rt.exec.functions as _func
from trac.rt.exec.graph import NodeId


@dataclass
class GraphContextNode:

    """
    Represents the state of a single node in the execution graph for processing by the TRAC engine
    """

    node: _graph.Node
    dependencies: tp.Dict[NodeId, _graph.DependencyType]
    function: tp.Optional[_func.NodeFunction] = None
    result: tp.Optional[tp.Any] = None
    error: tp.Optional[str] = None

    def __post_init__(self):
        if not self.dependencies:
            self.dependencies = copy(self.node.dependencies)


@dataclass
class GraphContext:

    """
    Represents the state of an execution graph being processed by the TRAC engine
    """

    nodes: tp.Dict[NodeId, GraphContextNode]
    pending_nodes: tp.Set[NodeId] = field(default_factory=set)
    active_nodes: tp.Set[NodeId] = field(default_factory=set)
    succeeded_nodes: tp.Set[NodeId] = field(default_factory=set)
    failed_nodes: tp.Set[NodeId] = field(default_factory=set)


class GraphBuilder(actors.Actor):

    """
    GraphBuilder is a worker (actors.Worker) responsible for building the execution graph for a job
    The logic for graph building is provided in graph_builder.py
    """

    def __init__(self, job_config: config.JobConfig, repositories: repos.Repositories):
        super().__init__()
        self.job_config = job_config
        self.graph: tp.Optional[GraphContext] = None

        self._resolver = _func.FunctionResolver(repositories)
        self._log = util.logger_for_object(self)

    def on_start(self):

        self._log.info("Building execution graph")

        graph_data = _graph.GraphBuilder.build_job(self.job_config)
        graph_nodes = {node_id: GraphContextNode(node, {}) for node_id, node in graph_data.nodes.items()}
        self.graph = GraphContext(graph_nodes, pending_nodes=set(graph_nodes.keys()))

        self._log.info("Resolving graph nodes to executable code")

        for node_id, node in self.graph.nodes.items():
            node.function = self._resolver.resolve_node(self.job_config, node.node)

        self.actors().send_parent("job_graph", self.graph)

    @actors.Message
    def get_execution_graph(self):

        self.actors().send(self.actors().sender, "job_graph", self.graph)


class GraphProcessor(actors.Actor):

    """
    GraphProcessor is the actor that runs the execution graph for a job
    Nodes are dispatched for execution as soon as all their dependencies are met
    When a node completes, the remaining nodes are checked to see if more can be dispatched
    The graph is fully processed when all its nodes are processed

    If there is an error with any node, the job is considered to have failed
    In this case, nodes already running are allowed to complete but no new nodes are submitted
    Once all running nodes are stopped, an error is reported to the parent
    """

    def __init__(self, graph: GraphContext):
        super().__init__()
        self.graph = graph
        self.processors: tp.Dict[NodeId, actors.ActorId] = dict()
        self._log = util.logger_for_object(self)

    def on_start(self):

        self._log.info("Begin processing graph")
        self.actors().send(self.actors().id, "submit_viable_nodes")

    @actors.Message
    def submit_viable_nodes(self):

        node_processors = dict()

        def process_graph(graph: GraphContext) -> GraphContext:

            pending_nodes = copy(graph.pending_nodes)
            active_nodes = copy(graph.active_nodes)
            failed_nodes = copy(graph.failed_nodes)

            for node_id in graph.pending_nodes:

                node = self.graph.nodes[node_id]

                if self._upstream_failure(node, graph):

                    self._log.warning(f"SKIP {str(node_id)} (upstream failure)")

                    pending_nodes.discard(node_id)
                    failed_nodes.add(node_id)

                elif self._is_viable_node(node, graph):

                    node_ref = self.actors().spawn(NodeProcessor, self.graph, node_id, node)
                    node_processors[node_id] = node_ref

                    pending_nodes.discard(node_id)
                    active_nodes.add(node_id)

            processed_graph = copy(self.graph)
            processed_graph.pending_nodes = pending_nodes
            processed_graph.active_nodes = active_nodes
            processed_graph.failed_nodes = failed_nodes

            return processed_graph

        current_graph = self.graph
        new_graph = process_graph(current_graph)

        # Let errors propagate as far as they can without any nodes being evaluated
        while len(new_graph.failed_nodes) > len(current_graph.failed_nodes):
            current_graph = new_graph
            new_graph = process_graph(current_graph)

        self.graph = new_graph
        self.processors = {**self.processors, **node_processors}

        # Job may have completed due to error propagation
        self.check_job_status(do_submit=False)

    @classmethod
    def _is_viable_node(cls, node: GraphContextNode, graph: GraphContext):

        return all(dep in graph.succeeded_nodes for dep in node.dependencies)

    @classmethod
    def _upstream_failure(cls, node: GraphContextNode, graph: GraphContext):

        return any(not dep_type.tolerant and dep in graph.failed_nodes
                   for dep, dep_type in node.dependencies.items())

    @actors.Message
    def node_succeeded(self, node_id: NodeId, result):

        old_node = self.graph.nodes[node_id]
        node = copy(old_node)
        node.result = result

        nodes = {**self.graph.nodes, node_id: node}

        active_nodes = copy(self.graph.active_nodes)
        active_nodes.remove(node_id)

        succeeded_nodes = copy(self.graph.succeeded_nodes)
        succeeded_nodes.add(node_id)

        new_graph = copy(self.graph)
        new_graph.nodes = nodes
        new_graph.active_nodes = active_nodes
        new_graph.succeeded_nodes = succeeded_nodes

        self.graph = new_graph
        self.check_job_status()

    @actors.Message
    def node_failed(self, node_id: NodeId, error):

        old_node = self.graph.nodes[node_id]
        node = copy(old_node)
        node.error = error

        nodes = {**self.graph.nodes, node_id: node}

        active_nodes = copy(self.graph.active_nodes)
        active_nodes.remove(node_id)

        failed_nodes = copy(self.graph.failed_nodes)
        failed_nodes.add(node_id)

        new_graph = copy(self.graph)
        new_graph.nodes = nodes
        new_graph.active_nodes = active_nodes
        new_graph.failed_nodes = failed_nodes

        self.graph = new_graph
        self.check_job_status()

    def check_job_status(self, do_submit=True):

        # Do not check final status if there are pending nodes to be submitted
        if do_submit and any(self.graph.pending_nodes):
            self.actors().send(self.actors().id, "submit_viable_nodes")
            return

        # If processing is complete, report the final status to the engine
        if not any(self.graph.active_nodes):

            if any(self.graph.pending_nodes):
                self._log.error("Processor has become deadlocked (cyclic dependency error)")
                self.actors().send_parent("job_failed")

            elif any(self.graph.failed_nodes):
                self.actors().send_parent("job_failed")

            else:
                self.actors().send_parent("job_succeeded")


class NodeProcessor(actors.Actor):

    """
    Processor responsible for running individual nodes in an execution graph
    TODO: How to decide when to allocate an actors.Worker (long running, separate thread)
    """

    def __init__(self, graph: GraphContext, node_id: str, node: GraphContextNode):
        super().__init__()
        self.graph = graph
        self.node_id = node_id
        self.node = node
        self._log = util.logger_for_object(self)

    def on_start(self):

        self.actors().send(self.actors().id, "evaluate_node")

    @actors.Message
    def graph_event(self):
        pass

    @actors.Message
    def stream_event(self):
        pass

    @actors.Message
    def evaluate_node(self):

        node_type = type(self.node.node).__name__

        try:
            self._log.info(f"START {str(self.node_id)} ({node_type})")

            if isinstance(self.node.node, _graph.ModelNode):
                self._log.info("Model entry point: " + self.node.node.model_def.entryPoint)

            result = self.node.function(self.graph.nodes)

            self.actors().send_parent("node_succeeded", self.node_id, result)
            self._log.info(f"DONE {str(self.node_id)} ({node_type})")

        except Exception as e:
            self.actors().send_parent("node_failed", self.node_id, e)
            self._log.error(f"FAILED {str(self.node_id)} ({node_type})")


class JobProcessor(actors.Actor):

    """
    JobProcessor oversees the whole lifecycle of a job
    This includes setup (GraphBuilder), execution (GraphProcessor) and reporting results
    """

    def __init__(self, job_id, job_config, repositories: repos.Repositories):
        super().__init__()
        self.job_id = job_id
        self.job_config = job_config
        self._repos = repositories
        self._log = util.logger_for_object(self)

    def on_start(self):
        self._log.info("Starting job")
        self.actors().spawn(GraphBuilder, self.job_config, self._repos)

    @actors.Message
    def job_graph(self, graph: GraphContext):
        self.actors().spawn(GraphProcessor, graph)
        self.actors().stop(self.actors().sender)

    @actors.Message
    def job_succeeded(self):
        self._log.info(f"Job succeeded {self.job_id}")
        self.actors().send_parent("job_succeeded", self.job_id)

    @actors.Message
    def job_failed(self):
        self._log.info(f"Job failed {self.job_id}")
        self.actors().send_parent("job_failed", self.job_id)


@dataclass
class EngineContext:

    jobs: tp.Dict
    data: tp.Dict


class TracEngine(actors.Actor):

    """
    TracEngine is the main actor that controls all operations in the TRAC runtime
    Messages may be passed in externally via ActorSystem, e.g. commands to launch jobs
    """

    def __init__(self, sys_config: config.RuntimeConfig, repositories: repos.Repositories, batch_mode=False):
        super().__init__()

        self.engine_ctx = EngineContext(jobs={}, data={})

        self._log = util.logger_for_object(self)
        self._sys_config = sys_config
        self._repos = repositories
        self._batch_mode = batch_mode

    def on_start(self):

        self._log.info("Engine is up and running")

    def on_stop(self):

        self._log.info("Engine shutdown complete")

    @actors.Message
    def submit_job(self, job_info: object):

        job_id = 'test_job'

        self._log.info("A job has been submitted")

        job_actor_id = self.actors().spawn(JobProcessor, job_id, job_info, self._repos)

        jobs = {**self.engine_ctx.jobs, job_id: job_actor_id}
        self.engine_ctx = EngineContext(jobs, self.engine_ctx.data)

    @actors.Message
    def job_succeeded(self, job_id: str):

        self._log.info(f"Recording job as successful: {job_id}")
        self._clean_up_job(job_id)

    @actors.Message
    def job_failed(self, job_id: str):

        self._log.info(f"Recording job as failed: {job_id}")
        self._clean_up_job(job_id)

    def _clean_up_job(self, job_id: str):

        jobs = copy(self.engine_ctx.jobs)
        job_actor_id = jobs.pop(job_id)

        if self._batch_mode:
            # If the engine is in batch mode, shut down the whole engine once the first job completes
            self._log.info("Batch run complete, shutting down the engine")
            self.actors().stop()

        else:
            # Otherwise, just stop the individual job actor for the job that has completed
            self.actors().stop(job_actor_id)
