# Copyright 2021 The gRPC Authors
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""Generates C++ grpc stubs from proto_library rules.

This is an internal rule used by cc_grpc_library, and shouldn't be used
directly.
"""

load("@com_google_protobuf//bazel/common:proto_info.bzl", "ProtoInfo")
load(
    ":protobuf.bzl",
    "get_include_directory",
    "get_plugin_args",
    "get_proto_root",
    "proto_path_to_generated_filename",
)

_GRPC_PROTO_HEADER_FMT = "{}.grpc.pb.h"
_GRPC_PROTO_SRC_FMT = "{}.grpc.pb.cc"
_GRPC_PROTO_MOCK_HEADER_FMT = "{}_mock.grpc.pb.h"
_PROTO_HEADER_FMT = "{}.pb.h"
_PROTO_SRC_FMT = "{}.pb.cc"

def _strip_package_from_path(label_package, file):
    prefix_len = 0
    if not file.is_source and file.path.startswith(file.root.path):
        prefix_len = len(file.root.path) + 1

    path = file.path
    if len(label_package) == 0:
        return path
    if not path.startswith(label_package + "/", prefix_len):
        fail("'{}' does not lie within '{}'.".format(path, label_package))
    return path[prefix_len + len(label_package + "/"):]

def _get_srcs_file_path(file):
    if not file.is_source and file.path.startswith(file.root.path):
        return file.path[len(file.root.path) + 1:]
    return file.path

def _join_directories(directories):
    massaged_directories = [directory for directory in directories if len(directory) != 0]
    return "/".join(massaged_directories)

def generate_cc_impl(ctx):
    """Implementation of the generate_cc rule."""
    protos = [f for src in ctx.attr.srcs for f in src[ProtoInfo].check_deps_sources.to_list()]
    includes = [
        f
        for src in ctx.attr.srcs
        for f in src[ProtoInfo].transitive_imports.to_list()
    ]
    outs = []
    proto_root = get_proto_root(
        ctx.label.workspace_root,
    )

    label_package = _join_directories([ctx.label.workspace_root, ctx.label.package])
    if ctx.executable.plugin:
        outs += [
            proto_path_to_generated_filename(
                _strip_package_from_path(label_package, proto),
                _GRPC_PROTO_HEADER_FMT,
            )
            for proto in protos
        ]
        outs += [
            proto_path_to_generated_filename(
                _strip_package_from_path(label_package, proto),
                _GRPC_PROTO_SRC_FMT,
            )
            for proto in protos
        ]
        if ctx.attr.generate_mocks:
            outs += [
                proto_path_to_generated_filename(
                    _strip_package_from_path(label_package, proto),
                    _GRPC_PROTO_MOCK_HEADER_FMT,
                )
                for proto in protos
            ]
    else:
        outs += [
            proto_path_to_generated_filename(
                _strip_package_from_path(label_package, proto),
                _PROTO_HEADER_FMT,
            )
            for proto in protos
        ]
        outs += [
            proto_path_to_generated_filename(
                _strip_package_from_path(label_package, proto),
                _PROTO_SRC_FMT,
            )
            for proto in protos
        ]
    out_files = [ctx.actions.declare_file(out) for out in outs]
    dir_out = str(ctx.genfiles_dir.path + proto_root)

    arguments = []
    if ctx.executable.plugin:
        arguments += get_plugin_args(
            ctx.executable.plugin,
            ctx.attr.flags,
            dir_out,
            ctx.attr.generate_mocks,
        )
        tools = [ctx.executable.plugin]
    else:
        arguments += ["--cpp_out=" + ",".join(ctx.attr.flags) + ":" + dir_out]
        tools = []

    arguments += [
        "--proto_path={}".format(get_include_directory(i))
        for i in includes
    ]

    # Include the output directory so that protoc puts the generated code in the
    # right directory.
    arguments += ["--proto_path={0}{1}".format(dir_out, proto_root)]
    arguments += [_get_srcs_file_path(proto) for proto in protos]

    ctx.actions.run(
        inputs = protos + includes,
        tools = tools,
        outputs = out_files,
        executable = ctx.executable.protoc,
        arguments = arguments,
        mnemonic = "CppGrpcGenSource",
        use_default_shell_env = True,
    )

    return DefaultInfo(files = depset(out_files))

generate_cc = rule(
    attrs = {
        "srcs": attr.label_list(
            mandatory = True,
            allow_empty = False,
            providers = [ProtoInfo],
        ),
        "plugin": attr.label(
            executable = True,
            cfg = "exec",
        ),
        "flags": attr.string_list(
            mandatory = False,
            allow_empty = True,
        ),
        "generate_mocks": attr.bool(
            default = False,
            mandatory = False,
        ),
        "protoc": attr.label(
            mandatory = True,
            executable = True,
            cfg = "exec",
        ),
    },
    # We generate .h files, so we need to output to genfiles.
    output_to_genfiles = True,
    implementation = generate_cc_impl,
)
