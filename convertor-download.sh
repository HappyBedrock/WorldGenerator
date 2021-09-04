#!/usr/bin/env bash

mkdir convertor
cd convertor

curl -o php.zip 'https://dev.azure.com/pocketmine/a29511ba-1771-4ad2-a606-23c00a4b8b92/_apis/build/builds/455/artifacts?artifactName=Linux&api-version=6.0&%24format=zip'

apt install unzip

unzip php.zip
tar -xzvf Linux/PHP_Linux-x86_64.tar.gz

rm -rf php.zip
rm -rf Linux

curl -o WorldConvertor.phar 'https://github.com/HappyBedrock/WorldGenerator/releases/download/1.0.0-UHC/WorldConvertor.phar'

cd ..
