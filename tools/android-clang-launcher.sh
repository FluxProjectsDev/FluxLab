#!/bin/sh
# The Android SDK publishes Linux NDK host binaries for x86_64. On an arm64
# development host, use native Clang while retaining the official NDK sysroot,
# headers, runtimes, libraries, and target. Standard x86_64 CI skips this.
compiler="$1"
shift
ndk_root="${compiler%%/toolchains/*}"
prebuilt="$ndk_root/toolchains/llvm/prebuilt/linux-x86_64"
sysroot="$prebuilt/sysroot"
resource_dir="$prebuilt/lib/clang/19"
target_lib="$resource_dir/lib/linux/aarch64"
common_flags="--target=aarch64-linux-android28 --sysroot=$sysroot -resource-dir $resource_dir --rtlib=compiler-rt --unwindlib=libunwind -L$target_lib"
case "$compiler" in
    *clang++) exec /usr/bin/clang++ $common_flags "$@" ;;
    *) exec /usr/bin/clang $common_flags "$@" ;;
esac
