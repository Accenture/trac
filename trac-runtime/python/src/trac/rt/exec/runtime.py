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

import yaml
import json
import datetime as dt
import sys
import pathlib

import trac.rt.impl.util as util
import trac.rt.impl.config_parser as cpi
import trac.rt.config.config as config


class TracRuntime:

    def __init__(self, sys_config: str, dev_mode: bool):

        python_version = sys.version.replace("\n", "")

        print(f">>> TRAC Python Runtime {'DEVELOPMENT VERSION'} starting at {dt.datetime.now()}")
        print(f">>> Python installation: {python_version} ({sys.exec_prefix})")
        print(f">>> Working directory: {pathlib.Path.cwd()}")
        print(f">>> System config: {sys_config}")

        if dev_mode:
            print(f">>> Development mode enabled (DO NOT USE THIS IN PRODUCTION)")

        util.configure_logging(self.__class__)
        self._log = util.logger_for_object(self)

        self._dev_mode = dev_mode
        self._sys_config_path = sys_config
        self._sys_config: config.RuntimeConfig = self._load_config(sys_config, config.RuntimeConfig, dev_mode)

    # ------------------------------------------------------------------------------------------------------------------
    # Runtime service control
    # ------------------------------------------------------------------------------------------------------------------

    def __enter__(self):
        self.start()
        return self

    def __exit__(self, exc_type, exc_val, exc_tb):
        self.stop()

    def start(self):

        self._log.info("Begin startup sequence")

    def stop(self):

        self._log.info("Begin shutdown sequence")

    def wait_for_shutdown(self):
        pass

    # ------------------------------------------------------------------------------------------------------------------
    # Job submission
    # ------------------------------------------------------------------------------------------------------------------

    def submit_job(self, job_config: str):
        pass

    # ------------------------------------------------------------------------------------------------------------------
    # Config loading
    # ------------------------------------------------------------------------------------------------------------------

    def _load_config(self, config_file: str, config_class: type, dev_mode: bool):

        config_path = pathlib.Path(config_file)
        extension = config_path.suffix.lower()

        if not config_path.exists() or not config_path.is_file():
            raise RuntimeError()  # TODO: Error

        with config_path.open('r') as config_stream:
            if extension == ".yaml" or extension == ".yml":
                config_dict = yaml.safe_load(config_stream)
            elif extension == ".json":
                config_dict = json.load(config_stream)
            else:
                raise RuntimeError()  # TODO: Error

            parser = cpi.ConfigParser(config_class)
            cfg = parser.parse(config_dict)

            if not isinstance(cfg, config_class):
                raise RuntimeError()  # TODO: Error

            return cfg
