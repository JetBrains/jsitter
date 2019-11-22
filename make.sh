#!/bin/bash

cd "$( dirname "${BASH_SOURCE[0]}" )/"

if [[ "$OSTYPE" == "linux-gnu" ]]; then
  mkdir -p native/build/linux-x86-64
  cd native/build/linux-x86-64
elif [[ "$OSTYPE" == "darwin"* ]]; then
  mkdir -p native/build/darwin
  cd native/build/darwin
fi

cmake ../..
make