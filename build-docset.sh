#! /bin/sh

wget http://kapeli.com/javadocset.zip
unzip javadocset.zip

git clone https://hub.spigotmc.org/stash/scm/spigot/bukkit.git

cd bukkit
mvn javadoc:javadoc
cd ..

./javadocset bukkit bukkit/target/site/apidocs/
