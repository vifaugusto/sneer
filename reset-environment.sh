#!/bin/bash -v

git pull --rebase || exit -1

#Delete all untracked files and directories (-d), even ignored ones (-x).
git clean -d -x --force --quiet

rm ~/.m2/repository/me/sneer/ -rf

./gradlew clean check install || exit -1

echo "Reset environment sucessful"
