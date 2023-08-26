#!/bin/sh

sbt assembly
cp target/scala-2.13/pi-clock-assembly-2.0.0.jar pi-clock-2.0.0.jar
