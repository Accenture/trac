/*
 * Copyright 2020 Accenture Global Solutions Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const path = require('path');

const config = {};
config.npm_dir = path.resolve('./node_modules')
config.src_dir = path.resolve('.');
config.dist_dir = path.resolve('./umd')


module.exports = (env, argv) => {

    const PROD = (argv.mode === 'production');

    return {

        entry: config.src_dir + '/trac.js',

        output: {
            path: config.dist_dir,
            filename: PROD ? 'trac.min.js' : 'trac.js',
            library: "trac",
            libraryTarget: "umd",
            umdNamedDefine: true,
            globalObject: `(typeof self !== 'undefined' ? self : this)`
        },

        resolve: {
            modules: [
                config.src_dir,
                config.npm_dir
            ],
            extensions: [
                '.js'
            ]
        },

        optimization: {
            minimize: PROD
        },

        devtool: "source-map"
    }
};