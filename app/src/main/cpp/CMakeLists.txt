cmake_minimum_required(VERSION 3.22.1)
project(alg C)

add_library(alg SHARED log.c)
add_library(termux SHARED termux.c)

target_compile_options(termux PRIVATE
    -Oz
    -fno-stack-protector
    -ffunction-sections
    -fdata-sections
    -fvisibility=hidden
    -fno-unwind-tables
    -fno-asynchronous-unwind-tables
    -fno-ident
    -flto
)

target_link_options(termux PRIVATE
    -flto
    -Wl,--gc-sections
    -Wl,--icf=all
    -Wl,--build-id=none
    -Wl,--strip-all
    -Wl,-z,max-page-size=16384
    -Wl,-z,common-page-size=16384
)

find_library(android_log_library log)

target_link_libraries(alg ${android_log_library})
