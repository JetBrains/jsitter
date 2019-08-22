#!/bin/bash

cd "$( dirname "${BASH_SOURCE[0]}" )/"

mkdir -p native/build/darwin
cd native/build/darwin
cmake ../..
make