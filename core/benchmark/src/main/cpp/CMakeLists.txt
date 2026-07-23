cmake_minimum_required(VERSION 3.22.1)
project(fluxlab_benchmark LANGUAGES CXX)

add_library(fluxlab-benchmark SHARED benchmark.cpp)
target_compile_options(fluxlab-benchmark PRIVATE -std=c++20 -O3 -Wall -Wextra -fno-fast-math)
find_library(log-lib log)
target_link_libraries(fluxlab-benchmark PRIVATE ${log-lib})
