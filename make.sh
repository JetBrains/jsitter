#!/bin/bash

cd "$( dirname "${BASH_SOURCE[0]}" )/"

mkdir -p native/build
cd native/build
cmake ..
make