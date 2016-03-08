#!/bin/sh

docker-compose up -d

sbt -J-Dpostgres.host=db $@