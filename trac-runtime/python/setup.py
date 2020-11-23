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

import setuptools
import subprocess as sp
import platform


def get_trac_version():

    if platform.system().lower().startswith("win"):
        command = ['powershell', '-ExecutionPolicy', 'Bypass', '-File', '..\\..\\dev\\version.ps1']
    else:
        command = ['../../dev/version.sh']

    process = sp.Popen(command, stdout=sp.PIPE)
    (output, err) = process.communicate()
    exit_code = process.wait()

    if exit_code != 0:
        raise RuntimeError('Failed to get TRAC version')

    return output.decode('utf-8').strip()


trac_version = get_trac_version()
print(f'TRAC version: {trac_version}')


setuptools.setup(
    name='trac-model-runtime',
    version=trac_version,
    python_requires='>=3.6',
    packages=setuptools.find_packages('src'),
    package_dir={'': 'src'})