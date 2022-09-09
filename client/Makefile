all: ghostlab

CC=gcc
CCFLAGS=-pthread -Wall -Wpedantic -g

ghostlab: src/ghostlab.c src/utils.c
	$(CC) $(CCFLAGS) -o ghostlab src/ghostlab.c src/utils.c

clean:
	rm -f *.o ghostlab
