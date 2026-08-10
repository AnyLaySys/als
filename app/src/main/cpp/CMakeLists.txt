cmake_minimum_required(VERSION 3.22.1)
project(alslog C)

add_library(alslog SHARED alslog.c)

find_library(log_library log)

target_link_libraries(alslog ${log_library})
