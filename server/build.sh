if [ "$#" -gt 2 ]; then
    >&2 echo "Too many arguments"
    echo "usage: build.sh [run] <port>"
    exit -1
fi
find ./src -type f -name "*.java" > sources.txt
javac -d ./bin @sources.txt
cd bin
find . -type f -name "*.class" > classes.txt
jar cfe ghostlab.jar grp.isj.ghostlab.GhostLab @classes.txt
rm classes.txt
mv ghostlab.jar ../
cd ..
rm sources.txt
rm -rf bin/
echo "JAR built successfully"
if [ "$1" = "run" ]; then
    if [ -z "$2" ]; then
        java -jar ghostlab.jar
    else
        java -jar ghostlab.jar $2
    fi
fi