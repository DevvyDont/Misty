##!/bin/bash
latesthash=$(curl -L https://github.com/niallsh/Misty/releases/latest/download/manifest.txt)
currenthash=$(md5sum Misty.jar | cut -d ' ' -f 1)

if [ "$latest" != "$currenthash" ]
then
    wget -q https://github.com/niallsh/Misty/releases/latest/download/Misty.jar -O ./Misty.jar
fi

java -jar Misty.jar