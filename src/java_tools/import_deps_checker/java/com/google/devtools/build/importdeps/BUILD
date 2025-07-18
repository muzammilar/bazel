# Description:
#   A checker to check the completeness of the deps of java_import or aar_import targets.

load("@rules_java//java:defs.bzl", "java_binary", "java_library")

package(
    default_visibility = ["//src:__subpackages__"],
)

filegroup(
    name = "srcs",
    srcs = glob(["**"]),
)

filegroup(
    name = "embedded_tools",
    srcs = [
        "BUILD.tools",
    ],
)

java_library(
    name = "import_deps_checker",
    srcs = glob(
        ["*.java"],
        exclude = ["Main.java"],
    ),
    deps = [
        "//src/main/protobuf:deps_java_proto",
        "//third_party:asm",
        "//third_party:asm-commons",
        "//third_party:asm-tree",
        "//third_party:auto_value",
        "//third_party:guava",
        "//third_party:jsr305",
        "//third_party:junit4",
    ],
)

java_binary(
    name = "ImportDepsChecker",
    srcs = ["Main.java"],
    jvm_flags = [
        # quiet warnings from com.google.protobuf.UnsafeUtil,
        # see: https://github.com/google/protobuf/issues/3781
        "-XX:+IgnoreUnrecognizedVMOptions",
        "--add-opens=java.base/java.nio=ALL-UNNAMED",
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
    ],
    main_class = "com.google.devtools.build.importdeps.Main",
    deps = [
        ":import_deps_checker",
        "//src/main/protobuf:deps_java_proto",
        "//src/main/protobuf:worker_protocol_java_proto",
        "//third_party:guava",
        "@maven//:args4j_args4j",
    ],
)
