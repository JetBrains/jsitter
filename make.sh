#!/bin/bash

cd "$( dirname "${BASH_SOURCE[0]}" )/native"

if [[ "$OSTYPE" == "linux-gnu" ]]; then
  mkdir -p build/linux-x86-64
  BUILD_DIR=build/linux-x86-64
elif [[ "$OSTYPE" == "darwin"* ]]; then
  mkdir -p build/darwin
  BUILD_DIR=build/darwin
fi

cmake -B $BUILD_DIR

cd $BUILD_DIR
make