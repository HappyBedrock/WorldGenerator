#!/usr/bin/env bash
cd convertor
tar -xzvf PHP-8.0-Linux-x86_64.tar.gz
curl -L -o WorldConvertor.phar 'https://github.com/HappyBedrock/WorldGenerator/releases/download/1.0.0-UHC/WorldConvertor.phar'
cd ..